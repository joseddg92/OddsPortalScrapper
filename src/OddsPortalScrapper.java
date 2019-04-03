import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class OddsPortalScrapper implements AutoCloseable {
	
	private static final String ENTRY_URL = "https://www.oddsportal.com/events/";
	
	private static final int WEB_LOAD_TIMEOUT_SEC = 60;
	private static final Function<? super WebDriver, Boolean> jsReadyCondition =
			(ExpectedCondition<Boolean>) wd -> ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete");
			
	private WebDriver driver;
	
	public OddsPortalScrapper() {
		driver = new ChromeDriver();
		driver.manage().timeouts().pageLoadTimeout(WEB_LOAD_TIMEOUT_SEC, TimeUnit.SECONDS);
	}
	
	private void load(String url) {
		System.out.println("Loading " + url + "...");
		
		long startTime = System.currentTimeMillis();
		driver.get(url);
		new WebDriverWait(driver, WEB_LOAD_TIMEOUT_SEC).until(jsReadyCondition);
		long estimatedTime = System.currentTimeMillis() - startTime;
		
		System.out.println("Loaded in " + estimatedTime + " ms");
	}
	
	public void run() throws Exception {
		try {
			load(ENTRY_URL);
		} finally {
			System.in.read();
		}
	}
	
	@Override
	public void close() throws Exception {
		driver.close();
	}
	
	public static void main(String[] args) throws Exception {
		System.setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");
		try (OddsPortalScrapper scrapper = new OddsPortalScrapper()) {
			scrapper.run();
		}
	}
}