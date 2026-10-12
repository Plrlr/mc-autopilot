package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;

/**
 * Nether deaths during fortress find/blazes come mostly from sudden damage (lava, fire, piglins,
 * wither skeletons) while the bot walks on with 16/20 health or less. Before the next fortress leg,
 * when nothing hostile is close, crouch in place (no stepping off edges, no magma damage) and let
 * health come back to full, eating first if hunger is under 18 (no regeneration below that).
 */
public class FortressRest extends EvolvedSkill {
	private float best;
	private float last;
	private int lastGain;
	private boolean eating;

	public FortressRest() {
		timeoutTicks = 20 * 75;
	}

	public static Option offer(Context c) {
		if (c.main() == null || !"fortress".equals(c.main().skill())) return null;
		if (!"the_nether".equals(Player.dimension())) return null;
		if (Player.inLava()) return null;
		float hp = Player.health();
		if (hp > 16f || hp <= 0f) return null;
		if (Player.food() < 18 && Player.count("food") == 0) return null;
		if (Player.nearestHostile(10) != null) return null;
		return new Option("fortress_rest", null,
				"health " + Math.round(hp) + "/20 and nothing hostile near: heal to full before the fortress (Nether deaths come from sudden hits)");
	}

	@Override
	public String name() { return "fortress_rest"; }

	@Override
	protected void start() {
		if (!"the_nether".equals(Player.dimension())) { fail(Fail.WRONG_PLACE, "only rests in the Nether"); return; }
		Player.stopWalking();
		best = Player.health();
		last = best;
		lastGain = 0;
	}

	@Override
	protected void tick() {
		if (Player.inLava()) { fail(Fail.HAZARD, "in lava while resting"); return; }
		float hp = Player.health();
		if (hp >= 19.5f) { done("healed to " + Math.round(hp) + " in " + ticks / 20 + " s"); return; }
		if (hp < last - 0.9f) {
			fail(Fail.HAZARD, "took damage while resting (" + Math.round(last) + " -> " + Math.round(hp) + "): fire, hot floor or a mob");
			return;
		}
		last = hp;
		if (hp > best + 0.4f) { best = hp; lastGain = ticks; }
		if (ticks % 5 == 0) {
			Player.Mob m = Player.nearestHostile(8);
			if (m != null) {
				fail(Fail.HAZARD, m.type() + " at " + Math.round(m.dist()) + " while resting");
				return;
			}
		}
		Player.press(Player.Key.SNEAK, true);
		if (Player.food() < 18) {
			if (Player.count("food") == 0) { fail(Fail.NEED_ITEM, "hunger " + Player.food() + " and no food: health can't come back"); return; }
			if (!eating) {
				if (!Player.hold("food")) { fail(Fail.USE_FAILED, "couldn't hold food"); return; }
				eating = true;
			}
			Player.press(Player.Key.USE, true);
			lastGain = ticks;
			return;
		}
		if (eating) {
			eating = false;
			Player.press(Player.Key.USE, false);
		}
		if (ticks - lastGain > 20 * 25) {
			fail(Fail.NO_PROGRESS, "no health back in 25 s (at " + Math.round(hp) + ")");
		}
	}
}
