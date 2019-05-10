package scrapper;

import model.League;
import model.Match;
import model.MatchData;
import model.ScrapException;
import model.Sport;

public interface ParserListener {
	void onError(ScrapException e);
	/* Return true to keep parsing, false to stop */
	boolean onElementParsed(RequestStatus status, Sport s);
	boolean onElementParsed(RequestStatus status, League l);
	boolean onElementParsed(RequestStatus status, Match m);
	boolean onElementParsed(RequestStatus status, MatchData m);
}
