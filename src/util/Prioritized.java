package util;

public interface Prioritized extends Comparable<Prioritized>{
	
	public enum Priority {
		HIGH,
		MEDIUM,
		DEFAULT,
		LOW
	}
	
	Priority getPriority();
	
	default int compareTo(Prioritized other) {
		return getPriority().compareTo(other.getPriority());
	}
}
