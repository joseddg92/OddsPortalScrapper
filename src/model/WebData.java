package model;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;

public class WebData {

	private Document doc;
	private String pageSource;
	private String pageUrl;
	
	
	private WebData() {
		
	}
	
	public Document getDoc() {
		ensureDocParsed();

		return doc;
	}
	
	public static WebData fromDriver(WebDriver driver) {
		final WebData wb = new WebData();
		wb.pageSource = driver.getPageSource();
		wb.pageUrl = driver.getCurrentUrl();

		return wb;
	}
	
	private void ensureDocParsed() {
		if (doc != null)
			return;
	
		doc = Jsoup.parse(pageSource, pageUrl);
		pageSource = null;
		pageUrl = null;
	}
}
