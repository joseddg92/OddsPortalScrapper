package main;

import java.util.List;

import model.League;
import model.Match;
import model.ScrapException;
import model.Sport;
import scrapper.OddsPortalScrapper;
import scrapper.ParserListener;
import util.EclipseTools;

public class Main {

	public static void main(String[] args) throws Exception {
		EclipseTools.fixConsole();

		System.setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");
		try (OddsPortalScrapper scrapper = new OddsPortalScrapper()) {
			boolean pauseOnExit = false;
			/* Introduce another try block so that scrapper 
			 * is not closed before the catch or finally blocks  */
			try {
				ParserListener listener = new ParserListener() {

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
				};
				
				scrapper.registerListener(listener); 
				scrapper.findSports();
				
				List<ScrapException> errors = scrapper.getErrors();
				if (!errors.isEmpty()) {
					System.err.println(errors.size() + " non-critical errors");
					pauseOnExit = true;
				}
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
