
public class Match {

	public final League league;
	public final String name;
	public final String url;
	
	public Match(League league, String name, String url) {
		this.league = league;
		this.name = name;
		this.url = url;
	}

	public String toString() {
		return league + "/" + name;
	}
}
