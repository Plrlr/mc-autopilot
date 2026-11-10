package io.github.plrlr.autopilot.plan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 2026-11-09, gene plan.place_backoff: where an action keeps failing. SkillBook pauses a failed
 * option for 60 s keyed by the action alone, then forgets the count, so an unreachable target was
 * tried again every minute for the rest of the game. Here a failure is keyed by action + dimension
 * + chunk the bot stood in, and each repeat failure at that place doubles the pause (60, 120,
 * 240 s ... up to plan.backoff_max_s). A success there clears it; walking to another chunk gives
 * the action a fresh chance. Pure logic (ticks and ints in), so unit tests need no game.
 */
public final class PlaceMemory {
	static final long BASE_TICKS = 60 * 20;
	static final long EXPIRE_TICKS = 30 * 60 * 20; // 30 game minutes without news: forget it
	static final int CAP = 256;

	/** fails: repeat count; until: paused until this tick; seen: last failure tick. */
	private record Entry(int fails, long until, long seen) {}

	// Insertion order = age: the oldest entry is dropped first at the cap.
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	static String key(String action, String dim, int chunkX, int chunkZ) {
		return action + "@" + dim + ":" + chunkX + "," + chunkZ;
	}

	/** A failure of `action` with the bot in chunk (cx, cz). Returns the pause in ticks it now has. */
	public synchronized long failed(String action, String dim, int cx, int cz, long tick, long maxTicks) {
		String k = key(action, dim, cx, cz);
		Entry old = entries.remove(k);
		if (old != null && tick - old.seen() > EXPIRE_TICKS) old = null;
		int fails = old == null ? 1 : old.fails() + 1;
		long pause = Math.min(Math.max(BASE_TICKS, maxTicks), BASE_TICKS << Math.min(fails - 1, 20));
		entries.put(k, new Entry(fails, tick + pause, tick));
		while (entries.size() > CAP) entries.remove(entries.keySet().iterator().next());
		return pause;
	}

	/** It worked here: no pause and no count left for this place. */
	public synchronized void succeeded(String action, String dim, int cx, int cz) {
		entries.remove(key(action, dim, cx, cz));
	}

	/** Whether `action` is still paused at this place. */
	public synchronized boolean paused(String action, String dim, int cx, int cz, long tick) {
		Entry e = entries.get(key(action, dim, cx, cz));
		return e != null && tick < e.until() && tick - e.seen() <= EXPIRE_TICKS;
	}

	public synchronized void clear() {
		entries.clear();
	}

	synchronized int size() {
		return entries.size();
	}
}
