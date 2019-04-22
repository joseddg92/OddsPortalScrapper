package model;

import scrapper.ParserListener;

public class Match implements Notifiable {

	public final League league;
	public final String name;
	public final String url;
	
	public Match(League league, String name, String url) {
		this.league = league;
		this.name = name;
		this.url = url;
	}
	
	public String getLocalTeam() {
		String parts[] = name.split(" - ");
		return parts[0];
	}
	
	public String getVisitorTeam() {
		String parts[] = name.split(" - ");
		return parts.length > 1 ? parts[1] : null;
	}
	
	public String getKey() {
		int beginIndex = url.lastIndexOf("-");
		int endIndex = url.lastIndexOf("/");
		if (endIndex < beginIndex)
			endIndex = url.length();

		return url.substring(beginIndex + 1, endIndex);
	}

	public String toString() {
		return "Match<" + getKey() + ">[" + league + "/" + name + "]";
	}
	
	public boolean notify(ParserListener listener) {
		return listener.onElementParsed(this);
	}
}
