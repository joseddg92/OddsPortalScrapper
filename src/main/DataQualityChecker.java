package main;

import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import model.League;
import model.Match;
import model.MatchData;
import model.MatchData.OddKey;
import model.ScrapException;
import model.Sport;
import scrapper.ParserListener;
import scrapper.RequestStatus;
import util.StringDate;

public class DataQualityChecker implements ParserListener {

	private static final Pattern webKeyPattern = Pattern.compile("[a-zA-Z0-9]{8}");

	@Override
	public void onError(ScrapException e) {

	}

	@Override
	public boolean onElementParsed(RequestStatus status, Sport s) {
		if (s == null)
			System.err.println("Null sport!!" + s);

		if (empty(s.name))
			System.err.println("Empty name on sport!! " + s);

		return true;
	}

	@Override
	public boolean onElementParsed(RequestStatus status, League l) {
		if (l == null || l.country == null || l.sport == null)
			System.err.println("Null league: " + l);

		if (empty(l.country.name) || empty(l.sport.name))
			System.err.println("Empty name on league!!" + l);

		return true;
	}

	@Override
	public boolean onElementParsed(RequestStatus status, Match m) {
		if (m == null || m.league == null)
			System.err.println("Null match!!" + m);

		if (empty(m.name) || empty(m.url))
			System.err.println("Empty " + m);

		if (empty(m.getKey()) || empty(m.getLocalTeam()) || empty(m.getVisitorTeam()))
			System.err.println("Bad data" + m);

		if (!webKeyPattern.matcher(m.getKey()).matches())
			System.err.println("BAD KEY");


		return true;
	}

	@Override
	public boolean onElementParsed(RequestStatus status, MatchData m) {
		if (m == null || m.match == null)
			System.err.println("Empty " + m);

		Map<OddKey, Map<StringDate, Double>> allOdds = m.getOdds();
		if (allOdds.isEmpty())
			System.err.println("Empty map" + m);
		for (Entry<OddKey, Map<StringDate, Double>> e : allOdds.entrySet()) {
			final OddKey key = e.getKey();
			final Map<StringDate, Double> value = e.getValue();

			if (empty(key.section.subtab) || empty(key.section.tab))
					System.err.println("Bad OddKey" + m);

			if (value == null || value.isEmpty())
				System.err.println("Bad value:" + key + " is empty");

			for (Entry<StringDate, Double> e2 : value.entrySet()) {
				if (!(e2.getValue() == 0 || e2.getValue() >= 1))
					System.err.println("Bad odd: " + key + " > " + e2.getKey() + " -> " + e2.getValue());
			}
		}
		return true;
	}

	private static boolean empty(String s) {
		return s == null || s.trim().isEmpty();
	}


}
