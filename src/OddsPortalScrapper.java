import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class OddsPortalScrapper implements AutoCloseable {
	
	private static final String BASE_URL = "https://www.oddsportal.com";
	private static final String ENTRY_URL = BASE_URL + "/events/";
	private static final String SPORT_URL_FORMAT = "https://www.oddsportal.com/events/#sport/%s/all";
	
	private static final int DEFAULT_WEBLOAD_TIMEOUT_SEC = 60;
	private static final Function<? super WebDriver, Boolean> jsReadyCondition =
			(ExpectedCondition<Boolean>) wd -> ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete");
	private static final Pattern sportsOnClickToURL_regex = Pattern.compile("tab_sport_main.select\\( '(.*)'\\);this.blur\\(\\);return false;");	
			
	private WebDriver driver;
	private List<ScrapException> errors = new ArrayList<>();
	
	public OddsPortalScrapper() {
		driver = new ChromeDriver();
	//	driver.manage().window().setPosition(new Point(1600, 0));
		driver.manage().window().maximize();
		driver.manage().timeouts().pageLoadTimeout(DEFAULT_WEBLOAD_TIMEOUT_SEC, TimeUnit.SECONDS);
	}
	
	private void logError(ScrapException e) {
		StackTraceElement errorLine = e.getStackTrace()[0];
		
		System.err.println("Non-critical error: " + errorLine.getMethodName() + ":" +  errorLine.getLineNumber() + "- >  " + e.getMessage());
		errors.add(e);
	}
	
	private Document loadAndWait(String url) {
		return loadAndWait(url, DEFAULT_WEBLOAD_TIMEOUT_SEC);
	}
	
	private Document loadAndWait(String url, int timeout) {
		System.out.println("Loading " + url + "...");
		
		long startTime = System.currentTimeMillis();
		driver.get(url);
		new WebDriverWait(driver, DEFAULT_WEBLOAD_TIMEOUT_SEC).until(jsReadyCondition);
		long estimatedTime = System.currentTimeMillis() - startTime;
		
		Document doc = Jsoup.parse(driver.getPageSource(), driver.getCurrentUrl());
		
		System.out.println("Loaded in " + estimatedTime + " ms");
		return doc;
	}
	
	private List<String> getSports() throws ScrapException {
		Document startPage = loadAndWait(ENTRY_URL);
		Elements tabs = startPage.select("div#tabdiv_sport_main li.tab");
		List<String> sports = new ArrayList<>(tabs.size());
		
		for (Element tab : tabs) {	
			String onClickAttr = tab.selectFirst("a").attr("onclick");
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
	
	private List<League> parseSport(String sportName) {
		final Sport sport = new Sport(sportName);
		List<League> leagues = new ArrayList<>();
		long startTime = System.currentTimeMillis();
		System.out.println("Parsing sport=" + sportName + " ...");
		
		loadAndWait("http://www.google.es");
		Document doc = loadAndWait(String.format(SPORT_URL_FORMAT, sportName));
		
		Elements rows = doc.select("tbody tr");
		if (rows.isEmpty()) {
			logError(new ScrapException("Sport " + sportName +  " contained no rows"));
			return leagues;
		}
		
		String countryName = "";
		for (Element row : rows) {
			if (row.attr("class").contains("center") && !row.attr("class").contains("dark")) {
				
				/* Skip 'popular' category */
				String xcid = row.attr("xcid");
				if (xcid != null && xcid.contains("popular"))
					continue;
				try {
					countryName = row.select("a.bfl").text();
				} catch (NoSuchElementException e) {
					logError(new ScrapException("Center-row did not contain a 'bfl' child to get name", row));
				}
			}
			
			/* Skip orphan subcategories (which should only be "popular" subcategories) */
			if (countryName.isEmpty())
				continue;
			
			Elements tdElements = row.select("td");
			final Country country = new Country(countryName);
			for (Element tdElement : tdElements) {
				Element linkElement = tdElement.selectFirst("a");
				/* Skip empty elements */
				if (linkElement == null)
					continue;
				
				String leagueName = linkElement.text();
				String relativeUrl = linkElement.attr("href");
				
				if (leagueName.trim().isEmpty())
					continue;
				
				if (relativeUrl.trim().isEmpty()) {
					logError(new ScrapException("League with empty link", tdElement));
					continue;
				}
				
				League l = new League(sport, country, leagueName, relativeUrl);
				System.out.println(l);
	
				leagues.add(l);
			}
		}
		
		long estimatedTime = System.currentTimeMillis() - startTime;
		System.out.println("Sport " + sportName + " parsed (" + leagues.size() + " leagues found) in " + estimatedTime / 1000.0 + " secs");
		
		return leagues;
	}
	
	private List<?> parseLeague(League league) {
		List<League> leagues = new ArrayList<>();
		Document doc = loadAndWait(BASE_URL + league.relUrl);
		
		return leagues;
	}
	
	public void run() throws Exception {
		long startTime = System.currentTimeMillis();
		
		for (String sport : getSports()) {
			for (League league : parseSport(sport)) {
				parseLeague(league);
			}
		}
		
		long elapsedTime = System.currentTimeMillis() - startTime;
		System.out.println("Complete execution took " + elapsedTime / 1000 + " seconds.");
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