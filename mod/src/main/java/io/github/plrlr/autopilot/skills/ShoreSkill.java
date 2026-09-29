package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.Set;

/** Reach a work area the player has seen, before trying to dig, place or explore from water. */
public final class ShoreSkill extends Skill {
	private final Set<BlockPos> aside = new HashSet<>();
	private BlockPos target;
	private int lastPathTick;
	private double lastX, lastZ;
	private int fallbackHeading = -1;

	@Override public String name() { return "shore"; }

	/** Water and tiny islands lack the dry neighboring blocks needed by ground skills. */
	public static boolean needed() {
		LocalPlayer pl = Mc.player();
		if (pl.isInWater()) return true;
		BlockPos feet = pl.blockPosition();
		if (!pl.onGround() || !Mc.solid(feet.below())) return false;
		boolean water = false;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = feet.relative(d);
			if (!Mc.state(n).getFluidState().isEmpty() || !Mc.state(n.below()).getFluidState().isEmpty()) water = true;
		}
		return water && !workArea();
	}

	private static boolean workArea() {
		return workArea(false);
	}

	private static boolean workArea(boolean awayFromWater) {
		LocalPlayer pl = Mc.player();
		if (pl.isInWater() || !pl.onGround()) return false;
		BlockPos feet = pl.blockPosition();
		if (!Mc.free(feet) || !Mc.free(feet.above()) || !Mc.solid(feet.below())) return false;
		int room = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = feet.relative(d);
			if (awayFromWater && (!Mc.state(n).getFluidState().isEmpty()
					|| !Mc.state(n.below()).getFluidState().isEmpty())) return false;
			if (Mc.free(n) && Mc.free(n.above()) && Mc.solid(n.below())) room++;
		}
		return room >= 2;
	}

	@Override protected void start() {
		timeoutTicks = 20 * 90;
		if (workArea("away_from_water".equals(arg))) { done("already on dry ground"); return; }
		path();
	}

	@Override protected void cleanup() {
		if (target != null && result() != null && (result().code() == Fail.STUCK || result().code() == Fail.TIMEOUT))
			memory.markBadLand(target);
		super.cleanup();
	}

	private void path() {
		LocalPlayer pl = Mc.player();
		target = memory.nearestLand(aside);
		lastPathTick = ticks;
		lastX = pl.getX();
		lastZ = pl.getZ();
		if (target != null) {
			Bari.path(new GoalBlock(target));
			return;
		}
		// With no seen shore, swim back along the least watery seen direction. A long goal keeps
		// moving and lets sight scans find a real shore on the way.
		if (fallbackHeading < 0) fallbackHeading = (memory.exploreHeading(pl.getX(), pl.getZ(), 32) + 4) % 8;
		int best = fallbackHeading;
		double bestScore = Double.MAX_VALUE;
		for (int h = 0; h < 8; h++) {
			double a = h * Math.PI / 4;
			double score = Math.min(Math.abs(h - fallbackHeading), 8 - Math.abs(h - fallbackHeading));
			for (WorldMemory.Seen water : memory.all("water")) {
				double dx = water.pos().getX() - pl.getX(), dz = water.pos().getZ() - pl.getZ();
				double d2 = dx * dx + dz * dz;
				if (d2 < 4 || d2 > 40 * 40) continue;
				if (dx * Math.cos(a) + dz * Math.sin(a) > Math.sqrt(d2) * 0.7) score += 8 / Math.sqrt(d2);
			}
			if (score < bestScore) { bestScore = score; best = h; }
		}
		fallbackHeading = best;
		double a = best * Math.PI / 4;
		Bari.path(new GoalXZ((int) (pl.getX() + Math.cos(a) * 32), (int) (pl.getZ() + Math.sin(a) * 32)));
	}

	@Override protected void tick() {
		if (ticks % 10 != 0) return;
		if (workArea("away_from_water".equals(arg))) { done("reached dry ground with room to work"); return; }
		if (ticks - lastPathTick < 20 * 5) return;
		BlockPos seen = memory.nearestLand(aside);
		if (seen != null && !seen.equals(target)) { path(); return; }
		if (Bari.pathing() && ticks - lastPathTick < 20 * 15) return;
		LocalPlayer pl = Mc.player();
		if (Bari.pathing() && Math.hypot(pl.getX() - lastX, pl.getZ() - lastZ) >= 3) {
			// A distant shore or a long fallback swim can take more than 15 seconds.
			lastPathTick = ticks;
			lastX = pl.getX(); lastZ = pl.getZ();
			return;
		}
		if (target != null) {
			aside.add(target);
			memory.markBadLand(target);
		}
		else if (Math.hypot(pl.getX() - lastX, pl.getZ() - lastZ) < 3) fallbackHeading = (fallbackHeading + 2) % 8;
		path();
	}
}
