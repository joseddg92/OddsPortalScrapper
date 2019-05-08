package htmlProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import model.ScrapException;
import model.WebData;
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
	private boolean headless;
	private int loadTimeout = DEFAULT_WEBLOAD_TIMEOUT_SEC;
	
	private static final String USER = "sureTenis123";
	private static final String PASSWORD = "6cJJFbDMrzmt5wt";
	
	public SeleniumChromeProvider() {
		this(false);
	}
	
	public RemoteWebDriver getDriver() {
		return driver;
	}
	
	public boolean isHeadless() {
		return headless;
	}
	
	public SeleniumChromeProvider(boolean headless) {
		this.headless = headless;

		ChromeOptions options = new ChromeOptions();
		if (headless)
			options.addArguments("--headless");
		
		options.addArguments(defaultOptions);
		driver = new ChromeDriver(options);
		//driver.manage().window().setPosition(new Point(1600, 0));
		driver.manage().window().maximize();
		driver.manage().timeouts().pageLoadTimeout(loadTimeout, TimeUnit.SECONDS);
	}	

	public WebData get() {
		return get(null);
	}
	
	public WebData get(String url) {
		if (url != null) {
			driver.get(url);
		}
		waitJs();
		WebData webData = WebData.fromProvider(this);
		
		if (!isLoggedIn(webData.getDoc())) {
			System.out.println("Not logged in, login in...");
			logIn();
			return get(url);
		}
		
		return webData;
	}
	
	private WebDriverHandler wdb = new WebDriverHandler() {
		public void waitJs() {
			SeleniumChromeProvider.this.waitJs();
		}
		
		@Override
		public RemoteWebDriver getDriver() {
			return driver;
		}
		
		@Override
		public WebData get(String url) {
			return SeleniumChromeProvider.this.get(url);
		}
		
		@Override
		public void executeScript(String code, Object... objects) {
			driver.executeScript(code, objects);
		}
	};
	
	public void getAndHandle(String url, BiConsumer<WebData, WebDriverHandler> handler) {
		handler.accept(get(url), wdb);
	}
	
	public <T> T getAndHandle(String url, BiFunction<WebData, WebDriverHandler, T> handler) {
		return handler.apply(get(url), wdb);
	}
	
	@Override
	public void close() throws Exception {
		driver.close();
		driver.quit();
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
		/* Assume logged for pages other than oddsportal.com */
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
