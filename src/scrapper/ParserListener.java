package scrapper;

import model.League;
import model.Match;
import model.ScrapException;
import model.Sport;

public interface ParserListener {
	void onError(ScrapException e);
	/* Return true to keep parsing, false to stop */
	boolean onElementParsed(Sport s);
	boolean onElementParsed(League l);
	boolean onElementParsed(Match m);
}
