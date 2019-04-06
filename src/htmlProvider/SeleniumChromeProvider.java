package htmlProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import model.WebSection;

public class SeleniumChromeProvider implements AutoCloseable {

	private static final int DEFAULT_WEBLOAD_TIMEOUT_SEC = 60;
	private static final Function<? super WebDriver, Boolean> jsReadyCondition =
			(ExpectedCondition<Boolean>) wd -> ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete");

	private ChromeDriver driver = new ChromeDriver();
	private int loadTimeout = DEFAULT_WEBLOAD_TIMEOUT_SEC;
	
	public SeleniumChromeProvider() {
		//driver.manage().window().setPosition(new Point(1600, 0));
		//driver.manage().window().maximize();
		driver.manage().timeouts().pageLoadTimeout(loadTimeout, TimeUnit.SECONDS);
	}	
	
	public Map<WebSection, Document> getAllTabs(String url) {
		Map<WebSection, Document> docPerTab = new HashMap<>();

		Document doc = get(url);
		Elements tabs = doc.select("div#bettype-tabs li a");
		/* The tab that is selected by default is the current element and does not have
		 * a <a> element, so manually append it. */
		Element activeTab = doc.selectFirst("div#bettype-tabs li.active strong span");
		String tabTitle = activeTab.text();
		tabs.add(0, activeTab);
		boolean firstTab = true;
		for (Element tab : tabs) {
			// Skip "More bets" tab
			if (tab.parent().attr("class").contains("more"))
				continue;

			if (!firstTab) {
				tabTitle = tab.attr("title");
				if (tabTitle.isEmpty())
					tabTitle = tab.text();

				String jsCode = tab.attr("onmousedown");
				System.out.println("Loading tab " + tabTitle);
				driver.executeScript(jsCode);
				waitJs();
				doc = get(url);
			} else {
				System.out.println("Loading tab " + tabTitle + " (was default)");
				firstTab = false;
			}
			
			Elements subtabs = doc.select("div#bettype-tabs-scope ul[style=\"display: block;\"] li a");
			Element activeSubtab = doc.selectFirst("div#bettype-tabs-scope ul[style=\"display: block;\"] li.active strong span");
			if (activeSubtab == null)
				continue;
			String subtabTitle = activeSubtab.text();
			subtabs.add(0, activeSubtab);
			boolean firstSubTab = true;
			for (Element subtab : subtabs) {
				if (!firstSubTab) {
					subtabTitle = subtab.attr("title");
					String jsCode = subtab.attr("onmousedown");
					System.out.println("Loading subtab " + tabTitle + " > " + subtabTitle);
					driver.executeScript(jsCode);
					waitJs();
					doc = get(url);
				} else {
					System.out.println("Loading subtab " + tabTitle + " > " + subtabTitle + " (was default)");
					firstSubTab = false;
				}
				
				docPerTab.put(new WebSection(tabTitle, subtabTitle), doc);
			}
		}
		
		return docPerTab;
	}
	
	public Document get(String url) {
		driver.get(url);
		waitJs();

		Document doc = Jsoup.parse(driver.getPageSource(), driver.getCurrentUrl());

		return doc;
	}
	
	@Override
	public void close() throws Exception {
		driver.close();
	}
	
	private void waitJs() {
		new WebDriverWait(driver, loadTimeout).until(jsReadyCondition);
	}
}
