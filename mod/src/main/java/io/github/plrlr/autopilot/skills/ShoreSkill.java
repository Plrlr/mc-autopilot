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
	// Gene water.swim (2026-11-10): swimming by hand when Baritone can't.
	private int manualUntil = -1, still, under;
	private double watchX, watchZ;
	private float manualYaw;

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

	/**
	 * Gene water.swim (2026-11-10). Shore left all the swimming to Baritone, and under water Baritone
	 * finds no path at all (its log: "open set size 0", one node): the bot sank, the drowning reflex
	 * cut the skill off, shore started again and sank again (a laptop game ended on 25 such rounds;
	 * 386 game minutes lost to shore in 171 loop games). A player holds jump to stay up and swims at
	 * the land. So: head under water for a second, or no headway for two, and we swim by hand toward
	 * the seen shore for 6 s, turning 45 degrees when that way is blocked; then Baritone gets the
	 * path back, from the surface, where it can plan. True = this tick was swum by hand.
	 */
	private boolean swim(LocalPlayer pl) {
		if (!pl.isInWater()) {
			if (manualUntil >= 0) endManual();
			under = still = 0;
			return false;
		}
		under = pl.isUnderWater() ? under + 1 : 0;
		if (ticks % 20 == 0) {
			still = Math.hypot(pl.getX() - watchX, pl.getZ() - watchZ) < 1.0 ? still + 1 : 0;
			watchX = pl.getX();
			watchZ = pl.getZ();
		}
		if (manualUntil < 0) {
			if (under <= 20 && still < 2) return false;
			if (!io.github.plrlr.autopilot.Exposure.mark("water.swim", under > 20 ? "head under water" : "no headway in water")) return false;
			Bari.stop();
			double dx, dz;
			if (target != null) {
				dx = target.getX() + 0.5 - pl.getX();
				dz = target.getZ() + 0.5 - pl.getZ();
			} else {
				double a = Math.max(0, fallbackHeading) * Math.PI / 4;
				dx = Math.cos(a);
				dz = Math.sin(a);
			}
			// Minecraft's yaw: 0 faces +z, 90 faces -x.
			manualYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			manualUntil = ticks + 20 * 6;
			still = 0;
		} else if (ticks > manualUntil) {
			endManual();
			path();
			return true;
		} else if (still >= 2) {
			// A wall or a bank too high to climb: try the next direction round.
			manualYaw += 45;
			still = 0;
		}
		pl.setYRot(manualYaw);
		pl.setXRot(0);
		Mc.mc().options.keyJump.setDown(true);
		Mc.mc().options.keyUp.setDown(true);
		return true;
	}

	private void endManual() {
		manualUntil = -1;
		Mc.mc().options.keyJump.setDown(false);
		Mc.mc().options.keyUp.setDown(false);
		lastPathTick = ticks;
	}

	/** Shore is swimming by hand and holds jump itself: the drowning reflex needn't cut it off. */
	public boolean swimming() {
		return manualUntil >= 0;
	}

	@Override protected void tick() {
		if (io.github.plrlr.autopilot.Tune.on("water.swim") && !workArea("away_from_water".equals(arg)) && swim(Mc.player())) return;
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
