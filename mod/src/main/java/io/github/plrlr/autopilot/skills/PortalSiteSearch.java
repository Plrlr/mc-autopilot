package io.github.plrlr.autopilot.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Search the reachable front rows across ticks; a depleted sight budget never ranks a plan. */
final class PortalSiteSearch {
	private record Candidate(BlockPos stand, BlockPos origin, Direction along) {}
	private final List<Candidate> candidates = new ArrayList<>();
	private final BlockPos feet;
	private int cursor;
	private PortalSite.Plan best;
	private double score = Double.MAX_VALUE;

	PortalSiteSearch(BlockPos feet, BlockPos pool, Set<BlockPos> bad) {
		this.feet = feet.immutable();
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			BlockPos stand = feet.offset(dx, 0, dz);
			for (Direction a : Direction.Plane.HORIZONTAL) for (int x = 1; x <= 2; x++) for (int f = 1; f <= 2; f++) {
				BlockPos o = stand.relative(a, -x).relative(a.getClockWise(), -f);
				if (bad.contains(o) || pool != null && (o.distSqr(pool) < 9 || o.distSqr(pool) > 196)) continue;
				candidates.add(new Candidate(stand, o, a));
			}
		}
	}

	/** True when all candidates have been considered. The caller gives us a fresh probe each tick. */
	boolean step(FairProbe seen) {
		for (int n = 0; n < 16 && cursor < candidates.size(); n++) {
			Candidate c = candidates.get(cursor);
			PortalSite.Plan p = null;
			if (seen.free(c.stand()) && seen.free(c.stand().above()) && seen.solid(c.stand().below()))
				p = PortalSite.makerPlan(c.origin(), c.along(), seen);
			// Retry this candidate next tick rather than treating budget exhaustion as hidden rock.
			if (seen.exhausted()) return false;
			cursor++;
			if (p == null) continue;
			double s = p.cost() + Math.sqrt(p.origin().distSqr(feet)) * 0.5;
			if (s < score) { score = s; best = p; }
		}
		return cursor == candidates.size();
	}

	PortalSite.Plan best() { return best; }
}
