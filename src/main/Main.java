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

import model.League;
import model.Match;
import model.MatchData;
import model.ScrapException;
import model.Sport;
import persistence.DDBBManager;
import persistence.SQLiteManager_v2;
import scrapper.OddsPortalScrapper;
import scrapper.ParserListener;
import util.EclipseTools;
import util.Utils;

public class Main {

	private static final String ERROR_REPORT_PATH = "log";
	private static final Path BINDATA_FODLER = Paths.get("bindata");
		
	private static void writeToFile(File output, Serializable object) throws IOException {
		System.out.println("Writing to " + output.getAbsolutePath().toString());
		output.createNewFile();
		try (ObjectOutputStream objectOut = new ObjectOutputStream(new FileOutputStream(output))) {
            objectOut.writeObject(object);
		}
	}
	
	private static void parseMatches(DDBBManager ddbbManager, OddsPortalScrapper scrapper) {
		final String runStartDate = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
		final List<Match> liveMatches = new ArrayList<>();
		
		final ParserListener listener = new ParserListener() {

			int nErrors = 0;
			
			@Override
			public void onError(ScrapException error) {
				nErrors++;

				System.err.println("Error logged: " + error.getMessage());
				final String reportName = String.format("%s_%d.log", runStartDate, nErrors);
				try (PrintWriter writer = new PrintWriter(new File(ERROR_REPORT_PATH, reportName))) {
						error.logTo(writer);
				} catch (IOException e) {
					System.err.println("Could not log exception.");
					e.printStackTrace();
				}
				
				final String screenShotName = String.format("%s_%d.png", runStartDate, nErrors);
				byte[] pngBytes = error.webData == null ? null : error.webData.getScreenShot();
				if (pngBytes != null) {
					try (OutputStream stream = new FileOutputStream(new File(ERROR_REPORT_PATH, screenShotName))) {
						stream.write(pngBytes);
					} catch (IOException e) {
						System.err.println("Could not log exception screenshot.");
						e.printStackTrace();
					}
				}
			}
			
			@Override
			public boolean onElementParsed(Match match) {
				if (match.isLive)
					liveMatches.add(match);
				
				return true;
			}
			
			@Override
			public boolean onElementParsed(League league) {
				scrapper.parse(league);
				return true;
			}
			
			@Override
			public boolean onElementParsed(Sport sport) {
				scrapper.parse(sport);
				return true;
			}

			@Override
			public boolean onElementParsed(MatchData data) {
				System.out.println("onElementParsed(" + data.match + ")");
				try {
					writeToFile(Files.createTempFile(BINDATA_FODLER, "matchdata_", ".bin").toFile(), data);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				
				try {
					ddbbManager.store(data);
				} catch (SQLException e) {
					e.printStackTrace();
				}

				return true;
			}
		};
		
		scrapper.registerListener(listener);

		Instant timeStart = Instant.now();
		scrapper.findSports();
		Duration processTime = Duration.between(timeStart, Instant.now());
		
		if (liveMatches.isEmpty()) {
			System.err.println("No matches!");
			return;
		}
		System.out.format(
				"It took %s to find %d live matches matches (%s / match)\n", 
				Utils.pretty(processTime), 
				liveMatches.size(), 
				Utils.pretty(processTime.dividedBy(liveMatches.size()))
		);
		
				
		timeStart = Instant.now();
		for (int i = 0; i < liveMatches.size(); i++) {
			final Match match = liveMatches.get(i);
			System.out.println("Parsing " + match + " ("+ (i + 1) + "/" + liveMatches.size() + ") ...");
			scrapper.parse(match);
		}
		processTime = Duration.between(timeStart, Instant.now());
		
		System.out.format(
				"It took %s to load %d matches (%s / match)\n", 
				Utils.pretty(processTime), 
				liveMatches.size(), 
				Utils.pretty(processTime.dividedBy(liveMatches.size()))
		);
	}
	
	public static void main(String[] args) throws Exception {
		EclipseTools.fixConsole();
		System.setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");
		BINDATA_FODLER.toFile().mkdir();

		try (DDBBManager ddbbManager = new SQLiteManager_v2()) {
			ddbbManager.ensureDDBBCreated();

			try (OddsPortalScrapper scrapper = new OddsPortalScrapper()) {
				while (true)
					parseMatches(ddbbManager, scrapper);
			}
		}
	}
}
