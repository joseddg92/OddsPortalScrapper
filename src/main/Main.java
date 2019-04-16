package main;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import model.League;
import model.Match;
import model.ScrapException;
import model.Sport;
import scrapper.OddsPortalScrapper;
import scrapper.ParserListener;
import util.EclipseTools;

public class Main {

	private static final String ERROR_REPORT_PATH = "log";
	
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
					int errorNumber = 0;
					String currentDateString = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
					for (ScrapException error : errors) {
						System.err.println("Exception " + errorNumber);
						error.printStackTrace();
						String reportName = String.format("%s_%d.log", currentDateString, ++errorNumber);
						try (PrintWriter writer = new PrintWriter(new File(ERROR_REPORT_PATH, reportName))) {
								error.logTo(writer);
							} catch (IOException e) {
								e.printStackTrace();
							}
					}
					
					System.err.println(errors.size() + " non-critical errors logged");
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
