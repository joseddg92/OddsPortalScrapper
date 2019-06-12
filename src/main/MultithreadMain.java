package main;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import model.League;
import model.Match;
import model.MatchData;
import model.ScrapException;
import model.Sport;
import persistence.DDBBManager;
import persistence.SQLiteManager_v2;
import persistence.SqlErrorListener;
import scrapper.OddsPortalScrapper;
import scrapper.ParserListener;
import scrapper.RequestStatus;
import util.Prioritized.Priority;
import util.PriorityExecutor;
import util.Utils;

public class MultithreadMain {
	final static int CORES = Runtime.getRuntime().availableProcessors();
	final static int THREADS_PER_CORE = 1;
	final static int N_THREADS = Math.max(2, CORES * THREADS_PER_CORE);

	final static int LIVE_PARSE_INTERVAL_MINS = 30;
	final static int NONLIVE_PARSE_INTERVAL_MINS = 120;

	final static Priority UPDATE_LIST_PRIORITY = Priority.HIGH;
	final static Priority PARSE_LIVE_PRIORITY = Priority.HIGH;
	final static Priority PARSE_NONLIVE_PRIORITY = Priority.LOW;

	final static int RETRIES_PER_MATCH = 3;

	final static UncaughtExceptionHandler handler = new UncaughtExceptionHandler() {
		@Override
		public void uncaughtException(Thread t, Throwable e) {
			System.err.println("Thread " + t + "died: ");
			e.printStackTrace();
		}
	};

	final static ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(
		3,
		new ThreadFactoryBuilder()
			.setNameFormat("ScheduledWorkThread-%d")
			.setUncaughtExceptionHandler(handler).build()
	);

	final static PriorityExecutor RWDExecutor = new PriorityExecutor(
		N_THREADS,
		new ThreadFactoryBuilder()
		.setNameFormat("RWDWorkThread-%d")
		.setUncaughtExceptionHandler(handler).build()
	);

	private static volatile List<Match> lastMatches = null;

	private static void updateLastMatchesList(OddsPortalScrapper scrapper) {
		System.out.println("Starting Update matches..." + new Date());
		Instant timeStart = Instant.now();

		final List<Match> updatedMatches = Collections.synchronizedList(new ArrayList<>());
		final List<Future<?>> tasks = Collections.synchronizedList(new ArrayList<>());

		tasks.add(RWDExecutor.submitWithPriority(UPDATE_LIST_PRIORITY, () -> {
			scrapper.findLeagues(new ParserListener() {
				@Override
				public void onError(ScrapException e) {	}
				@Override
				public boolean onElementParsed(RequestStatus status, MatchData m) { return true; }
				@Override
				public boolean onElementParsed(RequestStatus status, Match m) {
					if (!status.ok())
						System.err.println("Error parsing: " + m);
					updatedMatches.add(m);
					return true;
				}

				@Override
				public boolean onElementParsed(RequestStatus status, League l) {
					if (!status.ok())
						System.err.println("Error parsing: " + l);
					tasks.add(RWDExecutor.submitWithPriority(
							UPDATE_LIST_PRIORITY, () -> {
								scrapper.parse(l, this);
							}
						)
					);
					return true;
				}

				@Override
				public boolean onElementParsed(RequestStatus status, Sport s) {
					if (!status.ok())
						System.err.println("Error parsing: " + s);
					tasks.add(RWDExecutor.submitWithPriority(
						UPDATE_LIST_PRIORITY, () -> {
							scrapper.parse(s, this);
						})
					);
					return true;
				}
			});
		}));

		boolean interrupted = false;
		while (!tasks.isEmpty()) {
			try {
				tasks.remove(0).get();
			} catch (InterruptedException e) {
				interrupted = true;
				break;
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}

		if (interrupted) {
			System.out.println("Update matches task interrupted!");
		} else if (updatedMatches.isEmpty()) {
			System.err.println("0 matches found, probably failed");
		} else {
			lastMatches = Collections.unmodifiableList(updatedMatches);

			Duration duration = Duration.between(timeStart, Instant.now());
			System.out.println("Update matches done in " + Utils.pretty(duration) + ", " + updatedMatches.size() + " matches found.");
		}
	}

	private static void parseMatches(String desc, Priority priority, Predicate<Match> filter, DDBBManager ddbb, OddsPortalScrapper scrapper) {
		Instant timeStart = Instant.now();

		while (lastMatches == null) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				return;
			}
		}

		final List<Match> filteredMatches = lastMatches.stream().filter(filter).collect(Collectors.toList());
		final Map<Match, Integer> errorsPerMatch = Collections.synchronizedMap(new HashMap<>());

