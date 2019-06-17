package persistence;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import model.MatchData;

public abstract class AbstractSQLiteManager implements DDBBManager {

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

			this.interrupt();

			/* Block callers until we are done, unless called from the thread itself for some reason */
			if (!Thread.currentThread().equals(this)) {
				try {
						this.join();
				} catch (InterruptedException e) { }
			}
		}

		@Override
		public void run() {
			running = true;

			while (running) {
				try {
					MatchData data = dataToBeStored.take();

					try {
						writeToDDBB(data);
					} catch (SQLException e) {
						if (listener != null) {
							listener.onSqlError(data,  e);
						}
					}
				} catch (InterruptedException e) {
					System.out.println("SQlWorkingThread interrupted, stopping...");
					break;
				}
			}

			running = false;
		}
	}

	private BlockingQueue<MatchData> dataToBeStored = new ArrayBlockingQueue<>(QUEUE_SIZE);
	private SqlErrorListener listener = null;
	private WorkingThread workingThread = new WorkingThread();

	public AbstractSQLiteManager(SqlErrorListener listener) throws ClassNotFoundException {
		this();
		setErrorListener(listener);
	}

	public AbstractSQLiteManager() throws ClassNotFoundException {
		Class.forName("org.sqlite.JDBC");
	}

	public synchronized void setErrorListener(SqlErrorListener listener) {
		this.listener = listener;
	}

	@Override
	public void store(MatchData data) {
		Objects.requireNonNull(data);

		if (dataToBeStored.remainingCapacity() < WARNING_THREHOLD)
			System.err.println("SQLManager can't keep up! Working thread will soon block");

		try {
			dataToBeStored.put(data);
		} catch (InterruptedException e) {
			System.err.println("Interrupted while storing, data will be lost");
		}
	}

	@Override
	public synchronized void close() {
		workingThread.stopAndWait();
	}

	@Override
	public void open() throws SQLException, IOException {
		workingThread.start();
	}

	protected abstract void writeToDDBB(MatchData data) throws SQLException;

}
