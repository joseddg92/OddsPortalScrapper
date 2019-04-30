package main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
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
import persistence.SQLiteManager_v1;
import scrapper.OddsPortalScrapper;
import scrapper.ParserListener;
import util.EclipseTools;
import util.Utils;

public class Main {

	private static final String ERROR_REPORT_PATH = "log";
		
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
		
		System.out.format(
				"It took %s to find %d live matches matches (%.2f matches/sec)\n", 
				Utils.pretty(processTime), 
				liveMatches.size(), 
				(double) liveMatches.size() / processTime.getSeconds()
		);
		
				
		timeStart = Instant.now();
		for (int i = 0; i < liveMatches.size(); i++) {
			final Match match = liveMatches.get(i);
			System.out.println("Parsing " + match + " ("+ (i + 1) + "/" + liveMatches.size() + ") ...");
			scrapper.parse(match);
		}
		processTime = Duration.between(timeStart, Instant.now());
		
		System.out.format(
				"It took %s to load %d matches (%.2f sec/match)\n", 
				Utils.pretty(processTime), 
				liveMatches.size(), 
				(double) processTime.getSeconds() / liveMatches.size()
		);
	}
	
	public static void main(String[] args) throws Exception {
		EclipseTools.fixConsole();
		System.setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");

		try (DDBBManager ddbbManager = new SQLiteManager_v1()) {
			ddbbManager.ensureDDBBCreated();
			
			try (OddsPortalScrapper scrapper = new OddsPortalScrapper()) {
				while (true)
					parseMatches(ddbbManager, scrapper);
			}
		}
	}
}
