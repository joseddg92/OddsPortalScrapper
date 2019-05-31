package model;

import java.io.Serializable;

import scrapper.ParserListener;
import scrapper.RequestStatus;

public class League implements Notifiable, Serializable {

	private static final long serialVersionUID = 1L;

	public final Sport sport;
	public final Country country;
	public final String name;
	public final String relUrl;

	public League(Sport sport, Country country, String name, String url) {
		this.sport = sport;
		this.country = country;
		this.name = name;
		this.relUrl = url;
	}

	@Override
	public String toString() {
		return "League[" + sport.name + "/" + country.name + "/" + name + "]";
	}

	@Override
	public boolean notify(RequestStatus status, ParserListener listener) {
		return listener.onElementParsed(status, this);
	}
}
