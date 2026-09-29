package io.github.plrlr.autopilot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Livelock breaker (gene plan.livelock_break): a minute of actions that ends where it began, with
 * no item gained and nothing killed, is a loop, whatever each action reported.
 *
 * The local trial night2 (2026-09-29) stood at (6, 120, -1) for 90+ s: the fight reflex answered a
 * skeleton it couldn't reach ("block_arrows -> ok: out of its line of fire"), then the planner sent
 * it back toward its drops, into the same line of fire ("recover_items -> interrupted by
 * reflex_fight"), again and again. Each step reported ok or was interrupted, so neither the stuck
 * box (moving skills only) nor the failure pause (failures only) saw it, and reflexes skip the
 * planner's pauses. The morning's spider behind a wall was the same shape.
 *
 * Pure logic (no game calls): SkillBook feeds it every skill end; Autopilot acts on a detection by
 * leaving the monsters near us alone for a minute and pausing the looping actions.
 */
final class LivelockWatch {
	static final double WINDOW_S = 60, MIN_MOVE = 3;
	static final int MIN_ENDS = 6;

	private record End(double gs, String key, double x, double y, double z, int inv, boolean kill) {}

	private final Deque<End> ends = new ArrayDeque<>();

	/** One skill end. Returns the action keys of the loop when this end completes one, else null. */
	Set<String> record(double gs, String key, double x, double y, double z, int inventory, boolean kill) {
		ends.addLast(new End(gs, key, x, y, z, inventory, kill));
		while (!ends.isEmpty() && gs - ends.peekFirst().gs() > WINDOW_S) ends.removeFirst();
		if (ends.size() < MIN_ENDS || gs - ends.peekFirst().gs() < WINDOW_S * 0.75) return null;
		End first = ends.peekFirst();
		Set<String> keys = new LinkedHashSet<>();
		for (End e : ends) {
			double d = Math.sqrt(sq(e.x() - first.x()) + sq(e.y() - first.y()) + sq(e.z() - first.z()));
			if (e.kill() || e.inv() != first.inv() || d >= MIN_MOVE) return null;
			keys.add(e.key());
		}
		ends.clear();
		return keys;
	}

	void clear() {
		ends.clear();
	}

	private static double sq(double v) {
		return v * v;
	}
}
