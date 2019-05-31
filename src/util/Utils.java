package util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Element;

public class Utils {

	public static <E> List<E> union(final List<? extends E> list1, final List<? extends E> list2) {
        final ArrayList<E> result = new ArrayList<>(list1.size() + list2.size());
        result.addAll(list1);
        result.addAll(list2);
        return result;
    }

	public static String jsFixThis(String js, Element thisElement) {
		return js.replaceAll("this", String.format("document.querySelector(\"%s\")", thisElement.cssSelector()));
	}

	/*
	 * Based on Element::cssSelector()
	 * https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/Element.java
	 */
	public static String selectorFromTo(Element from, Element to) {
		if (!to.parents().contains(from))
			throw new IllegalArgumentException("First element is not parent of the second!");

		if (to.id().length() > 0)
            return "#" + to.id();

        String tagName = to.tagName().replace(':', '|');
        StringBuilder selector = new StringBuilder(tagName);
        String classes = StringUtil.join(to.classNames(), ".");
        if (classes.length() > 0)
            selector.append('.').append(classes);

        if (to.parent().equals(from)) // don't add Document to selector, as will always have a html node
            return selector.toString();

        selector.insert(0, " > ");
        if (to.parent().select(selector.toString()).size() > 1)
            selector.append(String.format(
                ":nth-child(%d)", to.elementSiblingIndex() + 1));

        return selectorFromTo(from, to.parent()) + selector.toString();
	}

	public static String pretty(Duration duration) {
		String result = "";
		final long hours = duration.toHours();
		if (hours > 0)
			result += hours + "h ";
		duration = duration.minusHours(hours);

		final long minutes = duration.toMinutes();
		if (minutes > 0)
			result += minutes + "m ";
		duration = duration.minusMinutes(minutes);

		final float seconds = duration.getSeconds() + (float) duration.getNano() / 1000000000;
		result += String.format("%.2f", seconds) + "s";

		return result;
	}

	public static <K,V> Stream<Pair<K,V>> zip(List<K> keys, List<V> values) {
		if (keys.size() != values.size())
			throw new IllegalArgumentException("keys size: " + keys.size() + " != values size: " + values.size());
		return IntStream.range(0, keys.size()).boxed().map(i -> Pair.create(keys.get(i), values.get(i)));
	}

	public static String isToString(InputStream is) throws IOException {
		return isToString(is, StandardCharsets.UTF_8.name());
	}

	public static String isToString(InputStream is, String charSet) throws IOException {
		final int BUFF_SIZE = 1024;

		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[BUFF_SIZE];
		int length;

		while ((length = is.read(buffer)) != -1)
		    result.write(buffer, 0, length);

		return result.toString(charSet);
	}

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

	public static String firstLine(String message) {
		return message.split(System.lineSeparator())[0];
	}
}
