package model;

import scrapper.ParserListener;

public interface Notifiable {
	public boolean notify(ParserListener listener);
}
