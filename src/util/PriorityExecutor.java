package util;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import util.Prioritized.Priority;

public class PriorityExecutor extends ThreadPoolExecutor {
	
	public PriorityExecutor(int nThreads) {
		this(nThreads, Executors.defaultThreadFactory());
	}
	
	public PriorityExecutor(int nThreads, ThreadFactory builder) {
		super(
			nThreads,
			nThreads,
			1,
			TimeUnit.MINUTES, 
			new PriorityBlockingQueue<Runnable>(
					2 * nThreads,
					new Comparator<Runnable>() {
						public int compare(Runnable o1, Runnable o2) {
							if (o1 instanceof Prioritized && o2 instanceof Prioritized)
								return ((Prioritized) o1).compareTo(((Prioritized) o2));
							else 
								return o1.hashCode() - o2.hashCode();
						}
					}),
			builder
		);
	}

	public <T> Future<?> submitWithPriority(Priority p, Callable<T> callable) {
		return submit(new PrioritizedCallable<T>(callable, p));
	}
	
	public Future<?> submitWithPriority(Priority p, Runnable runnable) {
		return submit(new PrioritizedCallable<Object>(new Callable<Object>() {
			public Object call() throws Exception {
				runnable.run();
				return null;
			}
		}, p));
	}
	
	protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        RunnableFuture<T> newTaskFor = super.newTaskFor(callable);

        Priority priority = (callable instanceof Prioritized)      ?
        			   		((Prioritized) callable).getPriority() :
        			   		Priority.DEFAULT;

        return new PrioritizedFuture<T>(newTaskFor, priority);
	}
}

class PrioritizedFuture<T> implements RunnableFuture<T>, Prioritized {

    private final RunnableFuture<T> runnable;
    private final Priority priority;

    public PrioritizedFuture(RunnableFuture<T> runnable, Priority priority) {
        this.runnable = Objects.requireNonNull(runnable);
        this.priority = Objects.requireNonNull(priority);
    }

    public Priority getPriority() {
        return priority;
    }
    
    public boolean cancel(boolean mayInterruptIfRunning) {
        return runnable.cancel(mayInterruptIfRunning);
    }

    public boolean isCancelled() {
        return runnable.isCancelled();
    }

    public boolean isDone() {
        return runnable.isDone();
    }

    public T get() throws InterruptedException, ExecutionException {
        return runnable.get();
    }

    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return runnable.get();
    }

    public void run() {
        runnable.run();
    }
}
