package main;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import util.PriorityExecutor;
import util.Prioritized.Priority;

public class TestPriorityExecutor {

	public static void main(String[] args) throws Exception {
		PriorityExecutor e = new PriorityExecutor(1);
		
		for (int i = 0; i < 100; i++) {
			final int j = i;
			final Priority p = Priority.values()[new Random().nextInt(Priority.values().length)];
			e.submitWithPriority(p, () -> { 
				System.out.println("task " + j + " with priority: " + p);
				try {
					Thread.sleep(5);
				} catch (Exception e2) {}
			});
		}
		e.shutdown();
		e.awaitTermination(1000, TimeUnit.DAYS);
	}
	
}
