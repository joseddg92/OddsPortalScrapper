package htmlProvider;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import model.WebData;

public class SeleniumChromeProvider implements AutoCloseable {

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
	private RWDUtils driverUtils;
	private boolean headless;

	public SeleniumChromeProvider() {
		this(false);
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
		if (!headless)
			driver.manage().window().maximize();
		driver.manage().timeouts().pageLoadTimeout(RWDUtils.WEBLOAD_TIMEOUT_SEC, TimeUnit.SECONDS);
		driverUtils = new RWDUtils(driver);
	}	

	public WebData get() {
		return get(null);
	}
	
	public WebData get(String url) {
		if (url != null) {
			driver.get(url);
		}
		driverUtils.waitJs();
		WebData webData = WebData.fromProvider(this);
		
		if (!driverUtils.isLoggedIn(webData.getDoc())) {
			System.out.println("Not logged in, login in...");
			driverUtils.logIn();
			return get(url);
		}
		
		return webData;
	}
	
	public <T> T handle(Function<RemoteWebDriver, T> handler) {
		return handler.apply(driver);
	}
	
	@Override
	public void close() throws Exception {
		driver.close();
		driver.quit();
	}
}
