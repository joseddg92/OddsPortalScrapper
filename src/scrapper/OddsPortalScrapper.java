package scrapper;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.NoSuchElementException;

import htmlProvider.SeleniumChromeProvider;
import model.Country;
import model.League;
import model.Match;
import model.ScrapException;
import model.Sport;
import model.WebSection;

public class OddsPortalScrapper implements AutoCloseable {
	
	private static final String BASE_URL = "https://www.oddsportal.com";
	private static final String ENTRY_URL = BASE_URL + "/events/";
	private static final String SPORT_URL_FORMAT = "https://www.oddsportal.com/events/#sport/%s/all";
	
	private static final Pattern sportsOnClickToURL_regex = Pattern.compile("tab_sport_main.select\\( '(.*)'\\);this.blur\\(\\);return false;");	
			
	private SeleniumChromeProvider htmlProvider = new SeleniumChromeProvider();
	private List<ScrapException> errors = new ArrayList<>();
	private List<WeakReference<ParserListener>> listeners = new ArrayList<>();
	
	public void registerListener(ParserListener l) {
		listeners.add(new WeakReference<>(l));
	}
	
	public void unregisterListener(ParserListener l) {
		for (WeakReference<ParserListener> w : listeners) {
			if (w.get() != null && w.get().equals(l)) {
				listeners.remove(w);
				return;
			}
		}
	}
	
	private boolean fireEventCheckStop(Sport obj) {
		return this.<Sport>_internal_fireEventCheckStop(obj);
	}
	
	private boolean fireEventCheckStop(League obj) {
		return this.<League>_internal_fireEventCheckStop(obj);
	}
	
	private boolean fireEventCheckStop(Match obj) {
		return this.<Match>_internal_fireEventCheckStop(obj);
	}
	
	private <T> boolean _internal_fireEventCheckStop(T obj) {
		boolean keepGoing = true;
	
		Iterator<WeakReference<ParserListener>> it = listeners.iterator();
		while (it.hasNext()) {
			ParserListener listener = it.next().get();
			if (listener == null) {
				it.remove();
				continue;
			}

			if (obj instanceof Sport)
				keepGoing &= listener.onElementParsed((Sport) obj);
			else if (obj instanceof League)
				keepGoing &= listener.onElementParsed((League) obj);
			else if (obj instanceof Match)
				keepGoing &= listener.onElementParsed((Match) obj);
			else 
				assert false;
		}
		return !keepGoing;
	}
	
	public void findSports() throws ScrapException {
		Document startPage = htmlProvider.get(ENTRY_URL);
		Elements tabs = startPage.select("div#tabdiv_sport_main li.tab");
		
		for (Element tab : tabs) {	
			String onClickAttr = tab.selectFirst("a").attr("onclick");
			Matcher m = sportsOnClickToURL_regex.matcher(onClickAttr);
			if (m.find()) {
				String sportName = m.group(1);
				Sport sport = new Sport(sportName);
				
				if (fireEventCheckStop(sport))
					return;
			} else {
				logError(new ScrapException("Parsing a sport tab 'onclick' attribute, the regex did not match: " + onClickAttr, tab));
			}
		}
	}
	
	public void parse(Sport sport) {
		htmlProvider.get("http://www.google.es");
		Document doc = htmlProvider.get(String.format(SPORT_URL_FORMAT, sport.name));
		
		Elements rows = doc.select("table[style] tbody > tr");
		if (rows.isEmpty()) {
			logError(new ScrapException("Sport " + sport +  " contained no rows"));
			return;
		}
		
		String countryName = "";
		for (Element row : rows) {
			if (row.attr("class").contains("center") && !row.attr("class").contains("dark")) {
				
				/* Skip 'popular' category */
				String xcid = row.attr("xcid");
				if (xcid != null && xcid.contains("popular"))
					continue;
				try {
					countryName = row.select("a.bfl").text();
				} catch (NoSuchElementException e) {
					logError(new ScrapException("Center-row did not contain a 'bfl' child to get name", row));
				}
			}
			
			/* Skip orphan subcategories (which should only be "popular" subcategories) */
			if (countryName.isEmpty())
				continue;
			
			Elements tdElements = row.select("td");
			final Country country = new Country(countryName);
			for (Element tdElement : tdElements) {
				Element linkElement = tdElement.selectFirst("a");
				/* Skip empty elements */
				if (linkElement == null)
					continue;
				
				String leagueName = linkElement.text();
				String relativeUrl = linkElement.attr("href");
				
				if (leagueName.trim().isEmpty())
					continue;
				
				if (relativeUrl.trim().isEmpty()) {
					logError(new ScrapException("League with empty link", tdElement));
					continue;
				}
				
				League l = new League(sport, country, leagueName, relativeUrl);

				if (fireEventCheckStop(l))
					return;
			}
		}
	}
	
