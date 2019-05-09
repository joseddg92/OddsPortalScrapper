package scrapper;

import org.openqa.selenium.JavascriptExecutor;

public class DisableAjax implements AutoCloseable {
		
	private JavascriptExecutor executor;
	
	public DisableAjax(JavascriptExecutor executor) {
		this.executor = executor;
		executor.executeScript("window.originalAjax = $.ajax;");
		executor.executeScript("$.ajax = function() {}");		
	}
	
	public void close() {
		executor.executeScript("$.ajax = window.originalAjax");
	}
}
