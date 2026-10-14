package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ExploreSideways extends EvolvedSkill {
	private static final List<String> ANIMALS = List.of("cow", "pig", "sheep", "chicken");
	private Vec3 startPos, lastPos;
	private float heading;
	private int stuckTicks, turns, waterTicks;

	public static Option offer(Context c) {
		if (!"explore".equals(c.lastSkill()) || !"TIMEOUT".equals(c.lastCode())) return null;
		if (c.lastArg() == null || !c.lastArg().contains("cow")) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 30) return null;
		String dim = Player.dimension();
		if (dim == null || !dim.contains("overworld")) return null;
		return new Option("explore_sideways", null, "explore for animals timed out: try a short walk to the side");
	}

	@Override
	public String name() { return "explore_sideways"; }

	private static Player.Mob animal(double range) {
		Player.Mob best = null;
		for (Player.Mob m : Player.mobs(range)) {
			if (!ANIMALS.contains(m.type().replace("minecraft:", ""))) continue;
			if (best == null || m.dist() < best.dist()) best = m;
		}
		return best;
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60;
		startPos = Player.pos();
		lastPos = startPos;
		heading = Player.yaw() + 90f;
	}

	private void stopKeys() {
		Player.press(Player.Key.FORWARD, false);
		Player.press(Player.Key.SPRINT, false);
		Player.press(Player.Key.JUMP, false);
	}

	private boolean dangerAhead() {
		double r = Math.toRadians(heading);
		int dx = (int) Math.round(-Math.sin(r));
		int dz = (int) Math.round(Math.cos(r));
		BlockPos ahead = Player.feet().offset(dx, 0, dz);
		int air = 0, seen = 0;
		for (int i = 0; i <= 3; i++) {
			String id = Player.blockIfVisible(ahead.below(i));
			if (id == null) continue;
			if (id.contains("lava") || id.contains("fire")) return true;
			if (i >= 1) {
				seen++;
				if (id.endsWith("air")) air++;
			}
		}
		return seen == 3 && air == 3;
	}

	private void turnAway(String why) {
		turns++;
		heading += (turns % 2 == 1) ? 90f : 135f;
		stuckTicks = 0;
	}

	@Override
	protected void tick() {
		if (ticks % 10 == 0) {
			Player.Mob near = animal(30);
			if (near != null) { stopKeys(); Player.stopWalking(); done("brought a " + near.type() + " into view"); return; }
			Player.Mob far = animal(64);
			if (far != null) {
				stopKeys();
				if (far.dist() < 24) { Player.stopWalking(); done("closed in on a " + far.type()); return; }
				if (!Player.walking()) Player.walkNear(BlockPos.containing(far.pos()), 3);
				return;
			}
		}
		if (Player.walking()) return;
		Vec3 p = Player.pos();
		double moved = Math.hypot(p.x - startPos.x, p.z - startPos.z);
		if (moved >= 56) { stopKeys(); done("walked " + Math.round(moved) + " blocks sideways, no animals yet"); return; }
		if (turns > 8) { stopKeys(); fail(Fail.STUCK, "blocked in every direction after " + Math.round(moved) + " blocks"); return; }
		if (Player.inWater()) waterTicks++; else waterTicks = Math.max(0, waterTicks - 1);
		if (waterTicks > 20 * 12) { waterTicks = 0; turnAway("water"); }
		if (ticks % 5 == 0 && dangerAhead()) turnAway("danger");
		Player.turn(heading, 0f);
		Player.press(Player.Key.FORWARD, true);
		Player.press(Player.Key.SPRINT, !Player.inWater());
		double step = Math.hypot(p.x - lastPos.x, p.z - lastPos.z);
		lastPos = p;
		if (step < 0.05) stuckTicks++; else stuckTicks = Math.max(0, stuckTicks - 1);
		Player.press(Player.Key.JUMP, Player.eyesInWater() || (stuckTicks > 3 && stuckTicks < 30));
		if (stuckTicks > 40) turnAway("stuck");
	}
}
