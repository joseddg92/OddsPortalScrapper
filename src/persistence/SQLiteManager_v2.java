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
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import model.Match;
import model.MatchData;
import model.MatchData.OddKey;
import util.StringDate;
import util.Utils;

public class SQLiteManager_v2 implements DDBBManager {

	private static final String DB_FILE_PATH = "odds_v2.db";
	private static final String SQLITE_JDBC_STRING = "jdbc:sqlite:" + DB_FILE_PATH;
	private static final String CREATE_DDBB_V2_SCRIPT_RES_PATH = "/persistence/db_create_v2.sql";
	
	private static final String NEW_LINE_SEPARATOR = System.lineSeparator() + System.lineSeparator();
	
	private static final MatchData NULL_SPECIAL_EXIT_VALUE = null;
	
	/* A high value is preferred, this gives up some memory to try to never lock a html provider therad */
	private static final int QUEUE_SIZE = 1024;
	/* Warn if less than this amount of slots are available in the queue */
	private static final int WARNING_THREHOLD = 16;
	
	private class WorkingThread extends Thread {
		
		private volatile boolean running = false;
		
		public WorkingThread() {
			super("SQLWorkingThread");
		}
		
		public void stopAndWait() {
			if (!running)
				return;

			running = false;
			
			try {
				dataToBeStored.put(NULL_SPECIAL_EXIT_VALUE);
				/* Block callers until we are done, unless called from the thread itself for some reason */
				if (!Thread.currentThread().equals(this))
						this.join();
			} catch (InterruptedException e) { }
		}
		
		public void run() {
			running = true;

			while (running) {
				try {
					MatchData data = dataToBeStored.take();
					if (data == NULL_SPECIAL_EXIT_VALUE)
						break;
					try {
						writeToDDBB(data);
					} catch (SQLException e) {
						if (listener != null) {
							listener.onSqlError(data,  e);
						}
					}
				} catch (InterruptedException e) {}
			}

			running = false;
		}
	}
	
	private BlockingQueue<MatchData> dataToBeStored = new ArrayBlockingQueue<>(QUEUE_SIZE);
	private SqlErrorListener listener = null;
	private WorkingThread workingThread = new WorkingThread();
	
	public SQLiteManager_v2(SqlErrorListener listener) throws ClassNotFoundException {
		this();
		setErrorListener(listener);
	}
	
	public SQLiteManager_v2() throws ClassNotFoundException {
		Class.forName("org.sqlite.JDBC");
	}
	
	public synchronized void setErrorListener(SqlErrorListener listener) {
		this.listener = listener;
	}
	
	public void store(MatchData data) {
		Objects.requireNonNull(data);

		if (dataToBeStored.remainingCapacity() < WARNING_THREHOLD) 
			System.err.println("SQLManager can't keep up! Working thread will soon block");
		
		try {
			dataToBeStored.put(data);
		} catch (InterruptedException e) { }
	}
	
	@Override
	public synchronized void close() {
		workingThread.stopAndWait();
	}
	
	public synchronized void open() throws SQLException, IOException {
		/* Esure DDBB is created */
		try (InputStream is = getClass().getResourceAsStream(CREATE_DDBB_V2_SCRIPT_RES_PATH)) {
			String createDDBBScript = Utils.isToString(is);
			List<String> sqlStatements = Arrays.asList(createDDBBScript.split(NEW_LINE_SEPARATOR));
	        try (Connection connection = DriverManager.getConnection(SQLITE_JDBC_STRING);
	        	 Statement statement = connection.createStatement()) {
	        	for (String sqlStatement : sqlStatements)
	        		statement.execute(sqlStatement);
	        }
        }
		workingThread.start();
	}
	
	private void writeToDDBB(MatchData data) throws SQLException {
		final Match m = data.match;
		
		try (Connection con = DriverManager.getConnection(SQLITE_JDBC_STRING)) {
			try {
				con.setAutoCommit(false);
				
				int sportId = insertIfNeededAndGetId(con, "sport", "sport_id", 
						list("name"), 
						list(m.league.sport.name)
				);
				int countryId = insertIfNeededAndGetId(con, "country", "country_id", 
						list("name"), 
						list(m.league.country.name)
				);
				int leagueId = insertIfNeededAndGetId(con, "league", "league_id",
						list("sport_id", "country_id", "name"),
						list(sportId, countryId, m.league.name)
				);
				int matchId = insertIfNeededAndGetId(con, "match", "match_id",
						list("web_key", "league_id", "name", "local_team", "visitor_team", "begin_time"),
						list(m.getKey(), leagueId, m.name, m.getLocalTeam(), m.getVisitorTeam(), data.beginTimeStamp)
				);
				
				Map<OddKey, Map<StringDate, Double>> odds = data.getOdds();
				for (Entry<OddKey, Map<StringDate, Double>> entry : odds.entrySet()) {
					final OddKey oddKey = entry.getKey();
					int betHouseId = insertIfNeededAndGetId(con, "bethouse", "bethouse_id",
							list("name"), list(oddKey.betHouse)
					);
					int oddCatId = insertIfNeededAndGetId(con, "odd_cat", "cat_id", 
							list("bethouse_id", "cat1", "cat2", "cat3"), 
							list(betHouseId, oddKey.section.tab, oddKey.section.subtab, oddKey.row)
					);
					for (Entry<StringDate, Double> oddData : entry.getValue().entrySet()) {
						final StringDate time = oddData.getKey();
						long millis = time.getTimeStamp() == null ? 0 : time.getTimeStamp();
						Timestamp ts = new Timestamp(millis);
						
						int oddId = insert(con, "odd",
								list("match_id", "cat_id", "time", "value"), 
								list(matchId, oddCatId, ts, oddData.getValue())
						);
						
						if (millis == 0) {
							/* Keep string version of timestamp, as it could not be parsed */
							insert(con, "odd_baddate", 
									list("odd_id", "time_str"),
									list(oddId, time.getText()));
						}
					}
				}
				
				con.commit();
			} catch (SQLException e) {
	    		con.rollback();
		    	throw e;
		    }
		}
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
