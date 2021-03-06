package persistence;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import model.Match;
import model.MatchData;
import model.MatchData.OddKey;
import util.StringDate;
import util.Utils;

public class SQLiteManager_v1 extends AbstractSQLiteManager {

	private static String DB_FILE_PATH = "odds_v1.db";
	private static String SQLITE_JDBC_STRING = "jdbc:sqlite:" + DB_FILE_PATH;
	private static String CREATE_DDBB_V1_SCRIPT_RES_PATH = "/persistence/db_create_v1.sql";

	private static String NEW_LINE_SEPARATOR = System.lineSeparator() + System.lineSeparator();

	public SQLiteManager_v1() throws ClassNotFoundException {
		this(null);
	}

	public SQLiteManager_v1(SqlErrorListener listener) throws ClassNotFoundException {
		super(listener);
	}

	@Override
	protected void writeToDDBB(MatchData data) throws SQLException {
		final Match m = data.match;

		try (Connection con = DriverManager.getConnection(SQLITE_JDBC_STRING)) {
			try {
				con.setAutoCommit(false);

				int sportId = insertIfNeededAndGetId(con, "sport", "sportId",
						list("name"),
						list(m.league.sport.name)
				);
				int countryId = insertIfNeededAndGetId(con, "country", "countryId",
						list("name"),
						list(m.league.country.name)
				);
				int leagueId = insertIfNeededAndGetId(con, "league", "leagueId",
						list("sportId", "countryId", "name"),
						list(sportId, countryId, m.league.name)
				);
				int matchId = insertIfNeededAndGetId(con, "match", "matchId",
						list("oddSportsKey", "leagueId", "name", "localTeam", "visitorTeam", "beginDatetime"),
						list(m.getKey(), leagueId, m.name, m.getLocalTeam(), m.getVisitorTeam(), data.beginTimeStamp)
				);

				Map<OddKey, Map<StringDate, Double>> odds = data.getOdds();
				for (Entry<OddKey, Map<StringDate, Double>> entry : odds.entrySet()) {
					final OddKey oddKey = entry.getKey();
					int oddCatId = insertIfNeededAndGetId(con, "odd_category", "catId",
							list("cat1", "cat2", "cat3"),
							list(oddKey.section.tab, oddKey.section.subtab, oddKey.row)
					);
					int betHouseId = insertIfNeededAndGetId(con, "bethouse", "bethouseId",
							list("name"), list(oddKey.betHouse)
					);
					for (Entry<StringDate, Double> oddData : entry.getValue().entrySet()) {
						final StringDate time = oddData.getKey();
						insert(con, "odd",
								list("matchId", "catId", "bethouseId", "time", "timeStr", "value"),
								list(matchId, oddCatId, betHouseId, time.getTimeStamp(), time.getText(), oddData.getValue())
						);
					}
				}

				con.commit();
			} catch (SQLException e) {
	    		con.rollback();
		    	throw e;
		    }
		}
	}

	@Override
	public void close() {
		super.close();
	}

	@Override
	public void open() throws SQLException, IOException {
		try (InputStream is = getClass().getResourceAsStream(CREATE_DDBB_V1_SCRIPT_RES_PATH)) {
			String createDDBBScript = Utils.isToString(is);
			List<String> sqlStatements = Arrays.asList(createDDBBScript.split(NEW_LINE_SEPARATOR));
	        try (Connection connection = DriverManager.getConnection(SQLITE_JDBC_STRING);
	        	 Statement statement = connection.createStatement()) {
	        	for (String sqlStatement : sqlStatements)
	        		statement.execute(sqlStatement);
	        }
        }

		super.open();
	}

	private static void fillStatement(PreparedStatement s, int i, Object o) throws SQLException {
		if (o instanceof String)
			s.setString(i, (String) o);
		else if (o instanceof Timestamp)
			s.setTimestamp(i, (Timestamp) o);
		else if (o instanceof Integer)
			s.setInt(i, (Integer) o);
		else if (o instanceof Long)
			s.setLong(i,  (Long) o);
		else if (o instanceof Double)
			s.setDouble(i, (Double) o);
		else
			throw new SQLException("Cannot handle " + o.getClass() + " type.");
	}

	private static int insert(Connection con, String table, List<String> columns, List<?> values) throws SQLException {
		if (columns.size() != values.size())
			throw new IllegalArgumentException("Missmatch: " + columns + ", " + values);

		final Collector<CharSequence, ?, String> sqlCollector = Collectors.joining(", ", " ( ", " ) ");
		String columnsWithoutNullValue =
				Utils.zip(columns, values).filter(e -> e.getValue() != null).map(e -> e.getKey()).collect(sqlCollector);

		final String sqlStatement =
				"INSERT into " + table + " " +
				columnsWithoutNullValue +
				"values " +
				Collections.nCopies(columnsWithoutNullValue.split(",").length, "?").stream().collect(sqlCollector);

		try (PreparedStatement st = con.prepareStatement(sqlStatement)) {
			int stField = 1;

			for (Object value : values)
				if (value != null)
					fillStatement(st, stField++, value);

			st.execute();

			try (Statement indexQuery = con.createStatement();
				 ResultSet result = indexQuery.executeQuery("SELECT last_insert_rowid();")) {
				if (!result.next())
					throw new SQLException("Insert + SELECT last_insert_rowid() did not return an int");

				return result.getInt(1);
			}
		}
	}

	private static int insertIfNeededAndGetId(Connection con, String table, String idColumn, List<String> columns, List<?> values) throws SQLException {
		if (columns.size() != values.size())
			throw new IllegalArgumentException("Missmatch: " + columns + ", " + values);

		int id;
		final String sqlStatement =
				"SELECT " + idColumn + " " +
				"FROM " + table + " " +
				"WHERE " +
				Utils.zip(columns, values).map(e -> e.getKey() + (e.getValue() == null ? " IS NULL" : " = ?" ))
					.collect(Collectors.joining(" AND "));

		try (PreparedStatement st = con.prepareStatement(sqlStatement)) {
			int stField = 1;

			for (Object value : values)
				if (value != null)
					fillStatement(st, stField++, value);

			try (ResultSet result = st.executeQuery()) {
				if (result.next()) {
					/* Present in the ddbb already, return id */
					id = result.getInt(1);
				} else {
					id = insert(con, table, columns, values);
				}
			}
		}

		return id;
	}

	private static List<String> list(String ... elements) {
		return Arrays.asList(elements);
	}

	private static List<Object> list(Object ... elements) {
		return Arrays.asList(elements);
	}
}
