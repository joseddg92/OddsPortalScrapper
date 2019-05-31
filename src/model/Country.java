package model;

import java.io.Serializable;

public class Country implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String name;

	public Country(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "Country[" + name + "]";
	}
}
