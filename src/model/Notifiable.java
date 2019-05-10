package model;

import scrapper.ParserListener;
import scrapper.RequestStatus;

public interface Notifiable {
	boolean notify(RequestStatus status, ParserListener listener);
}
