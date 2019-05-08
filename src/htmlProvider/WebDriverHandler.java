package htmlProvider;

import org.openqa.selenium.remote.RemoteWebDriver;

import model.WebData;

public interface WebDriverHandler {

	RemoteWebDriver getDriver();
	void executeScript(String code, Object... objects);
	
	default WebData get() {
		return get(null);
	}

	WebData get(String url);
	void waitJs();
	
}
