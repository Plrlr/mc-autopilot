package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.plan.Option;

/**
 * respawn_reset: right after a respawn, don't walk into the night bare-handed.
 *
 * Death spirals were common in generations 41-46 (one game: 7 deaths in 3 minutes): the bot
 * respawns at night with an empty inventory, the planner sends it to fight the zombies around
 * spawn or back to its items, and it dies again. A player respawning at night digs in first
 * (bare hands dig dirt and sand fine) and waits for the day; by day the planner rebuilds tools
 * and goes back for the items as usual.
 */
public final class RespawnReset extends Composite {
	private boolean hid;

	@Override
	public String name() {
		return "respawn_reset";
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60 * 8;
		maxChildFails = 2;
		io.github.plrlr.autopilot.plan.SurvivalPlan.respawnHandled();
		Bari.stop();
	}

	@Override
	protected Option next() {
		if (hid || !Mc.dimension().equals("overworld") || !Mc.isNight()) return null;
		hid = true;
		// A box when blocks are carried (a bed respawn keeps nothing, but a staged kit might), else dig in.
		return Mc.count(Items2.matcher("throwaway")) >= 9 ? new Option("panic_box", null, "respawned at night: box in")
				: new Option("shelter", null, "respawned at night: dig in until morning");
	}

	@Override
	protected void finish() {
		Facts.report("safe_restart");
		Skill.Result r = lastResult();
		if (r != null && !r.ok()) fail(r.code() == null ? Fail.NO_PROGRESS : r.code(), "couldn't hide after respawning: " + r.detail());
		else done(hid ? "waited out the night after respawning" : "respawned by day: carry on");
	}
}
