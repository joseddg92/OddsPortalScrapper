package model;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;

public class WebData {

	private Document doc;
	
	private WebData() {
		
	}
	
	public Document getDoc() {
		return doc;
	}
	
	public static WebData fromDriver(WebDriver driver) {
		final WebData wb = new WebData();
		wb.doc = Jsoup.parse(driver.getPageSource(), driver.getCurrentUrl());
		return wb;
	}
}
