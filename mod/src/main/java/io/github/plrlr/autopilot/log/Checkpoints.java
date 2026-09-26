package io.github.plrlr.autopilot.log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finer steps between milestones, so a batch shows how far the portal path got even when no
 * run reached the Nether: two buckets, flint and steel, lava seen, obsidian placed, frame
 * complete, portal lit. Each is recorded once per session with the time it was first reached.
 * Skills mark the ones only they know about (obsidian placed, frame complete); the main loop
 * checks the inventory and memory ones. Game thread only.
 */
public final class Checkpoints {
	/** The portal path in order; the scoreboard shows them as columns. */
	public static final List<String> PORTAL_PATH = List.of("two_buckets", "flint_and_steel", "lava_seen", "obsidian_placed",
			"frame_complete", "portal_lit");

	private static final Map<String, Long> reached = new LinkedHashMap<>();
	private static long startTick;
	private static long nowTick;

	private Checkpoints() {}

	/** New session: forget everything reached so far. */
	public static void start(long tick) {
		reached.clear();
		startTick = tick;
		nowTick = tick;
	}

	public static void tick(long tick) {
		nowTick = tick;
	}

	/** Records the checkpoint the first time; returns true if it's new. */
	public static boolean mark(String name) {
		if (reached.containsKey(name)) return false;
		reached.put(name, (nowTick - startTick) / 20);
		return true;
	}

	/** "two_buckets@312s lava_seen@330s ...", in the order reached. */
	public static List<String> summary() {
		List<String> out = new ArrayList<>();
		reached.forEach((k, v) -> out.add(k + "@" + v + "s"));
		return out;
	}
}
