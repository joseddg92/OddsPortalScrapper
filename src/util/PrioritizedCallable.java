package util;

import java.util.concurrent.Callable;

public class PrioritizedCallable<T> implements Callable<T>, Prioritized {

	private final Callable<T> callable;
	private final Priority p;
	
	public PrioritizedCallable(Callable<T> callable, Priority p) {
		this.callable = callable;
		this.p = p;
	}
	
	@Override
	public Priority getPriority() {
		return p;
	}

	@Override
	public T call() throws Exception {
		return callable.call();
	}

}
