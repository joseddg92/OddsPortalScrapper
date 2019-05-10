package main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import model.League;
import model.Match;
import model.MatchData;
import model.ScrapException;
import model.Sport;
import persistence.DDBBManager;
import persistence.SQLiteManager_v2;
import scrapper.OddsPortalScrapper;
import scrapper.ParserListener;
import scrapper.RequestStatus;
import util.EclipseTools;
import util.Utils;

public class Main {

	private static final String ERROR_REPORT_PATH = "log";
	private static final Path BINDATA_FOLDER = Paths.get("bindata");
	
	private static void writeToFile(File output, Serializable object) throws IOException {
		output.createNewFile();
		try (ObjectOutputStream objectOut = new ObjectOutputStream(new FileOutputStream(output))) {
            objectOut.writeObject(object);
		}
	}
	
	private static void parseMatches(DDBBManager ddbbManager, OddsPortalScrapper scrapper) {
		final String runStartDate = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
		final List<Match> matches = new ArrayList<>();

		final ParserListener listener = new ParserListener() {
			int nErrors = 0;
			@Override
			public void onError(ScrapException error) {
				final File errorFolder = new File(ERROR_REPORT_PATH, runStartDate);
				final String reportName = String.format("%d.log", nErrors++);

				System.err.format("%s%s%s\n",
								  error.getMessage(),
								  error.getCause() != null ? "\n\tcaused by: " + Utils.firstLine(error.getCause().getMessage()) : "",
								  error.element != null ? "\n\ton element: " + error.element.cssSelector() : ""
				);
				
				errorFolder.mkdir();
				try (PrintWriter writer = new PrintWriter(new File(errorFolder, reportName))) {
						error.logTo(writer);
				} catch (IOException e) {
					System.err.println("Could not log exception: " + e.getMessage());
				}
				
				final String screenShotName = String.format("%d.png", nErrors);
				byte[] pngBytes = error.webData == null ? null : error.webData.getScreenShot();
				if (pngBytes != null) {
					try (OutputStream stream = new FileOutputStream(new File(errorFolder, screenShotName))) {
						stream.write(pngBytes);
					} catch (IOException e) {
						System.err.println("Could not log exception screenshot.");
						e.printStackTrace();
					}
				}
			}
			
			@Override
			public boolean onElementParsed(RequestStatus status, Match match) {
				System.out.println("Found: " + match);
				matches.add(match);
				
				return true;
			}
			
			@Override
			public boolean onElementParsed(RequestStatus status, League league) {
				scrapper.parse(league);
				return true;
			}
			
			@Override
			public boolean onElementParsed(RequestStatus status, Sport sport) {
				scrapper.parse(sport);
				return true;
			}

			@Override
			public boolean onElementParsed(RequestStatus status, MatchData data) {
				if (!status.ok()) {
					System.err.println("Errors parsing " + data.match);
					return true;
				}

				System.out.println("Parsed :" + data.match + ", " + data.getOdds().size() + " odds");
				//data.dumpContents(System.out);
				new Thread() {
					public void run() {
						try {
							writeToFile(Files.createTempFile(BINDATA_FOLDER, "matchdata_", ".bin").toFile(), data);
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
				}.start();
				
				try {
					ddbbManager.store(data);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				return true;
			}
		};
		
		scrapper.registerListener(listener);
		scrapper.registerListener(new DataQualityChecker());

		Instant timeStart = Instant.now();
		scrapper.findSports();
		Duration processTime = Duration.between(timeStart, Instant.now());
		
		if (matches.isEmpty()) {
			System.err.println("No matches!");
			return;
		}
		
		System.out.format(
				"It took %s to find %d matches (%s / match)\n", 
				Utils.pretty(processTime), 
				matches.size(), 
				Utils.pretty(processTime.dividedBy(matches.size()))
		);
		
			
		List<Match> liveMatches = matches.stream().filter(m -> m.isLive).collect(Collectors.toList());
		
		System.out.println("Parsing live matches...");
		timeStart = Instant.now();
		final int RETRIES = 3;
		int retry = 0;
		for (int i = 0; i < liveMatches.size(); i++) {
			final Match match = liveMatches.get(i);
			System.out.println("Parsing " + match + "...");

			RequestStatus status;
			do {
				status = scrapper.parse(match);
			} while (!status.ok() && retry++ < RETRIES);
			if (!status.ok())
				System.err.println(match + " couldn't be parsed in " + RETRIES + " attempts. DATA LOST!");

			final Duration timeSoFar = Duration.between(timeStart, Instant.now());
			System.out.format("%d/%d (%d%%) (%s / match)\tETA: %s\n\n",
					i+1, liveMatches.size(), (100 * (i + 1)) / liveMatches.size(),
					Utils.pretty(timeSoFar.dividedBy(i + 1)),
					Utils.pretty(timeSoFar.dividedBy(i + 1).multipliedBy(liveMatches.size() - (i + 1)))
			);
				
					
		}
		processTime = Duration.between(timeStart, Instant.now());
		
		System.out.format(
				"It took %s to load %d live matches (%s / match)\n", 
				Utils.pretty(processTime), 
				matches.size(), 
				Utils.pretty(processTime.dividedBy(matches.size()))
		);
		
		scrapper.clearListeners();
	}
	
	public static void main(String[] args) throws Exception {
		EclipseTools.fixConsole();
		System.setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");
		BINDATA_FOLDER.toFile().mkdir();

		try (DDBBManager ddbbManager = new SQLiteManager_v2()) {
			ddbbManager.open();

			try (OddsPortalScrapper scrapper = new OddsPortalScrapper()) {
				while (true)
					parseMatches(ddbbManager, scrapper);
			}
		}
	}
}
