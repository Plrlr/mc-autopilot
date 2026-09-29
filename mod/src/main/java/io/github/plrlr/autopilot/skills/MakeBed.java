package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Goal;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.Perception;

/**
 * make_bed[:n]: wool from sheep, then beds. A bed skips the night (most deaths were at night)
 * and moves the respawn point next to the work; the End's bed route (bed_bomb) needs 5 or more.
 *
 * Hunt the nearest sheep in sight (one sheep, one wool), pick up the drops, craft planks if short,
 * craft the beds (the recipe book matches the colors we have). Explores for sheep at most twice.
 */
public final class MakeBed extends Composite {
	private int want;
	private int explores;
	private boolean justHunted;

	@Override
	public String name() {
		return "make_bed";
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60 * 6;
		maxChildFails = 5;
		want = argCount(1);
		if (!Mc.dimension().equals("overworld")) fail(Fail.WRONG_PLACE, "sheep live in the overworld");
	}

	private int beds() {
		return Mc.count(Items2.matcher("bed"));
	}

	@Override
	protected Option next() {
		int missing = want - beds();
		if (missing <= 0) return null;
		if (justHunted) {
			justHunted = false;
			return new Option("pickup", null, "pick up the wool");
		}
		if (Items2.mostOfOneColor("wool") >= 3) {
			if (Mc.count(Items2.matcher("planks")) < 3) {
				if (Mc.count(Items2.matcher("log")) > 0) return new Option("craft", "planks:4", "planks for the bed");
				return new Option("collect", "log:1", "a log for the bed's planks");
			}
			if (io.github.plrlr.autopilot.Tune.on("skill.bed_table_first")
					&& Mc.count("crafting_table") == 0 && memory.nearestStation("crafting_table") == null) {
				if (Mc.count(Items2.matcher("planks")) < 4) {
					if (Mc.count(Items2.matcher("log")) > 0) return new Option("craft", "planks:4", "planks for a crafting table");
					return new Option("collect", "log:1", "wood for a crafting table");
				}
				return new Option("craft", "crafting_table:1", "a crafting table for the bed");
			}
			if (io.github.plrlr.autopilot.Tune.on("skill.bed_table_first")
					&& Mc.count("crafting_table") > 0 && memory.nearestStation("crafting_table") == null)
				return new Option("place", "crafting_table", "place the table for the bed");
			return new Option("craft", "bed:1", "craft a bed from 3 wool and 3 planks");
		}
		Perception.Seen sheep = Perception.look(32).nearest("sheep");
		if (sheep != null) {
			justHunted = true;
			return new Option("attack", "sheep", "wool for a bed");
		}
		if (explores++ >= 2) {
			fail(Fail.NOT_FOUND, "no sheep found");
			return null;
		}
		return new Option("explore", "sheep", "look for sheep");
	}

	@Override
	protected void finish() {
		if (beds() >= want) done("have " + beds() + " bed(s)");
		else fail(Fail.NO_PROGRESS, "only " + beds() + " of " + want + " beds");
	}

	/** For the planner: a bed is worth making now (none carried or known, sheep in sight). */
	public static boolean worthIt(Perception seen) {
		return Goal.have("bed") == 0 && seen.nearest("sheep") != null && seen.hostilesWithin(12) == 0;
	}
}
