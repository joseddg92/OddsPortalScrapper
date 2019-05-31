package model;

import java.io.Serializable;

import scrapper.ParserListener;
import scrapper.RequestStatus;

public class Sport implements Notifiable, Serializable {

	private static final long serialVersionUID = 1L;

	public final String name;

	public Sport(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "Sport[" + name + "]";
	}

	@Override
	public boolean notify(RequestStatus status, ParserListener listener) {
		return listener.onElementParsed(status, this);
	}
}
