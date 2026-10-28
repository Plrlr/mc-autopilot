package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.skills.FairProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public class WaterStairOut extends EvolvedSkill {
	private static final Direction[] DIRS = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
	private static double failSeenAt = -1000;

	private int dirIdx, rejected, stepTicks, moveTicks, steps, riseTicks;
	private BlockPos base;
	private BlockPos[] cells;

	public static Option offer(Context c) {
		double now = Player.gameSeconds();
		if ("goto".equals(c.lastSkill()) && "surface".equals(c.lastArg()) && "UNREACHABLE".equals(c.lastCode())) {
			String d = c.lastDetail() == null ? "" : c.lastDetail();
			if (d.contains("water") || Player.inWater()) failSeenAt = c.lastEndSeconds();
		}
		if (now - failSeenAt > 180 || now < failSeenAt) return null;
		if (Player.inLava() || Player.y() > 62) return null;
		String dim = Player.dimension();
		if (dim != null && !dim.contains("overworld")) return null;
		return new Option("water_stair_out", null, "goto surface found no dry stairs from the water: dig steps from the waterline");
	}

	@Override
	public String name() { return "water_stair_out"; }

	@Override
	protected void start() {
		Player.stopWalking();
		BlockPos entry = memory.surfaceEntry();
		dirIdx = 0;
		if (entry != null) {
			BlockPos f = Player.feet();
			int dx = entry.getX() - f.getX(), dz = entry.getZ() - f.getZ();
			if (Math.abs(dx) > Math.abs(dz)) dirIdx = dx > 0 ? 1 : 3;
			else dirIdx = dz > 0 ? 2 : 0;
		}
	}

	private static boolean open(BlockPos p) {
		String b = Player.blockIfVisible(p);
		return b != null && b.contains("air");
	}

	private static boolean fluid(BlockPos p) {
		String b = Player.blockIfVisible(p);
		return b != null && (b.contains("water") || b.contains("lava"));
	}

	private static boolean wetNear(BlockPos p) {
		if (FairProbe.lavaSeenNear(p)) return true;
		for (Direction d : DIRS) if (fluid(p.relative(d))) return true;
		return fluid(p.above());
	}

	private void reject(String why) {
		Player.releaseAll();
		cells = null;
		dirIdx = (dirIdx + 1) % 4;
		if (++rejected >= 4) fail(Fail.NO_ROOM, "no side to dig a step out of the water at y " + Player.feet().getY() + " (" + why + ")");
	}

	private void plan() {
		base = Player.feet();
		Direction d = DIRS[dirIdx];
		BlockPos side = base.relative(d);
		String s = Player.blockIfVisible(side);
		if (s != null && (s.contains("air") || s.contains("water") || s.contains("lava"))) { reject("no support " + d); return; }
		cells = new BlockPos[] { base.above(2), side.above(), side.above(2) };
		stepTicks = 0;
		moveTicks = 0;
	}

	@Override
	protected void tick() {
		if (Player.inLava()) { fail(Fail.HAZARD, "in lava"); return; }
		if (steps >= 6 && !Player.inWater() && Player.onGround()) { done("dug " + steps + " steps out of the water to y " + Player.feet().getY()); return; }
		if (riseTicks < 60 && Player.eyesInWater()) {
			riseTicks++;
			Player.turn(Player.yaw(), -60);
			Player.press(Player.Key.JUMP, true);
			return;
		}
		riseTicks = 60;
		if (cells == null) { plan(); return; }
		for (BlockPos p : cells) {
			if (open(p)) continue;
			if (fluid(p) || wetNear(p)) { reject("fluid by " + p.toShortString()); return; }
			if (++stepTicks > 200) { reject("couldn't mine " + p.toShortString()); return; }
			Player.press(Player.Key.FORWARD, false);
			Player.press(Player.Key.JUMP, Player.eyesInWater());
			Player.holdBestToolFor(p);
			Player.lookAt(p);
			Player.mine(p);
			return;
		}
		// All three cells open: climb onto the step.
		if (Player.feet().getY() > base.getY() && !Player.inWater()) {
			Player.releaseAll();
			steps++;
			rejected = 0;
			cells = null;
			return;
		}
		if (++moveTicks > 80) { reject("couldn't get onto the step"); return; }
		BlockPos step = cells[1];
		Player.lookAt(new Vec3(step.getX() + 0.5, step.getY() + 0.6, step.getZ() + 0.5));
		Player.press(Player.Key.FORWARD, true);
		Player.press(Player.Key.JUMP, true);
	}
}
