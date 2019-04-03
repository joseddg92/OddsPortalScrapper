import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

public class OddsPortalScrapper {
	
	public enum SectionParser {
		LIVE_TENIS(
			"https://www.oddsportal.com/inplay-odds/live-now/tennis/",
			"#live-match-data td.name.table-participant"
				);
		
		public final String baseUrl;
		public final String matchesSelector;
		
		SectionParser(String baseUrl, String matchesSelector) {
			this.baseUrl = baseUrl;
			this.matchesSelector = matchesSelector;
		}
	}
	
	public static void main(String[] args) throws Exception {
		System.setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");
		WebDriver driver = new ChromeDriver();
		
		try {
			System.out.println("Loading...");
			driver.get(SectionParser.LIVE_TENIS.baseUrl);
			sleep(5);
		
			System.out.println("Matches:");
			List<WebElement> matches = driver.findElements(By.cssSelector(SectionParser.LIVE_TENIS.matchesSelector));
			for (WebElement match : matches) {
				System.out.println("\t" + match.findElement(By.tagName("a")).getText());
			}
		} finally {
			System.in.read();
			driver.close();
		}
	}
	
	private static void sleep(double seconds) {
		try {
			Thread.sleep((long) (seconds * 1000));
		} catch (InterruptedException e) {}
	}
}