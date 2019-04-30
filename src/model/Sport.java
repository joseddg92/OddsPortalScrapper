package model;

import java.io.Serializable;

import scrapper.ParserListener;

public class Sport implements Notifiable, Serializable {

	private static final long serialVersionUID = 1L;
	
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
