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

	public String toString() {
		return "Match[" + league + "/" + name + "]";
	}
	
	public boolean notify(ParserListener listener) {
		return listener.onElementParsed(this);
	}
}
