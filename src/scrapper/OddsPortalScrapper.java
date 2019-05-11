package scrapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.openqa.selenium.JavascriptException;
import org.openqa.selenium.NoSuchElementException;

import htmlProvider.RWDUtils;
import htmlProvider.SeleniumChromeProvider;
import model.Country;
import model.League;
import model.Match;
import model.MatchData;
import model.MatchData.OddKey;
import model.Notifiable;
import model.ScrapException;
import model.Sport;
import model.WebData;
import model.WebSection;
import util.StringDate;
import util.Utils;

public class OddsPortalScrapper implements AutoCloseable {
	
	private static final String BASE_URL = "https://www.oddsportal.com";
	private static final String ENTRY_URL = BASE_URL + "/events/";
	private static final String SPORT_URL_FORMAT = "https://www.oddsportal.com/events/#sport/%s/all";
	
	private static final Pattern sportsOnClickToURL_regex = Pattern.compile("tab_sport_main.select\\( '(.*)'\\);this.blur\\(\\);return false;");	
			
	private SeleniumChromeProvider htmlProvider = new SeleniumChromeProvider(true);
	private List<ParserListener> listeners = new ArrayList<>();
	
	public void registerListener(ParserListener l) {
		listeners.add(l);
	}
	
	public void unregisterListener(ParserListener l) {
		listeners.remove(l);
	}

	public void clearListeners() {
		listeners.clear();
	}

	private boolean fireEventCheckStop(RequestStatus status, Notifiable obj, ParserListener... moreListeners) {
		boolean keepGoing = true;
	
		for (ParserListener listener : Utils.union(listeners, Arrays.asList(moreListeners)))
			keepGoing &= obj.notify(status, listener);

		return !keepGoing;
	}
	
	public RequestStatus findSports(ParserListener... moreListeners) {
		final RequestStatus status = new RequestStatus();
		final WebData webData = htmlProvider.get(ENTRY_URL);
		final Document startPage = webData.getDoc();
		Elements tabs = startPage.select("div#tabdiv_sport_main li.tab");
		
		for (Element tab : tabs) {	
			String onClickAttr = tab.selectFirst("a").attr("onclick");
			Matcher m = sportsOnClickToURL_regex.matcher(onClickAttr);
			if (m.find()) {
				String sportName = m.group(1);
				Sport sport = new Sport(sportName);
				
				if (fireEventCheckStop(status, sport, moreListeners))
					return status;
			} else {
				logError(status, new ScrapException("Parsing a sport tab 'onclick' attribute, the regex did not match: " + onClickAttr, webData, tab));
			}
		}
		
		return status;
	}

