package model;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

import htmlProvider.SeleniumChromeProvider;

public class WebData {

	private Document doc;
	private String pageSource;
	private String pageUrl;
	private byte[] screenShot;
	
	
	private WebData() {
		
	}
	
	public Document getDoc() {
		ensureDocParsed();

		return doc;
	}
	
	public byte[] getScreenShot() {
		return screenShot;
	}
	
	public static WebData fromProvider(SeleniumChromeProvider provider) {
		return provider.handle((driver) -> {
			final WebData wb = new WebData();
			wb.pageSource = driver.getPageSource();
			wb.pageUrl = driver.getCurrentUrl();
	
			if (!provider.isHeadless() && driver instanceof TakesScreenshot) {
				try {
					wb.screenShot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
				} catch (Exception e) {}
			}
	
			return wb;
		});
	}
	
	private void ensureDocParsed() {
		if (doc != null)
			return;
	
		doc = Jsoup.parse(pageSource, pageUrl);
		pageSource = null;
		pageUrl = null;
	}
}
