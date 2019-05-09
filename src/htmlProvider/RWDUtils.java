package htmlProvider;

import java.util.function.Function;

import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class RWDUtils {
	
	public static final int WEBLOAD_TIMEOUT_SEC = 60;
	public static final int POLL_WAIT_INTERNAL = 50;
	private static final Function<? super WebDriver, Boolean> jsReadyCondition =
			(ExpectedCondition<Boolean>) wd -> ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete");
			
	private static final String USER = "sureTenis123";
	private static final String PASSWORD = "6cJJFbDMrzmt5wt";
	
	public final RemoteWebDriver driver;
	
	public RWDUtils(RemoteWebDriver rwd) {
		this.driver = rwd;
	}
	
	public void waitLoadSpinner() {
		/* 
		 * Wait for the spinner to appear, and then to disappear. 
		 * If it is not found within a second, keep going as maybe
		 * we didn't give it time to appear.
		 */
		try {
			new WebDriverWait(driver, 1, POLL_WAIT_INTERNAL).until(ExpectedConditions.visibilityOfElementLocated(By.id("event-wait-msg-main")));
		} catch (TimeoutException e) {}
		
		try {
			new WebDriverWait(driver, WEBLOAD_TIMEOUT_SEC, POLL_WAIT_INTERNAL).until(ExpectedConditions.invisibilityOfElementLocated(By.id("event-wait-msg-main")));
		} catch (TimeoutException e) {}
		
		/* After the spinner has disappeared, js is changing the DOM, so wait for it to finish */
		waitJs();
	}
	
	public void waitJs() {
		new WebDriverWait(driver, WEBLOAD_TIMEOUT_SEC, POLL_WAIT_INTERNAL).until(jsReadyCondition);
	}
	
	public void logIn() {
		driver.get("https://www.oddsportal.com/login/");
		waitJs();
		WebElement userNameTextField = driver.findElement(By.id("login-username1"));
		WebElement passwordTextfield = driver.findElement(By.id("login-password1"));
		userNameTextField.sendKeys(USER);
		passwordTextfield.sendKeys(PASSWORD);

		driver.findElement(By.cssSelector("div.item > button[name=login-submit]")).click();

		waitJs();
	}
	
	public boolean isLoggedIn(Document doc) {
		/* Assume logged for pages other than oddsportal.com */
		if (!driver.getCurrentUrl().contains("oddsportal.com"))
				return true;
		
		return doc.selectFirst("button[name=login-submit]") == null; 
	}
	
}
