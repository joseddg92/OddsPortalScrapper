package model;

import scrapper.ParserListener;

public class Sport implements Notifiable {

	public final String name;
	
	public Sport(String name) {
		this.name = name;
	}

	public String toString() {
		return "Sport[" + name + "]";
	}

	public boolean notify(ParserListener listener) {
		return listener.onElementParsed(this);
	}
}
