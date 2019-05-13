package htmlProvider;

import java.util.Arrays;
import java.util.List;
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
	private final boolean headless;

	public SeleniumChromeProvider() {
		this(false);
	}

	public boolean isHeadless() {
		return headless;
	}

	private static ChromeDriver createRWD(boolean headless) {
		ChromeOptions options = new ChromeOptions();
		if (headless)
			options.addArguments("--headless");
		
		options.addArguments(defaultOptions);
		ChromeDriver driver = new ChromeDriver(options);
		if (!headless)
			driver.manage().window().maximize();
		
		return driver;
	}
	
	public SeleniumChromeProvider(boolean headless) {
		this.headless = headless;
		this.driver = createRWD(headless);
		this.driverUtils = new RWDUtils(driver);
	}	

	public WebData get() {
		return get(null);
	}
	
	public synchronized WebData get(String url) {
		if (url != null) {
			driver.get(url);
		}
		WebData webData = WebData.fromProvider(this);
		
		if (!driverUtils.isLoggedIn(webData.getDoc())) {
			System.out.println("Not logged in, login in...");
			driverUtils.logIn();
			return get(url);
		}
		
		return webData;
	}
	
	public synchronized <T> T handle(Function<RemoteWebDriver, T> handler) {
		return handler.apply(driver);
	}
	
	@Override
	public synchronized void close() throws Exception {
		driver.close();
		driver.quit();
	}
}
