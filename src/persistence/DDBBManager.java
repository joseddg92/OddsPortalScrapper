package persistence;

import java.io.IOException;
import java.sql.SQLException;

import model.MatchData;

public interface DDBBManager extends AutoCloseable {
	public void store(MatchData data) throws SQLException;
	public void ensureDDBBCreated() throws SQLException, IOException;
}
