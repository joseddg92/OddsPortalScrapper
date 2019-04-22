package htmlProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import model.ScrapException;
import model.WebSection;
import util.Pair;

public class SeleniumChromeProvider implements AutoCloseable {

	private static final int DEFAULT_WEBLOAD_TIMEOUT_SEC = 60;
	private static final int POLL_WAIT_INTERNAL = 50;
	private static final Function<? super WebDriver, Boolean> jsReadyCondition =
			(ExpectedCondition<Boolean>) wd -> ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete");
	
	private static final List<String> defaultOptions = Arrays.asList(
			"--no-sandbox",
			"--disable-impl-side-painting",
			"--disable-setuid-sandbox",
			"--disable-seccomp-filter-sandbox",
			"--disable-breakpad",
			"--disable-client-side-phishing-detection",
			"--disable-cast",
			"--disable-cast-streaming-hw-encoding",
			"--disable-cloud-import",
			"--disable-popup-blocking",
			"--ignore-certificate-errors",
			"--disable-session-crashed-bubble",
			"--disable-ipv6",
			"--allow-http-screen-capture",
			"--blink-settings=imagesEnabled=false"
	);
			
	private ChromeDriver driver;
	private int loadTimeout = DEFAULT_WEBLOAD_TIMEOUT_SEC;
	
	private static final String USER = "sureTenis123";
	private static final String PASSWORD = "6cJJFbDMrzmt5wt";
	
	public SeleniumChromeProvider() {
		this(false);
	}
	
	public SeleniumChromeProvider(boolean headless) {
		ChromeOptions options = new ChromeOptions();
		if (headless)
			options.addArguments("--headless");
		
		options.addArguments(defaultOptions);
		driver = new ChromeDriver(options);
		//driver.manage().window().setPosition(new Point(1600, 0));
		driver.manage().window().maximize();
		driver.manage().timeouts().pageLoadTimeout(loadTimeout, TimeUnit.SECONDS);
	}	
	
	public Map<WebSection, Pair<Document, List<String>>> getAllTabs(String url) throws ScrapException {
		// Use LinkedHashMap as it could be important to keep the original tab order
		Map<WebSection, Pair<Document, List<String>>> docPerTab = new LinkedHashMap<>();

		Document doc = get(url);
		Elements tabs = doc.select("div#bettype-tabs li a");
		/* The tab that is selected by default is the current element and does not have
		 * a <a> element, so manually append it. */
		Element activeTab = doc.selectFirst("div#bettype-tabs li.active strong span");
		if (activeTab == null) {
			if (tabs.size() != 0)
				throw new ScrapException("tabs != 0 but no active tab", doc);
			return docPerTab;
		}
		
		String tabTitle = activeTab.text();
		tabs.add(0, activeTab);
		boolean firstTab = true;
		for (Element tab : tabs) {
			// Skip "More bets" tab
			if (tab.parent().attr("class").contains("more"))
				continue;

			if (!firstTab) {
				tabTitle = tab.attr("title");
				if (tabTitle.isEmpty())
					tabTitle = tab.text();

				String jsCode = tab.attr("onmousedown");
				System.out.println("Loading tab " + tabTitle);
				driver.executeScript(jsCode);
				waitLoadSpinner();
				doc = get();
			} else {
				System.out.println("Loading tab " + tabTitle + " (was default)");
				firstTab = false;
			}
			
			Elements subtabs = doc.select("div#bettype-tabs-scope ul[style=\"display: block;\"] li a");
			Element activeSubtab = doc.selectFirst("div#bettype-tabs-scope ul[style=\"display: block;\"] li.active strong span");
			if (activeSubtab == null)
				continue;
			String subtabTitle = activeSubtab.text();
			subtabs.add(0, activeSubtab);
			boolean firstSubTab = true;
			for (Element subtab : subtabs) {
				if (!firstSubTab) {
					subtabTitle = subtab.attr("title");
					String jsCode = subtab.attr("onmousedown");
					System.out.println("Loading subtab " + tabTitle + " > " + subtabTitle);
					driver.executeScript(jsCode);
					waitLoadSpinner();
					doc = get();
				} else {
					System.out.println("Loading subtab " + tabTitle + " > " + subtabTitle + " (was default)");
					firstSubTab = false;
				}
				
				/* Expand all bet groups by 'clicking' on them */
				for (Element rowToBeExpanded : doc.select("#odds-data-table > div > div > strong > a"))
					driver.executeScript(rowToBeExpanded.attr("onclick"));
				
				
				List<WebElement> oddElements = driver.findElementsByCssSelector(
						"div#odds-data-table div.table-container:not(.exchangeContainer) table > tbody > tr td.odds"
				);
				List<String> htmlOddHistoryFragments = new ArrayList<>(oddElements.size());
				for (WebElement oddElement : oddElements) {
					new Actions(driver).moveToElement(oddElement).perform();
					waitJs();
					try {
						htmlOddHistoryFragments.add(driver.findElementById("tooltipdiv").getAttribute("outerHTML"));
					} catch (NoSuchElementException e) {
						/* Add a null history to keep the rest aligned */
						htmlOddHistoryFragments.add(null);
					}
				}
				
				doc = get();
				docPerTab.put(new WebSection(tabTitle, subtabTitle), Pair.create(doc, htmlOddHistoryFragments));
			}
		}
		
		return docPerTab;
	}
	
	public Document get() {
		return get(null);
	}
	
	public Document get(String url) {
		if (url != null) {
			System.out.println("Getting " + url + "...");
			driver.get(url);
		}
		waitJs();
		Document doc = Jsoup.parse(driver.getPageSource(), driver.getCurrentUrl());
		
		if (!isLoggedIn(doc)) {
			System.out.println("Not logged in, login in...");
			logIn();
			return get(url);
		}
		
		return doc;
	}
	
	@Override
	public void close() throws Exception {
		driver.close();
	}
	
	private void logIn() {
		driver.get("https://www.oddsportal.com/login/");
		waitJs();
		WebElement userNameTextField = driver.findElement(By.id("login-username1"));
		WebElement passwordTextfield = driver.findElement(By.id("login-password1"));
		userNameTextField.sendKeys(USER);
		passwordTextfield.sendKeys(PASSWORD);

		driver.findElement(By.cssSelector("div.item > button[name=login-submit]")).click();

		waitJs();
	}
	
	private boolean isLoggedIn(Document doc) {
		/* Assume logged for pages othe than oddsportal.com */
		if (!driver.getCurrentUrl().contains("oddsportal.com"))
				return true;
		
		return doc.selectFirst("button[name=login-submit]") == null; 
	}
	
	private void waitJs() {
		new WebDriverWait(driver, loadTimeout, POLL_WAIT_INTERNAL).until(jsReadyCondition);
	}
	
	private void waitLoadSpinner() {
		/* 
		 * Wait for the spinner to appear, and then to disappear. 
		 * If it is not found within a second, keep going as maybe
		 * we didn't give it time to appear.
		 */
		try {
			new WebDriverWait(driver, 1, POLL_WAIT_INTERNAL).until(ExpectedConditions.visibilityOfElementLocated(By.id("event-wait-msg-main")));
		} catch (TimeoutException e) {}
		
		try {
			new WebDriverWait(driver, loadTimeout, POLL_WAIT_INTERNAL).until(ExpectedConditions.invisibilityOfElementLocated(By.id("event-wait-msg-main")));
		} catch (TimeoutException e) {}
		
		/* After the spinner has disappeared, js is changing the DOM, so wait for it to finish */
		waitJs();
	}
}
