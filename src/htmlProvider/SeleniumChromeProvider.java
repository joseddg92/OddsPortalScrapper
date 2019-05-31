package htmlProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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

	private final boolean headless;
	private final ThreadLocal<ChromeDriver> driverPerThread;
	private final Queue<ChromeDriver> driverInstances;

	public SeleniumChromeProvider() {
		this(false);
	}

	public SeleniumChromeProvider(boolean headless) {
		this.headless = headless;
		driverInstances = new ConcurrentLinkedQueue<>();
		driverPerThread = ThreadLocal.withInitial(
				() -> {
					ChromeDriver driver = createRWD(headless);
					driverInstances.add(driver);
					return driver;
				}
		);
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

	public WebData get() {
		return get(null);
	}

	public WebData get(String url) {
		final RemoteWebDriver driver = driverPerThread.get();
		final RWDUtils driverUtils = new RWDUtils(driver);
		if (url != null)
			driver.get(url);

		WebData webData = WebData.fromProvider(this);

		if (!driverUtils.isLoggedIn(webData.getDoc())) {
			System.out.println("Not logged in, login in...");
			driverUtils.logIn();
			return get(url);
		}

		return webData;
	}

	public <T> T handle(Function<RemoteWebDriver, T> handler) {
		return handler.apply(driverPerThread.get());
	}

	@Override
	public void close() {
		// TODO: Ensure (or wait until) no usages of driver are in progress.
		driverInstances.forEach(driver -> driver.quit());
	}
}
