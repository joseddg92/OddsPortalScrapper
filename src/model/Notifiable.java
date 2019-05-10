package model;

import scrapper.ParserListener;
import scrapper.RequestStatus;

public interface Notifiable {
	public boolean notify(RequestStatus status, ParserListener listener);
}
