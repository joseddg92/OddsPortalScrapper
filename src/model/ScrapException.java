package model;
import org.jsoup.nodes.Element;

public class ScrapException extends Exception {

	private static final long serialVersionUID = 7736898556227276127L;
	public final Element element;
	
	public ScrapException(String reason) {
		this(reason, null);	
	}
	
	public ScrapException(String reason, Element we) {
		this(reason, null, null);
	}
	
	public ScrapException(String reason, Element we, Exception e) {
		super(reason, e);
		this.element = we;
	}
	
}
