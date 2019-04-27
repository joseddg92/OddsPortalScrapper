package main;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import model.League;
import model.Match;
import model.MatchData;
import model.ScrapException;
import model.Sport;
import persistence.SQLiteManager;
import scrapper.OddsPortalScrapper;
import scrapper.ParserListener;
import util.EclipseTools;

public class Main {

	private static final String ERROR_REPORT_PATH = "log";
		
	private static void parseMatches(SQLiteManager ddbbManager, OddsPortalScrapper scrapper) {
		final String runStartDate = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
		final List<Match> matches = new ArrayList<>();
		
		final ParserListener listener = new ParserListener() {

			int nErrors = 0;
			
			@Override
			public void onError(ScrapException error) {
				nErrors++;

				System.err.println("Error logged: " + error.getMessage());
				String reportName = String.format("%s_%d.log", runStartDate, nErrors);
				try (PrintWriter writer = new PrintWriter(new File(ERROR_REPORT_PATH, reportName))) {
						error.logTo(writer);
				} catch (IOException e) {
					System.err.println("Could not log exception.");
					e.printStackTrace();
				}
			}
			
			@Override
			public boolean onElementParsed(Match match) {
				matches.add(match);
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

		long timeStart = System.currentTimeMillis();

		try {
			scrapper.findSports();
		} catch (ScrapException e) {
			e.printStackTrace();
		}
		
		long timeEnd = System.currentTimeMillis();
		
		System.out.format("It took %d ms to parse %d matches (%.2f matches/sec)\n", timeEnd - timeStart, matches.size(), matches.size() / (double) ((timeEnd- timeStart)) / 1000);
		
		int i = 0;
		timeStart = System.currentTimeMillis();
		for (Match match : matches) {
			System.out.println(i++ + "/" + matches.size());
			scrapper.parse(match);
		}
		timeEnd = System.currentTimeMillis();
		System.out.format("It took %d ms to load %d matches (%.2f sec/match)\n", timeEnd - timeStart, matches.size(), (timeEnd- timeStart) / (1000d * (double) matches.size()));
	}
	
	public static void main(String[] args) throws Exception {
		EclipseTools.fixConsole();
		System.setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");

		try (SQLiteManager ddbbManager = new SQLiteManager()) {
			ddbbManager.ensureDDBBCreated();
			
			try (OddsPortalScrapper scrapper = new OddsPortalScrapper()) {
				parseMatches(ddbbManager, scrapper);
			}
		}
	}
}
