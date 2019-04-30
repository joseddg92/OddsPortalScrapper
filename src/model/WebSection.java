package model;

import java.io.Serializable;
import java.util.Objects;

public class WebSection implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public final String tab;
	public final String subtab;
	
	public WebSection(String tab, String subtab) {
		this.tab = tab;
		this.subtab = subtab;
	}

	@Override
	public String toString() {
		return "WebSection[" + tab + ">" + subtab + "]";
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof WebSection))
			return false;
		
		WebSection other = (WebSection) o;
		return tab.equals(other.tab) && subtab.equals(other.subtab);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(tab, subtab);
	}
}
