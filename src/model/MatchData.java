package model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import scrapper.ParserListener;

public class MatchData implements Notifiable {

	public static class OddKey {
		public final WebSection section;
		public final String row; /* null for 1X2, not null for AH (e.g. +2.5) */
		public final String betHouse;
		
		public OddKey(WebSection section, String row, String betHouse) {
			this.section = section;
			this.row = row;
			this.betHouse = betHouse;
		}
		
		@Override
		public String toString() {
			return String.format("OddKey[%s,%s%s]", section, row != null ? (row + ",") : "", betHouse);
		}
		
		@Override
		public boolean equals(Object other) {
			if (!(other instanceof OddKey))
				return false;

			OddKey o = (OddKey) other;
			return section.equals(o.section) && row.equals(o.row) && betHouse.equals(o.betHouse);	
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(section, row, betHouse);
		}
	}
	
	final public Match match;
	final public long beginTimeStamp;
	/* e.g. {WebSection[AH, 1st Half], -1.5, 888Sport} -> [1 (meaning +) -> 8.2, 2 (meaning -) -> 1.08] */
	private Map<OddKey, Map<String,Double>> odds = new HashMap<>(); 
	
	public MatchData(Match match, long beginTimeStamp) {
		this.match = match;
		this.beginTimeStamp = beginTimeStamp;
	}
	
	public boolean addOdd(OddKey key, List<String> columns, List<Double> newOdds) {
		if (columns.size() != newOdds.size())
			return false;
		
		boolean retValue = true;
		for (int i = 0; i < columns.size(); i++)
			retValue &= addOdd(key, columns.get(i), newOdds.get(i));
		// TODO: If retValue this should probably rollback the rest of odds introduced... ?

		return retValue;

	}
	
	public boolean addOdd(OddKey key, String result, double newOdd) {
		Map<String, Double> oddsForGivenKey = odds.get(key);
		if (oddsForGivenKey == null) {
			oddsForGivenKey = new HashMap<>();
			odds.put(key, oddsForGivenKey);
		}
		
		Double oldValue = oddsForGivenKey.get(result);
		if (oldValue == null || oldValue.doubleValue() == newOdd) {
			oddsForGivenKey.put(result, newOdd);
			return true;
		} else {
			return false;
		}
	}
	
	public Map<OddKey, Map<String,Double>> getOdds() {
		return Collections.unmodifiableMap(odds);
	}
	
	@Override
	public boolean notify(ParserListener listener) {
		return listener.onElementParsed(this);
	}
}
