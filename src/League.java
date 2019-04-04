
public class League {
	
	public final Sport sport;
	public final Country country;
	public final String name;
	public final String relUrl;
	
	public League(Sport sport, Country country, String name, String url) {
		this.sport = sport;
		this.country = country;
		this.name = name;
		this.relUrl = url;
	}

	public String toString() {
		return sport.name + " -> " + country.name + " -> " + name + " [" + relUrl + "]";
	}
	
}
