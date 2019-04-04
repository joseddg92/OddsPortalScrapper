import org.openqa.selenium.WebElement;

public class ScrapException extends Exception {

	private static final long serialVersionUID = 7736898556227276127L;
	public final WebElement we;
	
	public ScrapException(String reason) {
		this(reason, null);
	}
	
	public ScrapException(String reason, WebElement we) {
		super(reason);
		this.we = we;
	}
}
