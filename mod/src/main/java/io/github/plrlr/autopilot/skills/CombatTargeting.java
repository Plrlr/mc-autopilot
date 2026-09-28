package io.github.plrlr.autopilot.skills;

import java.util.List;

/** Chooses among already visible hostiles without reading their hidden health. */
final class CombatTargeting {
	private CombatTargeting() {}

	record Candidate(int index, String type, double distance, int observedHits, boolean safeCreeperHit) {}

	static int choose(List<Candidate> mobs, int rule, boolean canHitCreeper, double creeperRange) {
		if (mobs.isEmpty()) return -1;
		Candidate best = null;
		for (Candidate mob : mobs) {
			if (best == null) { best = mob; continue; }
			if (rule == 1) {
				// Prior hits are the damage a player could infer; actual mob health is hidden.
				if (mob.observedHits() > best.observedHits()
						|| mob.observedHits() == best.observedHits() && mob.distance() < best.distance()) best = mob;
			} else if (rule == 2 && canHitCreeper) {
				boolean urgent = mob.safeCreeperHit() && mob.distance() <= creeperRange;
				boolean oldUrgent = best.safeCreeperHit() && best.distance() <= creeperRange;
				if (urgent && !oldUrgent || urgent == oldUrgent && mob.distance() < best.distance()) best = mob;
			} else if (mob.distance() < best.distance()) best = mob;
		}
		return best.index();
	}
}
