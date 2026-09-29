package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.Perception;

/**
 * panic_box: hurt with monsters close, on any ground: box in right here (four sides at feet and
 * head height and a roof, 9 blocks, a second or two), eat, and wait to heal.
 *
 * The rules only wall in underground (shelter heal); on the surface they run, and running with a
 * mob at arm's length is how most deaths happened. The boxing itself is shelter heal's tried
 * code; this skill decides it's worth doing anywhere, and refuses when a creeper is near (its
 * blast opens any box) or in the End (endermen and the void make a box a trap).
 */
public final class PanicBox extends Composite {
	private boolean started;

	/** Enough blocks, not in the End, no creeper about to open the box. */
	public static boolean suits(Perception seen) {
		if (Mc.dimension().equals("the_end") || Mc.count(Items2.matcher("throwaway")) < 9) return false;
		Perception.Seen c = seen.nearest("creeper");
		return c == null || c.dist() > 8;
	}

	@Override
	public String name() {
		return "panic_box";
	}

	@Override
	public boolean interruptible() {
		return false;
	}

	/** Sealed in and healing: the reflexes stand down, as they do for shelter heal. */
	public boolean sealed() {
		return current() instanceof NightSkills.Shelter sh && sh.sealed();
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 75;
		maxChildFails = 0;
		if (!suits(Perception.look(16))) {
			fail(Mc.count(Items2.matcher("throwaway")) < 9 ? Fail.NEED_ITEM : Fail.HAZARD, "can't box in here");
			return;
		}
		Bari.stop();
	}

	@Override
	protected Option next() {
		if (started) return null;
		started = true;
		return new Option("shelter", "heal", "box in and heal");
	}

	@Override
	protected void finish() {
		Skill.Result r = lastResult();
		if (r != null && r.ok()) {
			Facts.report("threat_cleared");
			done(r.detail());
		} else fail(r == null || r.code() == null ? Fail.NO_PROGRESS : r.code(), r == null ? "no box" : r.detail());
	}
}
