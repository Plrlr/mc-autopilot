package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class NetherHealFirst extends EvolvedSkill {
	private boolean eating;
	private boolean moving;
	private float best;
	private int bestAt;

	public NetherHealFirst() {
		timeoutTicks = 20 * 75;
	}

	public static Option offer(Context c) {
		if (c.main() == null || !"fortress".equals(c.main().skill())) return null;
		if ("blazes".equals(c.main().arg()) || (c.main().arg() != null && c.main().arg().startsWith("blazes"))) return null;
		if (!"the_nether".equals(Player.dimension())) return null;
		if (Player.health() >= 14 || Player.inLava()) return null;
		if (Player.food() < 18 && Player.count("food") == 0) return null;
		if (Player.nearestHostile(4) != null) return null;
		return new Option("nether_heal_first", null, "hurt (" + Math.round(Player.health()) + " hp) before the fortress walk: heal in a safe spot first");
	}

	@Override
	public String name() { return "nether_heal_first"; }

	@Override
	protected void start() {
		best = Player.health();
		bestAt = 0;
		Player.stopWalking();
		if (hotNear(Player.feet())) {
			BlockPos safe = safeSpot();
			if (safe != null) { Player.walkTo(safe); moving = true; }
		}
	}

	@Override
	protected void tick() {
		float hp = Player.health();
		if (hp >= 18) { done("healed to " + Math.round(hp) + " hp"); return; }
		if (Player.inLava()) { fail(Fail.HAZARD, "in lava while healing"); return; }
		if (Player.nearestHostile(4) != null) { fail(Fail.HAZARD, "a monster came close while healing"); return; }
		if (hp > best + 0.5f) { best = hp; bestAt = ticks; }
		if (ticks - bestAt > 20 * 30) { fail(Fail.NO_PROGRESS, "no health back in 30 s (at " + Math.round(hp) + ")"); return; }
		if (moving) {
			if (Player.walking() && ticks < 20 * 8) return;
			Player.stopWalking();
			moving = false;
		}
		if (ticks % 20 == 0 && hotNear(Player.feet())) {
			BlockPos safe = safeSpot();
			if (safe != null) {
				stopEat();
				Player.walkTo(safe);
				moving = true;
				return;
			}
		}
		if (Player.food() < 20 && Player.count("food") > 0) {
			if (!eating) {
				if (!Player.hold("food")) { stopEat(); return; }
				eating = true;
			}
			Player.press(Player.Key.USE, true);
			return;
		}
		stopEat();
		if (Player.food() < 18) { fail(Fail.NEED_ITEM, "hunger " + Player.food() + " and no food: health can't come back"); }
	}

	private void stopEat() {
		if (!eating) return;
		eating = false;
		Player.press(Player.Key.USE, false);
	}

	private static boolean hot(BlockPos p) {
		String b = Player.blockIfVisible(p);
		return b != null && (b.contains("lava") || b.contains("fire") || b.contains("magma_block"));
	}

	private static boolean hotNear(BlockPos feet) {
		if (hot(feet) || hot(feet.below()) || hot(feet.above())) return true;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = feet.relative(d);
			if (hot(n) || hot(n.below())) return true;
		}
		return false;
	}

	/** A visible standing spot within 3 blocks with solid floor, open space and no visible lava/fire around it. */
	private static BlockPos safeSpot() {
		BlockPos me = Player.feet();
		var probe = Player.probe();
		BlockPos best = null;
		double bd = Double.MAX_VALUE;
		for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 1; dy++) {
			if (dx == 0 && dz == 0) continue;
			BlockPos p = me.offset(dx, dy, dz);
			if (!probe.free(p) || !probe.free(p.above())) continue;
			if (!probe.visible(p.below()) || !probe.hard(p.below())) continue;
			if (probe.lavaNear(p) || probe.lavaNear(p.below()) || hotNear(p)) continue;
			double d = dx * dx + dz * dz + dy * dy * 2;
			if (d < bd) { bd = d; best = p; }
		}
		return best;
	}
}
