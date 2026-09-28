package io.github.plrlr.autopilot.plan;

import java.util.function.Predicate;

/**
 * Which goal to work on: the lowest unfinished rung of the ladder. Night safety, food and beds
 * are the planner's upkeep, so the goal doesn't flip with the time of day.
 */
public final class GoalLadder {
	private GoalLadder() {}

	public static Goal next(Predicate<Goal> done) {
		for (Goal g : Goal.ladder()) {
			// Speedrun route: full iron armor and diamonds cost minutes and the portal can be cast
			// without them, so these rungs are skipped (the planner may still take them on the way).
			if (g.optional()) continue;
			if (!done.test(g)) return g;
		}
		return Goal.KILL_DRAGON;
	}
}
