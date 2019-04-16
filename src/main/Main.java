package main;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import model.League;
import model.Match;
import model.MatchData;
import model.MatchData.OddKey;
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
					}
					
					@Override
					public boolean onElementParsed(Match match) {
						scrapper.parse(match);
						return false;
					}
					
					@Override
					public boolean onElementParsed(League league) {
						scrapper.parse(league);
						return false;
					}
					
					@Override
					public boolean onElementParsed(Sport sport) {
						scrapper.parse(sport);
						return true;
					}

					@Override
					public boolean onElementParsed(MatchData data) {
						System.out.println(data.match + " parsed!");
						
						for (Entry<OddKey, Map<String,Double>> entry : data.getOdds().entrySet()) {
							System.out.println(entry.getKey() + " -> " + entry.getValue().size() + " results");
						}
						
						return true;
					}
				};
				
				scrapper.registerListener(listener); 
				scrapper.findSports();
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
