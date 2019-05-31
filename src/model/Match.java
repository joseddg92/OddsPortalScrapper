package model;

import java.io.Serializable;

import scrapper.ParserListener;
import scrapper.RequestStatus;

public class Match implements Notifiable, Serializable {

	private static final long serialVersionUID = 1L;

	public final League league;
	public final String name;
	public final String url;
	public final boolean isLive;
	private final String webKey;

	public Match(League league, String name, String url, boolean isLive, String webKey) {
		this.league = league;
		this.name = name;
		this.url = url;
		this.isLive = isLive;
		this.webKey = webKey;
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
		return webKey;
	}

	@Override
	public String toString() {
		return "Match<" + getKey() + ">[" + league + "," + name + (isLive ? " <LIVE!> " : "") + "]";
	}

	@Override
	public boolean notify(RequestStatus status, ParserListener listener) {
		return listener.onElementParsed(status, this);
	}
}
