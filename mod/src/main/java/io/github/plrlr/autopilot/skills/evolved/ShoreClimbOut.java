package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.skills.FairProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class ShoreClimbOut extends EvolvedSkill {
	private final Set<BlockPos> aside = new HashSet<>();
	private BlockPos target;
	private float freeYaw;
	private Vec3 mark;
	private int markTick;
	private int stalls;
	private BlockPos dig;
	private int digTicks;

	public static Option offer(Context c) {
		if (!"shore".equals(c.lastSkill())) return null;
		if (!"STUCK".equals(c.lastCode()) && !"TIMEOUT".equals(c.lastCode())) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 30) return null;
		boolean mainShore = c.main() != null && "shore".equals(c.main().skill());
		if (!mainShore && !Player.inWater()) return null;
		return new Option("shore_climb_out", null, "shore got stuck: swim by hand to visible bank and climb out");
	}

	@Override
	public String name() { return "shore_climb_out"; }

	@Override
	protected void start() {
		timeoutTicks = 20 * 40;
		Player.stopWalking();
		freeYaw = Player.yaw();
		pickTarget();
		mark = Player.pos();
		markTick = 0;
	}

	private void pickTarget() {
		target = null;
		FairProbe pr = Player.probe();
		BlockPos f = Player.feet();
		double best = Double.MAX_VALUE;
		for (int dy = -1; dy <= 2; dy++)
			for (int dx = -5; dx <= 5; dx++)
				for (int dz = -5; dz <= 5; dz++) {
					if (dx == 0 && dz == 0) continue;
					BlockPos p = f.offset(dx, dy, dz);
					if (aside.contains(p)) continue;
					BlockPos below = p.below();
					if (!pr.visible(below) || !pr.solid(below) || pr.fluid(below)) continue;
					if (!pr.free(p) || pr.fluid(p) || !pr.free(p.above())) continue;
					double d = dx * dx + dz * dz + dy * dy * 0.5;
					if (d < best) { best = d; target = p.immutable(); }
				}
		if (target == null && memory != null) {
			BlockPos l = memory.nearestLand(aside);
			if (l != null) target = l;
		}
	}

	private static boolean diggable(BlockPos p) {
		String b = Player.blockIfVisible(p);
		if (b == null) return false;
		if (b.contains("air") || b.contains("water") || b.contains("lava") || b.contains("bedrock")) return false;
		return !FairProbe.lavaSeenNear(p);
	}

	@Override
	protected void tick() {
		if (ticks > 5 && !Player.inWater() && Player.onGround()) {
			done("climbed out onto dry ground at " + Player.feet());
			return;
		}
		if (dig != null) {
			digTicks++;
			String b = Player.blockIfVisible(dig);
			if (b == null || b.contains("air") || b.contains("water") || digTicks > 80) {
				dig = null;
				Player.releaseAll();
				mark = Player.pos();
				markTick = ticks;
				return;
			}
			Player.press(Player.Key.FORWARD, false);
			Player.press(Player.Key.JUMP, Player.eyesInWater());
			Player.holdBestToolFor(dig);
			Player.lookAt(dig);
			Player.mine(dig);
			return;
		}
		Vec3 p = Player.pos();
		float yaw = freeYaw;
		if (target != null) {
			double dx = target.getX() + 0.5 - p.x, dz = target.getZ() + 0.5 - p.z;
			if (dx * dx + dz * dz > 0.04) yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		}
		Player.turn(yaw, 5);
		Player.press(Player.Key.FORWARD, true);
		Player.press(Player.Key.SPRINT, true);
		Player.press(Player.Key.JUMP, true);
		if (ticks - markTick < 60) return;
		double mx = p.x - mark.x, mz = p.z - mark.z;
		boolean moved = mx * mx + mz * mz >= 1.0;
		mark = p;
		markTick = ticks;
		if (moved) return;
		stalls++;
		if (stalls >= 8) { fail(Fail.STUCK, "hand swim stalled 8 times near " + Player.feet()); return; }
		if (stalls % 2 == 1) {
			Direction d = Direction.fromYRot(yaw);
			BlockPos f = Player.feet();
			BlockPos front = f.relative(d);
			BlockPos[] cand = { front.above(), front.above(2), f.above(2) };
			for (BlockPos c : cand) {
				if (diggable(c)) {
					dig = c.immutable();
					digTicks = 0;
					Player.releaseAll();
					return;
				}
			}
		}
		if (target != null) aside.add(target);
		pickTarget();
		if (target == null) freeYaw = yaw + 90;
	}
}
