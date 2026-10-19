package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.skills.FairProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShoreCarve extends EvolvedSkill {
	private static final List<int[]> OFFS = new ArrayList<>();

	static {
		for (int dx = -7; dx <= 7; dx++)
			for (int dz = -7; dz <= 7; dz++)
				for (int dy = -1; dy <= 2; dy++) {
					if (dx == 0 && dz == 0) continue;
					OFFS.add(new int[] {dx, dy, dz});
				}
		OFFS.sort((a, b) -> Integer.compare(a[0] * a[0] + a[2] * a[2] + 2 * a[1] * a[1],
				b[0] * b[0] + b[2] * b[2] + 2 * b[1] * b[1]));
	}

	private BlockPos target;
	private final Set<BlockPos> bad = new HashSet<>();
	private double best = Double.MAX_VALUE;
	private int stall, sinceBest;
	private BlockPos digging;
	private int digTicks;

	public ShoreCarve() {
		timeoutTicks = 20 * 60;
	}

	public static Option offer(Context c) {
		if (!Player.inWater()) return null;
		if (!"shore".equals(c.lastSkill())) return null;
		if (!"TIMEOUT".equals(c.lastCode()) && !"STUCK".equals(c.lastCode())) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 30) return null;
		return new Option("shore_carve", null, "shore " + c.lastCode() + " in water: swim to a near visible dry spot, dig a step");
	}

	@Override
	public String name() { return "shore_carve"; }

	@Override
	protected void start() {
		Player.stopWalking();
		pick();
		if (target == null) fail(Fail.NOT_FOUND, "no dry ground seen nearby");
	}

	private void pick() {
		best = Double.MAX_VALUE;
		stall = 0;
		sinceBest = 0;
		target = null;
		FairProbe pr = Player.probe();
		BlockPos f = Player.feet();
		for (int[] o : OFFS) {
			if (pr.exhausted()) break;
			BlockPos p = f.offset(o[0], o[1], o[2]);
			if (bad.contains(p)) continue;
			if (!pr.free(p) || pr.fluid(p)) continue;
			BlockPos up = p.above();
			if (pr.fluid(up) || (pr.visible(up) && !pr.free(up))) continue;
			BlockPos b = p.below();
			if (!pr.visible(b) || !pr.solid(b) || pr.fluid(b)) continue;
			target = p.immutable();
			return;
		}
		if (memory != null) target = memory.nearestLand(bad);
	}

	private static boolean solidSeen(BlockPos p) {
		String id = Player.blockIfVisible(p);
		if (id == null) return false;
		return !id.endsWith("air") && !id.contains("water") && !id.contains("lava");
	}

	@Override
	protected void tick() {
		if (!Player.inWater() && Player.onGround()) { done("on dry ground at " + Player.feet()); return; }
		if (target == null) {
			pick();
			if (target == null) { fail(Fail.NOT_FOUND, "no dry ground left to try"); return; }
		}
		Vec3 me = Player.pos();
		double dx = target.getX() + 0.5 - me.x, dz = target.getZ() + 0.5 - me.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d < best - 0.3) { best = d; stall = 0; sinceBest = 0; }
		else { stall++; sinceBest++; }

		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		if (Player.eyesInWater() && Player.air() < 60) {
			Player.press(Player.Key.FORWARD, false);
			Player.press(Player.Key.JUMP, true);
			Player.turn(yaw, -60);
			return;
		}

		Direction dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
				: (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		BlockPos f = Player.feet();

		if (digging != null) {
			if (!solidSeen(digging) || ++digTicks > 80) {
				digging = null;
				Player.releaseAll();
				stall = 0;
			} else {
				Player.press(Player.Key.FORWARD, false);
				Player.press(Player.Key.JUMP, Player.eyesInWater());
				Player.lookAt(digging);
				Player.mine(digging);
				return;
			}
		}

		if (stall > 30 && d < 4) {
			BlockPos[] cands = {f.above().relative(dir), f.above(2).relative(dir), f.above(2)};
			for (BlockPos c : cands) {
				if (solidSeen(c) && !FairProbe.lavaSeenNear(c)) {
					digging = c.immutable();
					digTicks = 0;
					Player.holdBestToolFor(c);
					return;
				}
			}
		}

		if (sinceBest > 200) {
			bad.add(target);
			if (bad.size() > 4) { fail(Fail.UNREACHABLE, "5 dry spots tried, none reached"); return; }
			pick();
			return;
		}

		Player.turn(yaw, d < 2.5 ? -20 : -5);
		Player.press(Player.Key.FORWARD, true);
		Player.press(Player.Key.SPRINT, false);
		Player.press(Player.Key.JUMP, Player.eyesInWater() || d < 2.5 || stall > 10);
	}
}
