package util;

import java.util.Objects;

public class StringDate {

	private String text;
	private Long timeStamp;
	
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
		return text + (timeStamp == null ? "<X>" : "");
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
		// TODO
	}
}
