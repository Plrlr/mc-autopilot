package io.github.plrlr.autopilot.state;

import java.util.List;

/** One survival decision over facts already visible to the player. No world reads here. */
public final class Danger {
	private Danger() {}

	public enum Kind { FIGHT, RETREAT, WALL_IN, AVOID_HAZARD, NONE }
	public record Mob(String type, double distance, double dx, double dz, boolean visible) {}
	public record Step(int dx, int dz, boolean safe, boolean water, boolean cover) {}
	public record Input(double health, int armor, int food, boolean armed, boolean shield,
			boolean burning, boolean inLava, boolean onFireSource, boolean nearHazard,
			boolean canWall, List<Mob> mobs, List<Step> steps) {}
	public record Verdict(Kind kind, String target, int dx, int dz) {
		public static Verdict of(Kind kind) { return new Verdict(kind, null, 0, 0); }
	}

	public static Verdict assess(Input in, double closeMelee, double creeperRange, double fleeHp, double alertRange) {
		Mob nearest = null, creeper = null, skeleton = null;
		int nearby = 0;
		for (Mob mob : in.mobs()) {
			if (nearest == null || mob.distance() < nearest.distance()) nearest = mob;
			if (mob.distance() <= 6) nearby++;
			if (mob.type().equals("creeper") && mob.visible() && mob.distance() < creeperRange
					&& (creeper == null || mob.distance() < creeper.distance())) creeper = mob;
			if (mob.type().equals("skeleton") && mob.visible() && mob.distance() < 16
					&& (skeleton == null || mob.distance() < skeleton.distance())) skeleton = mob;
		}
		if (in.inLava() || in.onFireSource() || in.nearHazard()) {
			Step step = bestStep(in, null, true, false);
			if (step != null) return move(Kind.AVOID_HAZARD, step);
			return Verdict.of(Kind.AVOID_HAZARD);
		}
		if (in.burning()) for (Step step : in.steps()) if (step.safe() && step.water())
			return move(Kind.AVOID_HAZARD, step);
		if (creeper != null) {
			Step step = bestStep(in, creeper, false, false);
			if (step != null) return move(Kind.RETREAT, step);
			return in.canWall() ? Verdict.of(Kind.WALL_IN) : Verdict.of(Kind.AVOID_HAZARD);
		}
		if (nearest == null) return Verdict.of(Kind.NONE);
		if (nearest.distance() >= alertRange && skeleton == null) return Verdict.of(Kind.NONE);
		if (nearest.type().equals("skeleton") && !nearest.visible() && nearest.distance() > closeMelee)
			return Verdict.of(Kind.NONE);
		// A pursuer in reach hits a player who turns to run.
		if (nearest.distance() <= closeMelee && !nearest.type().equals("skeleton"))
			return in.canWall() && in.health() <= 6 && nearby > 1
					? Verdict.of(Kind.WALL_IN) : new Verdict(Kind.FIGHT, nearest.type(), 0, 0);
		if (skeleton != null) {
			Step cover = bestStep(in, skeleton, false, true);
			if (cover != null) return move(Kind.RETREAT, cover);
			if (in.canWall() && !in.shield()) return Verdict.of(Kind.WALL_IN);
			// The attack skill advances with the shield raised when there is no cover.
			return new Verdict(Kind.FIGHT, skeleton.type(), 0, 0);
		}
		double cautiousHp = fleeHp + (in.armor() < 4 && !in.armed() ? 3 : 0) + (in.food() < 8 ? 2 : 0);
		if (in.health() <= cautiousHp && (nearby > 1 || nearest.distance() > closeMelee)) {
			Step step = bestStep(in, nearest, false, false);
			if (step != null) return move(Kind.RETREAT, step);
			if (in.canWall()) return Verdict.of(Kind.WALL_IN);
		}
		return new Verdict(Kind.FIGHT, nearest.type(), 0, 0);
	}

	private static Verdict move(Kind kind, Step step) {
		return new Verdict(kind, null, step.dx(), step.dz());
	}

	private static Step bestStep(Input in, Mob away, boolean preferWater, boolean requireCover) {
		Step best = null;
		double score = -Double.MAX_VALUE;
		for (Step step : in.steps()) {
			if (!step.safe() || requireCover && !step.cover()) continue;
			if (!preferWater) {
				boolean towardCreeper = false;
				for (Mob mob : in.mobs()) if (mob.type().equals("creeper") && mob.visible()
						&& mob.distance() < 8 && step.dx() * mob.dx() + step.dz() * mob.dz() > 0) towardCreeper = true;
				if (towardCreeper) continue;
			}
			double s = preferWater && step.water() ? 10 : 0;
			if (away != null) s -= step.dx() * away.dx() + step.dz() * away.dz();
			// A safe escape from one creeper must not run toward another.
			for (Mob mob : in.mobs()) if (mob.type().equals("creeper") && mob.visible() && mob.distance() < 8)
				s -= 2 * (step.dx() * mob.dx() + step.dz() * mob.dz()) / Math.max(1, mob.distance());
			if (best == null || s > score) { best = step; score = s; }
		}
		return best;
	}
}
