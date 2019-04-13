package scrapper;

import model.League;
import model.Match;
import model.Sport;

public interface ParserListener {
	/* Return true to keep parsing, false to stop */
	boolean onElementParsed(Sport s);
	boolean onElementParsed(League l);
	boolean onElementParsed(Match m);
}
