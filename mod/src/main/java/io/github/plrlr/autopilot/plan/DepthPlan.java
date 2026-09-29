package io.github.plrlr.autopilot.plan;

/**
 * How deep to branch-mine for iron (gene gather.cautious_depth).
 *
 * gather.iron_y is 16: most ore, and also the most caves and monsters. The local trial night2
 * (2026-09-29, everything on) respawned on a mountain four times, each time staircased ~115 blocks
 * down to y 5-20 within ~90 s with stone tools, no armor, no food and no torches, and died there
 * four times (zombie, enderman, zombie, spider). Iron is still common at y 40 (the ore band runs to
 * y 56), in smaller, fewer caves. So: without armor, no deeper than y 40; after a death deep down,
 * no deeper than y 48 for the rest of the run; with armor (10+, e.g. an iron chestplate and
 * leggings), the gene's own depth.
 */
public final class DepthPlan {
	private DepthPlan() {}

	static final int UNARMORED_Y = 40, AFTER_DEEP_DEATH_Y = 48, DEEP = 40, ARMORED = 10;
	private static volatile int deepDeaths;

	/** Autopilot calls this when we die: a death below y 40 underground makes later trips shallower. */
	public static void noteDeath(int y, boolean underground) {
		if (underground && y < DEEP) deepDeaths++;
	}

	public static void reset() {
		deepDeaths = 0;
	}

	/** The iron depth to use, given the gene's depth and our armor value. */
	public static int ironY(int base, int armor) {
		if (armor >= ARMORED) return base;
		int y = Math.max(base, UNARMORED_Y);
		return deepDeaths > 0 ? Math.max(y, AFTER_DEEP_DEATH_Y) : y;
	}
}
