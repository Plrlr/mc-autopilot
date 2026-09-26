package io.github.plrlr.autopilot.brains;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared background threads for AI calls, so the game thread never waits on a network or process. */
public final class Brains {
	private Brains() {}

	private static final AtomicInteger N = new AtomicInteger();

	public static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "autopilot-brain-" + N.incrementAndGet());
		t.setDaemon(true);
		return t;
	});
}
