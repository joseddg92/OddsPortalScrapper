package main;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

public class OddsPortalScrapper implements AutoCloseable {
	
	private static final String BASE_URL = "https://www.oddsportal.com";
	private static final String ENTRY_URL = BASE_URL + "/events/";
	private static final String SPORT_URL_FORMAT = "https://www.oddsportal.com/events/#sport/%s/all";
	
	private static final Pattern sportsOnClickToURL_regex = Pattern.compile("tab_sport_main.select\\( '(.*)'\\);this.blur\\(\\);return false;");	
			
	private SeleniumChromeProvider htmlProvider = new SeleniumChromeProvider();
	private List<ScrapException> errors = new ArrayList<>();
	
	private void logError(ScrapException e) {
		StackTraceElement errorLine = e.getStackTrace()[0];
		
		System.err.println("Non-critical error: " + errorLine.getMethodName() + ":" +  errorLine.getLineNumber() + "- >  " + e.getMessage());
		errors.add(e);
	}
	
	private List<Sport> getSports() throws ScrapException {
		Document startPage = htmlProvider.get(ENTRY_URL);
		Elements tabs = startPage.select("div#tabdiv_sport_main li.tab");
		List<Sport> sports = new ArrayList<>(tabs.size());
		
		for (Element tab : tabs) {	
			String onClickAttr = tab.selectFirst("a").attr("onclick");
			Matcher m = sportsOnClickToURL_regex.matcher(onClickAttr);
			if (m.find()) {
				String sportName = m.group(1);
				sports.add(new Sport(sportName));
			} else {
				logError(new ScrapException("Parsing a sport tab 'onclick' attribute, the regex did not match: " + onClickAttr, tab));
			}
		}
		
		return sports;
	}
	
	private List<League> parseSport(Sport sport) {
		List<League> leagues = new ArrayList<>();
		long startTime = System.currentTimeMillis();
		System.out.println("Parsing sport=" + sport + " ...");
		
		htmlProvider.get("http://www.google.es");
		Document doc = htmlProvider.get(String.format(SPORT_URL_FORMAT, sport));
		
		Elements rows = doc.select("tbody tr");
		if (rows.isEmpty()) {
			logError(new ScrapException("Sport " + sport +  " contained no rows"));
			return leagues;
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
				System.out.println(l);
	
				leagues.add(l);
			}
		}
		
		long estimatedTime = System.currentTimeMillis() - startTime;
		System.out.println("Sport " + sport + " parsed (" + leagues.size() + " leagues found) in " + estimatedTime / 1000.0 + " secs");
		
		return leagues;
	}
	
	private List<Match> parseLeague(League league) {
		List<Match> matches = new ArrayList<>();
		long startTime = System.currentTimeMillis();
		System.out.println("Parsing league=" + league + " ...");
		
		Document doc = htmlProvider.get(BASE_URL + league.relUrl);
		
		Elements rows = doc.select("tbody tr");
		if (rows.isEmpty()) {
			logError(new ScrapException("League " + league +  " contained no rows"));
			return matches;
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
			Match match = new Match(league, matchName, matchUrl);
			matches.add(match);

			System.out.println("Adding match: " + match);
		}
		
		long estimatedTime = System.currentTimeMillis() - startTime;
		System.out.println("League: " + league + " parsed (" + matches.size() + " matches found) in " + estimatedTime / 1000.0 + " secs");
		
		
		return matches;
	}

	public void run() throws Exception {
		List<Match> matches = new ArrayList<>();
		long startTime = System.currentTimeMillis();
		
		List<Sport> sports = getSports();
		
		long totalQueries = 0;
		for (Sport sport : sports)
			totalQueries += parseSport(sport).size();
		
		long currentQuery = 0;
		for (Sport sport : sports) {
			List<League> leagues = parseSport(sport);
			for (League league : leagues) {
				matches.addAll(parseLeague(league));
				System.err.println(String.format("Progress: %d/%d ", currentQuery, totalQueries) + " (" + (double) ++currentQuery / ((double) currentQuery) + "%)");
			}
		}
		
		long elapsedTimeSecs = (System.currentTimeMillis() - startTime) / 1000;
		double matchesParsedPerSecond = matches.size() / (double) elapsedTimeSecs;
		System.out.println(matches.size() + " matches found in " + matchesParsedPerSecond + " seconds (" + matchesParsedPerSecond + " matches/s)");
	}
	
	@Override
	public void close() throws Exception {
		htmlProvider.close();
	}
	
	public List<ScrapException> getErrors() {
		return Collections.unmodifiableList(errors);
	}
}