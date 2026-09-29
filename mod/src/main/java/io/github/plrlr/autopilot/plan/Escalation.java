package io.github.plrlr.autopilot.plan;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Running didn't work: the reflex's memory of recent escapes (gene reflex.escalate).
 *
 * In generations 41-46, 101 of 281 deaths came right after a retreat, and a mob near the furnace
 * made the bot flip smelt / retreat / smelt every 4-6 s for over 100 s. A player who has run from
 * the same trouble twice in half a minute stops running: fights it, walls in, or pillars up.
 * Planner.escape asks ranTwice() and, when it's true, offers standing ground instead of a third run.
 *
 * Times are game ticks (the world's game time), so it works the same at any game speed.
 */
public final class Escalation {
	private Escalation() {}

	/** How far back a retreat still counts: 30 s. */
	static final long WINDOW_TICKS = 20 * 30;

	private static final Deque<Long> RETREATS = new ArrayDeque<>();
	private static long now;

	/** A retreat started at this game tick. */
	public static synchronized void fled(long tick) {
		now = Math.max(now, tick);
		RETREATS.addLast(tick);
		trim();
	}

	/** The clock moves on (called every tick with the world's game time). */
	public static synchronized void tick(long tick) {
		now = Math.max(now, tick);
	}

	/** Two or more retreats in the last 30 s: running isn't working here. */
	public static synchronized boolean ranTwice() {
		trim();
		return RETREATS.size() >= 2;
	}

	/** After a death or in a new world: the slate is clean. */
	public static synchronized void clear() {
		RETREATS.clear();
		now = 0;
	}

	private static void trim() {
		while (!RETREATS.isEmpty() && RETREATS.peekFirst() < now - WINDOW_TICKS) RETREATS.removeFirst();
	}
}
