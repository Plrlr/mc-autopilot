package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class CloseIn extends EvolvedSkill {
	private double best = Double.MAX_VALUE;
	private int stall;
	private int unseen;

	public CloseIn() {
		timeoutTicks = 20 * 15;
	}

	public static Option offer(Context c) {
		if (!"attack".equals(c.lastSkill()) || !"UNREACHABLE".equals(c.lastCode())) return null;
		String type = c.lastArg();
		if (type == null || type.isEmpty() || type.equals("creeper")) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 10) return null;
		if (Player.inLava()) return null;
		if (find(type, 8) == null) return null;
		return new Option("close_in", type, "attack couldn't path to the " + type + "; step straight at it");
	}

	private static Player.Mob find(String type, double range) {
		Player.Mob best = null;
		for (Player.Mob m : Player.mobs(range))
			if (m.type().equals(type) && (best == null || m.dist() < best.dist())) best = m;
		return best;
	}

	@Override
	public String name() { return "close_in"; }

	@Override
	protected void start() {
		if (arg == null || find(arg, 10) == null) { fail(Fail.NOT_FOUND, "no " + arg + " in sight"); return; }
		Player.stopWalking();
	}

	private static boolean air(String id) { return id != null && id.endsWith("air"); }

	private static boolean solid(String id) {
		return id != null && !air(id) && !id.contains("water") && !id.contains("lava")
				&& !id.contains("grass") && !id.contains("fern") && !id.contains("flower");
	}

	private static boolean lava(String id) { return id != null && id.contains("lava"); }

	private void release() {
		Player.press(Player.Key.FORWARD, false);
		Player.press(Player.Key.JUMP, false);
	}

	@Override
	protected void tick() {
		if (Player.inLava()) { release(); fail(Fail.HAZARD, "in lava"); return; }
		Player.Mob m = find(arg, 10);
		if (m == null) {
			release();
			if (++unseen > 40) fail(Fail.NOT_FOUND, "lost sight of the " + arg);
			return;
		}
		unseen = 0;
		if (m.dist() <= 2.8) {
			release();
			done("in reach of the " + arg + " (" + Math.round(m.dist() * 10) / 10.0 + " blocks)");
			return;
		}
		if (m.dist() < best - 0.3) { best = m.dist(); stall = 0; } else stall++;
		if (stall > 200) { release(); fail(Fail.UNREACHABLE, "no headway toward the " + arg); return; }

		Player.lookAt(m.pos().add(0, 0.5, 0));
		BlockPos feet = Player.feet();
		Direction dir = Direction.fromYRot(Player.yaw());
		BlockPos ahead = feet.relative(dir);
		String a0 = Player.blockIfVisible(ahead);
		String a1 = Player.blockIfVisible(ahead.below());
		if (lava(a0) || lava(a1) || lava(Player.blockIfVisible(ahead.above()))) {
			release();
			fail(Fail.HAZARD, "lava toward the " + arg);
			return;
		}
		boolean mobBelow = m.pos().y < Player.y() - 1.5;
		if (!Player.inWater() && air(a0) && air(a1) && air(Player.blockIfVisible(ahead.below(2)))
				&& air(Player.blockIfVisible(ahead.below(3)))) {
			release();
			fail(Fail.HAZARD, "drop toward the " + arg);
			return;
		}

		if (stall > 30 && Player.onGround() && !Player.inWater()) {
			BlockPos head = ahead.above();
			BlockPos dig = null;
			if (solid(Player.blockIfVisible(head))) dig = head;
			else if (solid(a0) && (mobBelow || solid(Player.blockIfVisible(feet.above(2))))) dig = ahead;
			if (dig != null) {
				Player.press(Player.Key.FORWARD, false);
				Player.press(Player.Key.JUMP, false);
				Player.holdBestToolFor(dig);
				Player.mine(dig);
				return;
			}
		}

		boolean mobAbove = m.pos().y > Player.y() + 0.6;
		boolean jump;
		if (Player.inWater()) jump = mobAbove || Player.air() < 100 || (!mobBelow && !Player.eyesInWater() && stall > 8);
		else jump = Player.onGround() && (mobAbove || stall > 8 || solid(a0));
		Player.press(Player.Key.SPRINT, false);
		Player.press(Player.Key.FORWARD, true);
		Player.press(Player.Key.JUMP, jump);
	}
}
