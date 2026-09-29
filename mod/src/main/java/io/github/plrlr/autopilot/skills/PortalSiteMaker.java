package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Mc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Makes a cast site one visible block at a time, then verifies the finished room. */
final class PortalSiteMaker {
	private final Act.Breaker breaker = new Act.Breaker();
	private final Set<BlockPos> bad = new HashSet<>();
	private final BlockPos pool;
	private PortalSite.Plan plan;
	private String failure;
	private int attempts, idle, placeTries;
	private int pillars;

	private PortalSiteSearch search;
	private boolean ready;

	PortalSiteMaker(BlockPos pool) { this.pool = pool; }

	PortalSite.Plan site() {
		return failure == null && ready ? plan : null;
	}
	String failure() { return failure; }

	void tick() {
		if (failure != null || ready) return;
		FairProbe seen = new FairProbe();
		if (plan == null) {
			if (search == null) {
				if (++attempts > 12) { failure = "12 visible portal sites became unsafe or unreachable"; return; }
				search = new PortalSiteSearch(Mc.player().blockPosition(), pool, bad);
			}
			if (!search.step(seen)) return;
			plan = search.best();
			search = null;
			if (plan == null) { failure = "no reachable front row with a dry, diggable 4x6 frame and repairable floor near lava"; return; }
			idle = placeTries = 0;
			return;
		}
		// Replan after every exposure: a new face can reveal fluid, bedrock, or a floor hole.
		PortalSite.Plan now = PortalSite.makerPlan(plan.origin(), plan.along(), seen);
		if (now == null) { abandon("newly exposed fluid, hard block or deep hole"); return; }
		plan = now;
		BlockPos target = closestVisibleDig(seen);
		if (target != null) {
			if (!inReach(target)) {
				if (target.getY() > Mc.player().blockPosition().getY() + 3 && pillars < 4) pillar(seen);
				else walkNear(target);
				return;
			}
			Mc.mc().options.keyJump.setDown(false);
			Bari.stop();
			if (breaker.tick(target)) idle = 0;
			else if (breaker.ticks() > 20 * 10) abandon("block would not break at " + target.toShortString());
			return;
		}
		if (seen.exhausted()) return;
		if (!plan.dig().isEmpty()) { abandon("no exposed dig face from the front row"); return; }
		for (BlockPos f : plan.floor()) {
			if (!seen.visible(f)) { abandon("floor gap cannot be seen at " + f.toShortString()); return; }
			if (seen.solid(f)) continue;
			if (seen.fluid(f) || seen.fluidNear(f)) { abandon("fluid beside floor at " + f.toShortString()); return; }
			if (!inReach(f)) { walkNear(f); return; }
			Bari.stop();
			if (Mc.count("throwaway") < 28) { failure = "fewer than 28 blocks remain for the backing wall"; return; }
			if (Act.place(f)) { placeTries = idle = 0; return; }
			if (++placeTries > 12) abandon("cannot fill floor at " + f.toShortString());
			return;
		}
		boolean fits = CastGeometry.fits(plan.origin(), plan.along(), PortalSite.WALL_H, seen);
		if (seen.exhausted()) return;
		if (fits) ready = true;
		else abandon("finished room failed CastGeometry.fits");
	}

	private BlockPos closestVisibleDig(FairProbe seen) {
		BlockPos best = null;
		double distance = Double.MAX_VALUE;
		for (BlockPos p : plan.dig()) {
			if (!seen.visible(p) || seen.free(p)) continue;
			double d = p.distSqr(Mc.player().blockPosition());
			if (d < distance) { distance = d; best = p; }
		}
		return best;
	}

	private void walkNear(BlockPos p) {
		Mc.mc().options.keyJump.setDown(false);
		breaker.stop();
		if (!Bari.pathing() && ++idle % 40 == 1) Bari.path(new GoalNear(p, 2));
		if (idle > 20 * 20) abandon("cannot reach " + p.toShortString());
	}

	private void pillar(FairProbe seen) {
		// The frame is six blocks high; a player can raise their standing row to mine its top.
		Bari.stop();
		BlockPos below = Mc.player().blockPosition().below();
		Mc.player().setXRot(90f);
		Mc.mc().options.keyJump.setDown(true);
		if (!Mc.player().onGround() && seen.free(below) && Mc.clearOfPlayer(below)
				&& !seen.fluidNear(below) && Act.holdBlock() && Mc.placeAt(below)) {
			pillars++;
			idle = 0;
			Mc.mc().options.keyJump.setDown(false);
		}
		if (++idle > 20 * 8) abandon("cannot pillar to the high frame cells");
	}

	private void abandon(String why) {
		io.github.plrlr.autopilot.AutopilotMod.LOGGER.info("[portal-site] {} abandoned: {}", plan.origin().toShortString(), why);
		breaker.stop();
		Bari.stop();
		bad.add(plan.origin());
		plan = null;
	}

	void stop() { Mc.mc().options.keyJump.setDown(false); breaker.stop(); Bari.stop(); }

	private static boolean inReach(BlockPos p) {
		return Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(p)) <= Mc.reach() - 0.5;
	}
}
