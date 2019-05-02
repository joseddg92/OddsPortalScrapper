package model;

import java.io.Serializable;

import scrapper.ParserListener;

public class Match implements Notifiable, Serializable {

	private static final long serialVersionUID = 1L;
	
	public final League league;
	public final String name;
	public final String url;
	public final boolean isLive;
	
	public Match(League league, String name, String url, boolean isLive) {
		this.league = league;
		this.name = name;
		this.url = url;
		this.isLive = isLive;
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
		return "Match<" + getKey() + ">[" + league + "," + name + (isLive ? " <LIVE!> " : "") + "]";
	}
	
	public boolean notify(ParserListener listener) {
		return listener.onElementParsed(this);
	}
}
