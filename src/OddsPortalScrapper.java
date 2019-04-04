import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class OddsPortalScrapper implements AutoCloseable {
	
	private static final String ENTRY_URL = "https://www.oddsportal.com/events/";
	private static final String SPORT_URL_FORMAT = "https://www.oddsportal.com/events/#sport/%s/all";
	
	private static final int DEFAULT_WEBLOAD_TIMEOUT_SEC = 60;
	private static final Function<? super WebDriver, Boolean> jsReadyCondition =
			(ExpectedCondition<Boolean>) wd -> ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete");
	private static final Pattern sportsOnClickToURL_regex = Pattern.compile("tab_sport_main.select\\( '(.*)'\\);this.blur\\(\\);return false;");	
			
	private WebDriver driver;
	private List<ScrapException> errors = new ArrayList<>();
	
	public OddsPortalScrapper() {
		driver = new ChromeDriver();
		driver.manage().window().setPosition(new Point(1600, 0));
		driver.manage().window().maximize();
		driver.manage().timeouts().pageLoadTimeout(DEFAULT_WEBLOAD_TIMEOUT_SEC, TimeUnit.SECONDS);
	}
	
	private void logError(ScrapException e) {
		StackTraceElement errorLine = e.getStackTrace()[0];
		
		System.err.println(errorLine.getMethodName() + errorLine.getLineNumber() + ":  " + e.getMessage());
		
		errors.add(e);
	}
	
	private void loadAndWait(String url) {
		loadAndWait(url, DEFAULT_WEBLOAD_TIMEOUT_SEC);
	}
	
	private void loadAndWait(String url, int timeout) {
		System.out.println("Loading " + url + "...");
		
		long startTime = System.currentTimeMillis();
		driver.get(url);
		new WebDriverWait(driver, DEFAULT_WEBLOAD_TIMEOUT_SEC).until(jsReadyCondition);
		long estimatedTime = System.currentTimeMillis() - startTime;
		
		System.out.println("Loaded in " + estimatedTime + " ms");

	}
	
	private List<String> getSports() throws ScrapException {
		List<WebElement> tabs = driver.findElements(By.cssSelector("div#tabdiv_sport_main li.tab"));
		List<String> sports = new ArrayList<>(tabs.size());
		
		for (WebElement tab : tabs) {	
			String onClickAttr = tab.findElement(By.cssSelector("a")).getAttribute("onclick");
			Matcher m = sportsOnClickToURL_regex.matcher(onClickAttr);
			if (m.find()) {
				String sport = m.group(1);
				sports.add(sport);
			} else {
				logError(new ScrapException("Parsing a sport tab 'onclick' attribute, the regex did not match: " + onClickAttr, tab));
			}
		}
		
		return sports;
	}
	
	private void parseSport(String sport) {
		long startTime = System.currentTimeMillis();
		System.out.println("Parsing sport=" + sport + " ...");
		
		loadAndWait(String.format(SPORT_URL_FORMAT, sport));
		
		List<WebElement> rows = driver.findElements(By.cssSelector("tbody tr"));
		if (rows.isEmpty()) {
			logError(new ScrapException("Sport " + sport +  " contained no rows"));
			return;
		}
		
		String country = "";
		for (WebElement row : rows) {
			if (row.getAttribute("class").contains("center") && !row.getAttribute("class").contains("dark")) {
				
				/* Skip 'popular' category */
				String xcid = row.getAttribute("xcid");
				if (xcid != null && xcid.contains("popular"))
					continue;
				try {
					country = row.findElement(By.cssSelector("a.bfl")).getText();
				} catch (NoSuchElementException e) {
					logError(new ScrapException("Center-row did not contain a 'bfl' child to get name", row));
					System.out.println(row.getAttribute("innerHTML"));
				}
			}
			
			/* Skip orphan subcategories (which should only be "popular" subcategories) */
			if (country.isEmpty())
				continue;
			
			List<WebElement> tdElements = row.findElements(By.cssSelector("td"));
			for (WebElement tdElement : tdElements) {
				/* Skip empty elements */
				if (tdElement.getText().trim().isEmpty() && tdElement.findElements(By.xpath(".//*")).isEmpty())
					continue;

				try {
					String subCategoryName = tdElement.findElement(By.cssSelector("a")).getText();
					System.out.println(sport + " -> " + country + " -> " + subCategoryName);
				} catch (NoSuchElementException e) {
					e.printStackTrace();
					System.out.println(tdElement.getAttribute("innerHTML"));
				}
				
			}
		}
		
		long estimatedTime = System.currentTimeMillis() - startTime;
		System.out.println("Sport " + sport + " parsed in " + estimatedTime / 1000.0 + " secs");
	}
	
	public void run() throws Exception {
		loadAndWait(ENTRY_URL);
		List<String> sports = getSports();
		
		for (String sport : sports) {
			loadAndWait("http://www.google.es");
			parseSport(sport);
		}
	}
	
	@Override
	public void close() throws Exception {
		driver.close();
	}
	
	public List<ScrapException> getErrors() {
		return errors;
	}
	
	public static void main(String[] args) throws Exception {
		boolean pauseOnExit = false;

		System.setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");
		try (OddsPortalScrapper scrapper = new OddsPortalScrapper()) {
			
			/* Introduce another try block so that scrapper is not closed before the catch or finally blocks */
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
			}
			
			if (pauseOnExit) {
				System.err.flush();
	
				System.out.println("Press any key to close.");
				System.in.read();
			}
		}
	}
}