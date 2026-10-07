package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public class ShelterClimbOut extends EvolvedSkill {
	private int bestY;
	private int lastProgress;
	private BlockPos pillarFrom;
	private int pillarTicks;
	private int pillarFails;
	private boolean noBlocks;
	private Direction stairDir;
	private BlockPos stepFrom;
	private int stepTicks;

	public static Option offer(Context c) {
		if (!"shelter".equals(c.lastSkill()) || !"STUCK".equals(c.lastCode())) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 60) return null;
		if (!"overworld".equals(Player.dimension()) || Player.inWater() || Player.inLava()) return null;
		return new Option("shelter_climb_out", null, "boxed in after shelter: dig up and out by hand");
	}

	@Override
	public String name() { return "shelter_climb_out"; }

	@Override
	protected void start() {
		timeoutTicks = 20 * 90;
		Player.stopWalking();
		bestY = Player.feet().getY();
		lastProgress = 0;
	}

	private static boolean passable(BlockPos p) {
		String id = Player.blockIfVisible(p);
		if (id == null) return false;
		return id.endsWith("air") || id.endsWith("short_grass") || id.endsWith("tall_grass")
				|| id.endsWith("snow") || id.endsWith("torch") || id.endsWith("fern");
	}

	private static boolean fluidNear(BlockPos p) {
		for (Direction d : Direction.values()) {
			String id = Player.blockIfVisible(p.relative(d));
			if (id != null && (id.contains("lava") || id.contains("water"))) return true;
		}
		return false;
	}

	private static Direction openSide(BlockPos f) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = f.relative(d);
			String below = Player.blockIfVisible(n.below());
			if (passable(n) && passable(n.above()) && below != null && !passable(n.below())
					&& !below.contains("lava") && !below.contains("water")) return d;
		}
		return null;
	}

	private void dig(BlockPos p) {
		Player.press(Player.Key.FORWARD, false);
		Player.press(Player.Key.JUMP, false);
		Player.holdBestToolFor(p);
		Player.lookAt(p);
		Player.mine(p);
	}

	@Override
	protected void tick() {
		if (Player.inLava() || Player.inWater()) { fail(Fail.HAZARD, "water or lava while climbing out"); return; }
		BlockPos f = Player.feet();
		if (f.getY() > bestY) { bestY = f.getY(); lastProgress = ticks; }
		if (ticks - lastProgress > 20 * 20) { fail(Fail.NO_PROGRESS, "no height gained for 20 s at " + f); return; }
		if (pillarFrom != null) { pillarTick(); return; }
		if (stepFrom != null) { stepTick(f); return; }
		Direction open = openSide(f);
		if (open != null) {
			Player.releaseAll();
			done("climbed out of the shelter, open to the " + open + " at " + f);
			return;
		}
		BlockPos head2 = f.above(2);
		if (!passable(head2)) {
			if (fluidNear(head2) && !noBlocks && Player.count("throwaway") > 0) {
				noBlocks = true; // pillar column leads into fluid: try steps instead
			}
			if (fluidNear(head2) && stairDir == null && !noBlocks) {
				fail(Fail.HAZARD, "fluid above the shaft");
				return;
			}
			if (!fluidNear(head2)) { dig(head2); return; }
		}
		if (!noBlocks && Player.count("throwaway") > 0 && passable(head2)) {
			if (!Player.onGround()) return;
			Player.releaseAll();
			if (!Player.hold("throwaway")) { noBlocks = true; return; }
			Player.turn(Player.yaw(), 90f);
			Player.press(Player.Key.JUMP, true);
			pillarFrom = f;
			pillarTicks = 0;
			return;
		}
		stairs(f);
	}

	private void pillarTick() {
		pillarTicks++;
		Player.turn(Player.yaw(), 90f);
		if (Player.y() >= pillarFrom.getY() + 0.9) Player.place(pillarFrom);
		if (!passable(pillarFrom) && Player.blockIfVisible(pillarFrom) != null) {
			Player.press(Player.Key.JUMP, false);
			pillarFrom = null;
			return;
		}
		if (pillarTicks > 20) {
			Player.press(Player.Key.JUMP, false);
			pillarFrom = null;
			if (++pillarFails >= 3) noBlocks = true;
		}
	}

	private void stairs(BlockPos f) {
		if (stairDir == null) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos a = f.relative(d).above();
				if (!fluidNear(a) && !fluidNear(a.above())) { stairDir = d; break; }
			}
			if (stairDir == null) { fail(Fail.HAZARD, "fluid on every side of the shaft"); return; }
		}
		BlockPos a = f.relative(stairDir).above();
		BlockPos b = a.above();
		if (!passable(a)) {
			if (fluidNear(a)) { stairDir = stairDir.getClockWise(); return; }
			dig(a);
			return;
		}
		if (!passable(b)) {
			if (fluidNear(b)) { stairDir = stairDir.getClockWise(); return; }
			dig(b);
			return;
		}
		Player.releaseAll();
		stepFrom = f;
		stepTicks = 0;
	}

	private void stepTick(BlockPos f) {
		stepTicks++;
		BlockPos target = stepFrom.relative(stairDir);
		Player.lookAt(Vec3.atCenterOf(target.above()));
		Player.press(Player.Key.FORWARD, true);
		Player.press(Player.Key.JUMP, true);
		if (f.getY() > stepFrom.getY() && Player.onGround() || stepTicks > 30) {
			Player.press(Player.Key.FORWARD, false);
			Player.press(Player.Key.JUMP, false);
			if (stepTicks > 30) stairDir = stairDir.getClockWise();
			stepFrom = null;
		}
	}
}
