CREATE TABLE IF NOT EXISTS sport (
	sportId INTEGER PRIMARY KEY autoincrement,
	name VARCHAR NOT NULL
);

CREATE TABLE IF NOT EXISTS country (
	countryId INTEGER PRIMARY KEY autoincrement,
	name VARCHAR NOT NULL
);

CREATE TABLE IF NOT EXISTS league (
	leagueId INTEGER PRIMARY KEY autoincrement,
	sportId INT NOT NULL,
	countryId INT NOT NULL,
	name VARCHAR NOT NULL,
	FOREIGN KEY (sportId) REFERENCES sport (sportId),
	FOREIGN KEY (countryId) REFERENCES country (countryId)
);

CREATE TABLE IF NOT EXISTS match (
	matchId INTEGER PRIMARY KEY autoincrement,
	oddSportsKey VARCHAR NOT NULL,
	leagueId INT NOT NULL,
	name DATETIME NOT NULL,
	localTeam VARCHAR,
	visitorTeam VARCHAR,
	beginDatetime DATETIME NOT NULL,
	FOREIGN KEY (leagueId) REFERENCES league (leagueId)
);

CREATE TABLE IF NOT EXISTS bethouse (
	bethouseId INTEGER PRIMARY KEY autoincrement,
	name VARCHAR NOT NULL
);

CREATE TABLE IF NOT EXISTS odd_category (
	catId INTEGER PRIMARY KEY autoincrement,
	cat1 VARCHAR NOT NULL,
	cat2 VARCHAR NOT NULL,
	cat3 VARCHAR
);

CREATE TABLE IF NOT EXISTS odd (
    INT INTEGER PRIMARY KEY autoincrement,
	matchId INT NOT NULL,
	catId INT NOT NULL,
	bethouseId INT NOT NULL,
	time DATETIME,
	timeStr VARCHAR,
	value FLOAT NOT NULL,
	FOREIGN KEY (matchId) REFERENCES league (match),
	FOREIGN KEY (catId) REFERENCES odd_category (catId),
	FOREIGN KEY (bethouseId) REFERENCES bethouse (bethouseId)
);