package model;
import java.io.IOException;
import java.io.PrintWriter;

import org.jsoup.nodes.Element;

public class ScrapException extends Exception {

	private static final long serialVersionUID = 7736898556227276127L;
	public final Element element;
	
	public ScrapException(String reason) {
		this(reason, null);	
	}
	
	public ScrapException(String reason, Element we) {
		this(reason, we, null);
	}
	
	public ScrapException(String reason, Element we, Exception e) {
		super(reason, e);
		this.element = we;
	}
	
	public void logTo(PrintWriter out) throws IOException {
		printStackTrace(out);
		out.append("\n");
	
		if (element != null) {
			out.append("HTML element:\n");
			out.append(element.outerHtml());
			out.append("\n");
			
			Element root = element.parents().last();
			if (root != null) {
				out.append("HTML document:\n");
				out.append(root.outerHtml());
				out.append("\n");
			}
		}
		
	}
}
