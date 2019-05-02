CREATE TABLE IF NOT EXISTS sport (
	sport_id integer PRIMARY KEY,
	name varchar NOT NULL
);

CREATE TABLE IF NOT EXISTS country (
	country_id integer PRIMARY KEY,
	name varchar NOT NULL
);

CREATE TABLE IF NOT EXISTS bethouse (
	bethouse_id integer PRIMARY KEY,
	name varchar NOT NULL
);

CREATE TABLE IF NOT EXISTS league (
	league_id integer PRIMARY KEY,
	sport_id integer,
	country_id integer,
	name integer NOT NULL,
	FOREIGN KEY (sport_id) REFERENCES sport (sport_id),
	FOREIGN KEY (country_id) REFERENCES country (country_id)
);

CREATE TABLE IF NOT EXISTS match (
	match_id integer PRIMARY KEY,
	league_id integer,	
	web_key varchar NOT NULL,
	name datetime NOT NULL,
	local_team varchar,
	visitor_team varchar,
	begin_time datetime NOT NULL,
	FOREIGN KEY (league_id) REFERENCES league (league_id)
);

CREATE TABLE IF NOT EXISTS odd_cat (
	cat_id integer PRIMARY KEY,
	bethouse_id integer,
	cat1 varchar NOT NULL,
	cat2 varchar NOT NULL,
	cat3 varchar,
	FOREIGN KEY (bethouse_id) REFERENCES bethouse (bethouse_id)
);

CREATE TABLE IF NOT EXISTS odd (
	odd_id integer PRIMARY KEY,
	match_id integer,
	cat_id integer,
	time datetime NOT NULL,
	value float NOT NULL,
	FOREIGN KEY (match_id) REFERENCES match (match_id),
	FOREIGN KEY (cat_id) REFERENCES odd_cat (cat_id)
);

CREATE TABLE IF NOT EXISTS odd_baddate (
	odd_id integer PRIMARY KEY,
	time_str varchar NOT NULL,
	FOREIGN KEY (odd_id) REFERENCES odd (odd_id)
);
