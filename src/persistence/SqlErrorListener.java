package persistence;

import java.sql.SQLException;

import model.MatchData;

public interface SqlErrorListener {
	void onSqlError(MatchData data, SQLException e);
}
