package util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.TimeZone;

import org.jsoup.nodes.Element;

public class Utils {
	public static LocalDateTime secTimestampToDate(long timestamp) {
		return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp),
		                                TimeZone.getDefault().toZoneId());
	}
	
	public static Double tryParseDouble(String s) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	
	public static double parseDoubleEmptyIsZero(String s) throws NumberFormatException {
		if (s.trim().isEmpty())
			return 0d;
		return Double.parseDouble(s);
	}
	
	public static String combineAllText(Collection<Element> elements) {
		String s = "";
		for (Element e : elements) 
			s += e.text();
		
		return s;
	}
}