		System.out.format("Starting to parse matches: %s... (%d matches)\n", desc, filteredMatches.size());
		final ParserListener toDDBB = new ParserListener() {
			@Override
			public void onError(ScrapException e) {	}
			@Override
			public boolean onElementParsed(RequestStatus status, MatchData matchData) {
				if (!status.ok()) {
					int nErrorsSoFar = errorsPerMatch.getOrDefault(matchData.match, 0);
					System.err.println("Errors parsing " + matchData.match + " ( " + nErrorsSoFar + " errors so far)");
					for (ScrapException e : status.getErrors())
						System.err.println("\t" + e.getMessage());

					if (nErrorsSoFar < RETRIES_PER_MATCH) {
						errorsPerMatch.put(matchData.match, nErrorsSoFar + 1);
						// TODO: Move this to the executor...
						scrapper.parse(matchData.match, this);
					} else {
						System.err.println("FATAL, could not parse: " + matchData.match);
					}
				} else {
					ddbb.store(matchData);
				}
				return true;
			}
			@Override
			public boolean onElementParsed(RequestStatus status, Match m) { return true; }
			@Override
			public boolean onElementParsed(RequestStatus status, League l) { return true; }
			@Override
			public boolean onElementParsed(RequestStatus status, Sport s) { return true; }
		};

		List<Future<?>> tasks = filteredMatches.stream()
			.map(match ->
			RWDExecutor.submitWithPriority(priority, () -> {
					scrapper.parse(match, toDDBB);
				})
			).collect(Collectors.toList());

		/* Block until all matches got parsed */
		int parsedMatches = 0;
		for (Future<?> task : tasks) {
			try {
				task.get();
				parsedMatches++;
			} catch (InterruptedException e) {
				System.out.println(desc + " task interrupted!");
				break;
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}

		Duration duration = Duration.between(timeStart, Instant.now());
		System.out.format("%s done (%d matches in %s, %s sec/match)\n",
						  desc, parsedMatches, Utils.pretty(duration),
				(parsedMatches == 0 ? 0 : Utils.pretty(duration.dividedBy(parsedMatches))));
	}

	private static void handleConsole(DDBBManager ddbb, OddsPortalScrapper scrapper) throws IOException {
		System.out.println("Press a key to exit");
		System.in.read();
	}

	public static void main(String[] args) throws Exception {
		Thread.currentThread().setName("MainThread");

		System.out.println("Using path: " + System.getProperty("webdriver.chrome.driver"));

		Instant start = Instant.now();
		try (final DDBBManager ddbbManager = new SQLiteManager_v2(
			new SqlErrorListener() {
				@Override
				public void onSqlError(MatchData data, SQLException e) {
					e.printStackTrace();
				}
			})
		) {
			ddbbManager.open();
			try (final OddsPortalScrapper scrapper = new OddsPortalScrapper()) {
				scrapper.registerListener(new DataQualityChecker());

				scheduledExecutor.scheduleAtFixedRate(
						() -> { updateLastMatchesList(scrapper); },
						0,
						LIVE_PARSE_INTERVAL_MINS,
						TimeUnit.MINUTES
				);

				scheduledExecutor.scheduleAtFixedRate(
						() -> { parseMatches("Live matches", PARSE_LIVE_PRIORITY, m -> m.isLive, ddbbManager, scrapper); },
						0,
						LIVE_PARSE_INTERVAL_MINS,
						TimeUnit.MINUTES
				);

				scheduledExecutor.scheduleAtFixedRate(
						() -> { parseMatches("Non-live matches", PARSE_NONLIVE_PRIORITY, m -> !m.isLive, ddbbManager, scrapper); },
						LIVE_PARSE_INTERVAL_MINS,
						NONLIVE_PARSE_INTERVAL_MINS,
						TimeUnit.MINUTES
				);

				System.out.println("Scrapper running (it took " + Utils.pretty(Duration.between(start, Instant.now())) + " to start)");
				handleConsole(ddbbManager, scrapper);

				System.out.println("Shutting down...");
				Instant shutDownStartTime = Instant.now();
				scheduledExecutor.shutdownNow();
				RWDExecutor.shutdownNow();
				scheduledExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
				RWDExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
				start = Instant.now();
				System.out.println("Thread finished in " + Utils.pretty(Duration.between(shutDownStartTime, start)));
			}
			System.out.println("Scrapper closed in " + Utils.pretty(Duration.between(start, Instant.now())));
			start = Instant.now();
		}
		System.out.println("DDBB Manager closed in " + Utils.pretty(Duration.between(start, Instant.now())));
	}
}