	public void parse(League league) {
		Document doc = htmlProvider.get(BASE_URL + league.relUrl);
		
		Elements rows = doc.select("table[style] tbody > tr");
		if (rows.isEmpty()) {
			logError(new ScrapException("League " + league +  " contained no rows"));
			return;
		}
		
		final Collection<String> rowsClassesToSkip = Arrays.asList("center", "table-dummyrow");
		for (Element row : rows) {
			List<String> rowClasses = Arrays.asList(row.attr("class").split(" "));
			if (!Collections.disjoint(rowsClassesToSkip, rowClasses))
				continue;
		
			Element matchElement = null;
			Elements possibleMatchElement = row.select("td.name a");
			for (Element e : possibleMatchElement) {
				if (e.id().isEmpty()) {
					matchElement = e;
					break;
				}
			}
			if (matchElement == null)
				continue;
			
			String matchName = matchElement.text();
			String matchUrl = matchElement.attr("href");
			Match match = new Match(league, matchName, BASE_URL + matchUrl);
			
			if (fireEventCheckStop(match))
				return;
		}
	}

	public void parse(Match m) {
		Map<WebSection, Document> tabs = htmlProvider.getAllTabs(m.url);
		
		for (Entry<WebSection, Document> sectionEntry : tabs.entrySet()) {
			final WebSection section = sectionEntry.getKey();
			final Document doc = sectionEntry.getValue();
			
			System.out.println("Parsing " + section + " ...");
			
			Elements oddTables = doc.select("div#odds-data-table div.table-container");
			if (oddTables.isEmpty()) {
				logError(new ScrapException("Could not locate any oddTable!", doc));
				return;
			}
			
			/* Do not parse exchanges for now */
			oddTables.removeIf(oddTable -> oddTable.attr("class").contains("exchangeContainer"));

			for (Element oddTable : oddTables) {
				String oddTableTitle;
				if (oddTables.size() == 1) {
					oddTableTitle = "";
				} else {
					oddTableTitle = oddTable.selectFirst("strong").text();
				}

				Element headerRow = oddTable.selectFirst("thead > tr");
				if (headerRow == null) {
					logError(new ScrapException("No header row while parsing " + section + "in " + m, doc));
					return;
				}
				
				Elements columns = headerRow.select("tr a");
				List<String> columnTitles = new ArrayList<>(columns.size());
				for (Element column : columns) {
					columnTitles.add(column.text());
				}
				System.out.println("\t" + section + " --> " + columnTitles);
				
				Elements rows = oddTable.select("table > tbody > tr");

				int i=0;
				for (Element row : rows) {
					i++;
					List<Double> odds = new ArrayList<>();
					Elements oddElements = row.select("td.odds");
					
					if (oddElements.isEmpty())
						continue;
					
					/* For now assume first column to be the bethouse */
					Element bethouseElement = row.child(0);
					String betHouse = combineAllText(bethouseElement.select("a"));

					for (Element oddElement : oddElements) {
						double odd = 0;
						try {
							odd = parseDoubleEmptyIsZero(combineAllText(oddElement.children()));
						} catch (NumberFormatException e) {
							logError(new ScrapException(m + ", " + section + " strange odd cell does not have an odd", row, e));
						} finally {
							/* Even if parsing fails, we need to add it to keep the rest of indexes */
							odds.add(odd);
						}
					}
					System.out.println("\t" + "\t" + "Section "  + oddTableTitle + ", Row " + i + ", bethouse: " + betHouse + ", odds: " + odds);
				}
			}
		}
	}
	
	@Override
	public void close() throws Exception {
		htmlProvider.close();
	}
	
	public List<ScrapException> getErrors() {
		return Collections.unmodifiableList(errors);
	}
	
	private void logError(ScrapException e) {
		StackTraceElement errorLine = e.getStackTrace()[0];
		
		System.err.println("Non-critical error: (" + errorLine.getMethodName() + ":" +  errorLine.getLineNumber() + ")>  " + e.getMessage());
		errors.add(e);
	}
	
	private static double parseDoubleEmptyIsZero(String s) throws NumberFormatException {
		if (s.trim().isEmpty())
			return 0d;
		return Double.parseDouble(s);
	}
	
	private static String combineAllText(Collection<Element> elements) {
		String s = "";
		for (Element e : elements) 
			s += e.text();
		
		return s;
	}
}