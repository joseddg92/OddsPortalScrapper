package util;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class StringDate implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String text;
	private Long timeStamp;

	private static List<SimpleDateFormat> ODDSPORTAL_DATE_FORMATS = Arrays.asList(
			new SimpleDateFormat("dd MMM, hh:mm", Locale.ENGLISH) /*02 Apr, 11:47*/
	);
	public StringDate(String text) {
		this.text = text;
		timeStamp = null;
		tryParse();
	}
	
	public StringDate(long timestamp) {
		this.text = String.valueOf(timestamp);
		this.timeStamp = timestamp;
	}
	
	public String getText() {
		return (timeStamp == null ? text + " /!\\" : new Date(timeStamp).toString());
	}

	public Long getTimeStamp() {
		return timeStamp;
	}
	@Override
	public String toString() {
		return text;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof StringDate))
			return false;
		StringDate o = (StringDate) other;
		return Objects.equals(text, o.text) && Objects.equals(timeStamp, o.timeStamp);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(text, timeStamp);
	}
	
	private void tryParse() {
		List<ParseException> exceptions = new ArrayList<>(ODDSPORTAL_DATE_FORMATS.size());
		for (SimpleDateFormat sdf : ODDSPORTAL_DATE_FORMATS) {
			try {	
				timeStamp = sdf.parse(text).getTime();
				break;
			} catch (ParseException e) {
				exceptions.add(e);
			}
			
			System.err.println("Couldn't parse date:" + text);
			Utils.zip(ODDSPORTAL_DATE_FORMATS, exceptions).forEach(
					pair -> System.err.format("\t%s \t-> %s\n", pair.first, pair.second)
			);
		}
	}
}