	public RequestStatus parse(Sport sport, ParserListener... moreListeners) {
		final RequestStatus status = new RequestStatus();
		/* As a workaround we need to load a different page (e.g. google) first */
		final WebData webData = htmlProvider.handle((unused) -> {
			htmlProvider.get("about:blank");
			return htmlProvider.get(String.format(SPORT_URL_FORMAT, sport.name));
		});

		final Document doc = webData.getDoc();
		
		Elements rows = doc.select("table[style=\"display: table;\"] tbody > tr");
		if (rows.isEmpty()) {
			logError(status, new ScrapException("Sport " + sport +  " contained no rows", webData));
			return status;
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
					logError(status, new ScrapException("Center-row did not contain a 'bfl' child to get name", webData, row));
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
					logError(status, new ScrapException("League with empty link", webData, tdElement));
					continue;
				}
				
				League l = new League(sport, country, leagueName, relativeUrl);

				if (fireEventCheckStop(status, l, moreListeners))
					return status;
			}
		}
		
		return status;
	}
	
	public RequestStatus parse(League league, ParserListener... moreListeners) {
		final RequestStatus status = new RequestStatus();
		final WebData webData = htmlProvider.get(BASE_URL + league.relUrl);
		final Document doc = webData.getDoc();
		Elements rows = doc.select("table[style] tbody > tr:not(.center):not(.table-dummyrow)");
		if (rows.isEmpty()) {
			/* 
			 * If there's an info box informing about no odds, then we know for
			 * sure that there were no parse errors, it is just that there are
			 * no available matches but the League is there for some reason...
			 * Otherwise, log an error, just in case it was a parse problem.
			 */
			Element infoMessage = doc.selectFirst("div.message-info div.cms");
			final String NO_MATCHES_AVAILABLE_MSG = "will appear here as soon as bookmaker betting odds become available."; 
			if (!infoMessage.text().contains(NO_MATCHES_AVAILABLE_MSG))
				logError(status, new ScrapException(league +  " contained no rows", webData));

			return status;
		}
		
		for (Element row : rows) {
			Element matchElement = null;
			Elements possibleMatchElement = row.select("td.name > a");
			for (Element e : possibleMatchElement) {
				final String text = e.text();
				/* Take first child with text inside */
				if (text != null && !text.trim().isEmpty()) {
					matchElement = e;
					break;
				}
			}
			if (matchElement == null) {
				logError(status, new ScrapException("Could not locate match name, skipping", webData, row));
				continue;
			}
			
			final String matchName = matchElement.text();
			final String matchUrl = matchElement.attr("href");
			final String webId = row.attr("xeid");
			final boolean isLive = row.selectFirst("span.live-odds-ico-prev") != null;
			Match match = new Match(league, matchName, BASE_URL + matchUrl, isLive, webId);
			
			if (fireEventCheckStop(status, match, moreListeners))
				return status;
		}
		
		return status;
	}

	private static Map<StringDate, Double> parseOddHistory(Element element) {
		Map<StringDate, Double> map = new LinkedHashMap<>();
		if (element == null)
			return map;

		Element textElement = element.selectFirst("#tooltiptext");
		String previous = null;
		for (Node node : textElement.childNodes()) {
			String text;
			if (node instanceof TextNode)
				text = ((TextNode) node).text().trim();
			else if (node instanceof Element)
				text = ((Element) node).text().trim();
			else 
				continue;
			if (text.isEmpty())
				continue;
			if (Utils.tryParseDouble(text) != null && (text.startsWith("+") || text.startsWith("-")))
				continue;
			
			Double odd = Utils.tryParseDouble(text);
			if (odd != null && previous != null)
				map.put(new StringDate(previous), odd);
			
			previous = text;
		}
		
		return map;
	}

	public RequestStatus parse(Match m, ParserListener... moreListeners) {
		final RequestStatus status = new RequestStatus();
		MatchData data = htmlProvider.handle((driver) -> {
			RWDUtils utils = new RWDUtils(driver);
			WebData webData = htmlProvider.get(m.url);
			Document doc = webData.getDoc();
			Element dateElement = doc.selectFirst("p.date");
			if (dateElement == null) {
				logError(status, new ScrapException("Could not find match time! " + m, webData));
				return null;
			}

			final Pattern dateClassPattern = Pattern.compile("t([0-9]+)-");
			Matcher matcher = dateClassPattern.matcher(dateElement.attr("class"));
			if (!matcher.find() || matcher.groupCount() > 1) {
				logError(status, new ScrapException("Could not parse time: " + dateElement.attr("class") + "," + m, webData, doc));
				return null;
			}
			long dateTimestamp;
			try {
				dateTimestamp = Long.parseLong(matcher.group(1));
			} catch (NumberFormatException e) {
				logError(status, new ScrapException("Could not parse time: " + dateElement.attr("class") + "," + m, webData, doc, e));
				return null;
			}
			final MatchData matchData = new MatchData(m, dateTimestamp);
			
			Elements tabs = webData.getDoc().select("div#bettype-tabs li a");
			/* The tab that is selected by default is the current element and does not have
			 * a <a> element, so manually append it. */
			Element activeTab = webData.getDoc().selectFirst("div#bettype-tabs li.active strong span");
			if (activeTab == null) {
				if (tabs.size() != 0)
					logError(status, new ScrapException("tabs != 0 but no active tab", webData));
			}
			
			String tabTitle = activeTab.text();
			tabs.add(0, activeTab);
			boolean firstTab = true;
			for (Element tab : tabs) {
				// Skip "More bets" tab
				if (tab.parent().attr("class").contains("more"))
					continue;

				if (!firstTab) {
					tabTitle = tab.attr("title");
					if (tabTitle.isEmpty())
						tabTitle = tab.text();

					String jsCode = tab.attr("onmousedown");
					driver.executeScript(jsCode);
					utils.waitLoadSpinner();
					webData = htmlProvider.get();
				} else {
					firstTab = false;
				}
				
				Elements subtabs = webData.getDoc().select("div#bettype-tabs-scope ul[style=\"display: block;\"] li a");
				Element activeSubtab = webData.getDoc().selectFirst("div#bettype-tabs-scope ul[style=\"display: block;\"] li.active strong span");
				if (activeSubtab == null)
					continue;
				String subtabTitle = activeSubtab.text();
				subtabs.add(0, activeSubtab);
				boolean firstSubTab = true;
				for (Element subtab : subtabs) {
					if (!firstSubTab) {
						subtabTitle = subtab.attr("title");
						String jsCode = subtab.attr("onmousedown");
						driver.executeScript(jsCode);
						utils.waitLoadSpinner();
						webData = htmlProvider.get();
					} else {
						firstSubTab = false;
					}
					
					/* 
					 * Expand all bet groups by 'clicking' on them. Sometimes the rows are
					 * already expanded, so expand only if they don' contain data to be
					 * parsed (table.table-main)
					 */
					int rowsToBeExpanded = 0;
					int rowsExpanded = 0;
					for (Element rowToBeExpanded : webData.getDoc().select("#odds-data-table > div.table-container")) {
						rowsToBeExpanded++;
						final Element jsCodeContainer = rowToBeExpanded.selectFirst("div > strong > a");
						final boolean needsToBeExpanded = rowToBeExpanded.selectFirst("table.table-main") == null;
						if (needsToBeExpanded && jsCodeContainer != null) {
							rowsExpanded++;
							driver.executeScript(jsCodeContainer.attr("onclick"));
						}
					}

					final WebSection section = new WebSection(tabTitle, subtabTitle);
					webData = htmlProvider.get();
					doc = webData.getDoc();

					final Elements oddTables = doc.select("div#odds-data-table div.table-container:not(.exchangeContainer)");
					if (oddTables.isEmpty()) {
						final String errorMsg = String.format("Could not locate any oddTable tbe: %d, e: %d. %s, %s", 
														 rowsToBeExpanded, rowsExpanded, section, m);
						logError(status, new ScrapException(errorMsg, webData, doc));
						return null;
					}
					
					for (Element oddTable : oddTables) {
						String oddTableTitle = null;
						if (oddTables.size() > 1) {
							oddTableTitle = oddTable.selectFirst("strong").text();
						}
						
						Element headerRow = oddTable.selectFirst("thead > tr");
						if (headerRow == null) {
							logError(status, new ScrapException("No header row while parsing " + section + "in " + m, webData, doc));
							continue;
						}
						
						List<String> columns = headerRow.select("tr a").stream().map(e -> e.text()).collect(Collectors.toList());
						
						/* Check first and last columns are as expected, and remove them */
						String firstColumn = columns.get(0);
						String lastColumn = columns.get(columns.size() - 1);
						if (!firstColumn.trim().equals("Bookmakers")) {
							logError(status, new ScrapException("Strange first column: " + columns.get(0), webData, headerRow));
							continue;
						} else {
							columns.remove(firstColumn);
						}
						if (lastColumn.trim().equals("Payout"))
							columns.remove(lastColumn);

						if (columns.isEmpty())
							logError(status, new ScrapException("There are no columns!", webData, oddTable));
						
						Elements rows = oddTable.select("table > tbody > tr");
						if (rows.isEmpty())
							logError(status, new ScrapException("There are no rows!", webData, oddTable));

						
						for (Element row : rows) {
							String rowSelector = row.cssSelector();
							Elements oddElements = row.select("td.odds");
							
							if (oddElements.isEmpty())
								continue;
							
							if (oddElements.size() != columns.size()) {
								logError(status, new ScrapException("Strange number of odds, expected: " + columns + " only got: " + oddElements.size(), webData, row));
								continue;
							}

							/* For now assume first column to be the bethouse */
							Element bethouseElement = row.child(0);
							String betHouse = Utils.combineAllText(bethouseElement.select("a"));

							for (int i = 0; i < oddElements.size(); i++) {
								final String column = columns.get(i);
								final OddKey key = new OddKey(section, oddTableTitle, betHouse, column);
								
								Element oddElement = oddElements.get(i);
								String oddSelector = String.format("%s > td.odds:nth-child(%d)", rowSelector, oddElement.elementSiblingIndex() + 1);
								
								Element overElement = oddElement.selectFirst("[onmouseover]");
								oddSelector += " > " + Utils.selectorFromTo(oddElement, overElement);
								
								Map<StringDate, Double> oddsForKey = null;
								Element fragment = null;
								try {
									driver.executeScript(
											String.format("document.querySelector(\"%s\")", oddSelector) +
											".dispatchEvent(new Event('mouseover'))"
									);
									fragment = Jsoup.parse(driver.findElementById("tooltipdiv").getAttribute("outerHTML"));
									oddsForKey = parseOddHistory(fragment);
								} catch (NoSuchElementException | JavascriptException e) {
									logError(status, new ScrapException("Could not get odd history element", webData, doc, e));
									try {
										/* At least used the parsed odd with current timestamp */
										double odd = Utils.parseDoubleEmptyIsZero(oddElement.text());
										oddsForKey = Collections.singletonMap(new StringDate(System.currentTimeMillis()), odd);
									} catch (NumberFormatException e2) {
										logError(status, new ScrapException(m + ", " + section + " strange odd cell does not have an odd", webData, row, e2));
									}
								}

								boolean emptyOddCell = oddElement.text().trim().isEmpty();
								if (oddsForKey == null || (oddsForKey.isEmpty() && !emptyOddCell))
									logError(status, new ScrapException("Empty oddKey: " + key + ", tbe: " + rowsToBeExpanded + ", e: " + rowsExpanded, webData, fragment));
								else if (oddsForKey.isEmpty() && emptyOddCell)
									; /* Empty cell, just skip it */
								else 
									matchData.addOdds(key, oddsForKey);
							}
						}
					}
				}
			}

			return matchData;
		});
		
		if (data == null || data.getOdds().isEmpty())
			logError(status, new ScrapException("Empty matchData D: " + data));
		else
			fireEventCheckStop(status, data, moreListeners);
		
		return status;
	}
	
	@Override
	public void close() throws Exception {
		htmlProvider.close();
	}
	
	private void logError(RequestStatus status, ScrapException e) {
		status.addError(e);
		for (ParserListener listener : listeners)
			listener.onError(e);
	}
}