package scrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import model.ScrapException;

public class RequestStatus {

	private List<ScrapException> errors = new ArrayList<>(0);
	
	RequestStatus() {
		
	}
	
	void addError(ScrapException e) {
		errors.add(e);
	}
	
	public List<ScrapException> getErrors() {
		return Collections.unmodifiableList(errors);
	}
	
	public boolean ok() {
		return errors.isEmpty();
	}
}
