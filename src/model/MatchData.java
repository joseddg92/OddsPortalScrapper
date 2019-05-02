package model;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import scrapper.ParserListener;
import util.StringDate;

public class MatchData implements Notifiable, Serializable {

	private static final long serialVersionUID = 1L;
	
	public static class OddKey implements Serializable {
		
		private static final long serialVersionUID = 1L;

		public final WebSection section;
		public final String row; /* null for 1X2, not null for AH (e.g. +2.5) */
		public final String betHouse;
		public final String result;
		
		public OddKey(WebSection section, String row, String betHouse, String result) {
			/*
			 * For some categories the section.tab (cat1) is also present in row (cat3).
			 * Get rid of that so that hopefully cat3 can be parsed to a double.
			 */
			if (row != null && row.startsWith(section.tab))
				row = row.substring(section.tab.length()).trim();

			this.section = section;
			this.row = row;
			this.betHouse = betHouse;
			this.result = result;
		}
		
		@Override
		public String toString() {
			return String.format("OddKey[%s,%s%s,%s]", section, row != null ? (row + ",") : "", betHouse, result);
		}
		
		@Override
		public boolean equals(Object other) {
			if (!(other instanceof OddKey))
				return false;

			OddKey o = (OddKey) other;
			return section.equals(o.section) && row.equals(o.row) && betHouse.equals(o.betHouse) && result.equals(o.result);	
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(section, row, betHouse, result);
		}
	}
	
	final public Match match;
	final public long beginTimeStamp;
	/* e.g. {WebSection[AH, 1st Half], -1.5, 888Sport} -> [1 (meaning +) -> 8.2, 2 (meaning -) -> 1.08] */
	private Map<OddKey, Map<StringDate, Double>> odds = new LinkedHashMap<>(); 
		
	public MatchData(Match match, long beginTimeStamp) {
		this.match = match;
		this.beginTimeStamp = beginTimeStamp;
	}
	
	public void addOdds(OddKey key, Map<StringDate, Double> newOdds) {
		Map<StringDate, Double> existingOdds = odds.get(key);
		if (existingOdds == null) {
			existingOdds = new LinkedHashMap<>();
			odds.put(key, existingOdds);
		}
		
		existingOdds.putAll(newOdds);
	}
	
	public Map<OddKey, Map<StringDate, Double>> getOdds() {
		return Collections.unmodifiableMap(odds);
	}
	
	@Override
	public boolean notify(ParserListener listener) {
		return listener.onElementParsed(this);
	}

	
}
