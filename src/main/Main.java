package main;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import model.League;
import model.Match;
import model.MatchData;
import model.ScrapException;
import model.Sport;
import scrapper.OddsPortalScrapper;
import scrapper.ParserListener;
import util.EclipseTools;

public class Main {

	private static final String ERROR_REPORT_PATH = "log";
	
	public static void main(String[] args) throws Exception {
		final String runDateString = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
		EclipseTools.fixConsole();
		System.setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");
		
		try (OddsPortalScrapper scrapper = new OddsPortalScrapper()) {
			boolean pauseOnExit = false;
			/* Introduce another try block so that scrapper 
			 * is not closed before the catch or finally blocks  */
			try {
				List<Match> matches = new ArrayList<>();
				ParserListener listener = new ParserListener() {

					int nErrors = 0;
					
					@Override
					public void onError(ScrapException error) {
						nErrors++;

						System.err.println("Error logged: " + error.getMessage());
						String reportName = String.format("%s_%d.log", runDateString, nErrors);
						try (PrintWriter writer = new PrintWriter(new File(ERROR_REPORT_PATH, reportName))) {
								error.logTo(writer);
						} catch (IOException e) {
							System.err.println("Could not log exception.");
							e.printStackTrace();
						}
						
						try {
							System.in.read();
						} catch (IOException e) {
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
						System.out.println(data.match + " parsed!");
						return true;
					}
				};
				
				scrapper.registerListener(listener);
				long timeStart = System.currentTimeMillis();
				scrapper.findSports();
				long timeEnd = System.currentTimeMillis();
				
				System.out.format("It took %d ms to parse %d matches (%.2f matches/sec)", timeEnd- timeStart, matches.size(), matches.size() / (double) ((timeEnd- timeStart)) / 1000);
				
				timeStart = System.currentTimeMillis();
				int i = 0;
				for (Match match : matches) {
					System.out.println(i++ + "/" + matches.size());
					scrapper.parse(match);
				}
				timeEnd = System.currentTimeMillis();
				System.out.format("It took %d ms to load %d matches (%.2f matches/sec)", timeEnd- timeStart, matches.size(), matches.size() / (double) ((timeEnd- timeStart)) / 1000);
				
				
			} catch (Exception e) {
				System.err.println("Critical exception: ");
				e.printStackTrace();
				
				pauseOnExit = true;
			} finally {
				if (pauseOnExit) {
					System.err.flush();
		
					System.out.println("Press any key to close.");
					System.in.read();
				}
			}
		}
	}
}
