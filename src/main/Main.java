package main;

import java.util.List;

import model.ScrapException;
import util.EclipseTools;

public class Main {

	public static void main(String[] args) throws Exception {
		boolean pauseOnExit = false;

		EclipseTools.fixConsole();
		
		System.setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");
		try (OddsPortalScrapper scrapper = new OddsPortalScrapper()) {
			/* Introduce another try block so that scrapper 
			 * is not closed before the catch or finally blocks  */
			try {
				scrapper.run();
				
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
