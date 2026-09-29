package io.github.plrlr.autopilot.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Where to make room for a cast portal, and what it takes: the blocks to dig out and the floor to
 * lay. CastPortal only casts on a site that is already flat, open and dry (CastGeometry.fits),
 * and near lava pools that almost never exists: in 4 of 5 lava starts of generation 45 every
 * build_portal failed NO_ROOM "no flat open ground", while the one run that found a site cast and
 * lit its portal. So cast_portal makes the site the way a player does, then hands it over.
 *
 * The site is CastPortal's: frame columns x 0..3 along `along`, rows y 0..5 above the origin; a
 * wall behind (free or solid, CastPortal fills it); two rows in front to stand in, two high.
 * Pure over a Probe, so it's unit-tested on made-up terrain.
 */
final class PortalSite {
	private PortalSite() {}

	/** What the world looks like, as far as the plan cares. */
	interface Probe {
		boolean free(BlockPos p);

		boolean solid(BlockPos p);

		/** Water or lava in the cell. */
		boolean fluid(BlockPos p);

		/** Water or lava in any of the 6 neighbors (digging next to it lets it flow in). */
		boolean fluidNear(BlockPos p);

		/** Bedrock, obsidian and the like: not worth digging for a site. */
		boolean hard(BlockPos p);
	}

	record Plan(BlockPos origin, Direction along, List<BlockPos> dig, List<BlockPos> floor, int cost) {}

	static final int WALL_H = 6;
	static final int MAX_DIG = 48;

	/** The work to make a site at o along a, or null if it can't be made safely. */
	static Plan plan(BlockPos o, Direction a, Probe pr) {
		Direction front = a.getClockWise();
		List<BlockPos> dig = new ArrayList<>(), floor = new ArrayList<>();
		for (int x = 0; x < 4; x++) {
			BlockPos col = o.relative(a, x);
			for (int y = 0; y < WALL_H; y++) {
				if (!clearable(col.above(y), pr, dig)) return null;
				BlockPos back = col.relative(front.getOpposite()).above(y);
				if (pr.fluid(back)) return null;
				// The wall's cells must be solid or empty (CastGeometry.fits): a flower or torch there
				// made a planned site fail its final check in the first drill. Dig such things out.
				if (!pr.solid(back) && !pr.free(back)) {
					if (pr.hard(back)) return null;
					dig.add(back.immutable());
				}
			}
			if (!floorable(col.below(), pr, floor)) return null;
			// The wall behind rests on the ground (CastGeometry.fits wants a block under it or at it).
			BlockPos back = col.relative(front.getOpposite());
			if (!pr.solid(back) && !floorable(back.below(), pr, floor)) return null;
			for (int f = 1; f <= 2; f++) {
				BlockPos p = col.relative(front, f);
				if (!clearable(p, pr, dig) || !clearable(p.above(), pr, dig)) return null;
				if (!floorable(p.below(), pr, floor)) return null;
			}
		}
		if (dig.size() > MAX_DIG) return null;
		return new Plan(o.immutable(), a, dig, floor, dig.size() * 2 + floor.size() * 3);
	}

	/** An open cell, or one that can be dug without freeing a fluid. */
	private static boolean clearable(BlockPos p, Probe pr, List<BlockPos> dig) {
		if (pr.fluid(p) || pr.fluidNear(p)) return false;
		if (pr.free(p)) return true;
		if (pr.hard(p)) return false;
		dig.add(p.immutable());
		return true;
	}

	/** Solid ground, or an empty cell over solid ground we can fill with one block. */
	private static boolean floorable(BlockPos p, Probe pr, List<BlockPos> floor) {
		if (pr.fluid(p)) return false;
		if (pr.solid(p)) return true;
		if (!pr.free(p) || !pr.solid(p.below())) return false;
		floor.add(p.immutable());
		return true;
	}

	/**
	 * The cheapest site 3-8 blocks from us whose frame is 3-14 blocks from the lava pool (close
	 * for the bucket trips, not so close that lava can reach it), up to 1 block up or down.
	 */
	static Plan best(BlockPos feet, BlockPos pool, Probe pr, java.util.Set<BlockPos> bad) {
		Plan best = null;
		double bestScore = Double.MAX_VALUE;
		// Rings 3-8, one block up or down: ~3,000 candidate plans, each stopping at its first bad cell,
		// so a search is a few milliseconds once per site (not per tick).
		for (int r = 3; r <= 8; r++)
			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					for (int dy = -1; dy <= 1; dy++) {
						BlockPos o = feet.offset(dx, dy, dz);
						if (bad.contains(o)) continue;
						double toPool = pool == null ? 8 : Math.sqrt(o.distSqr(pool));
						if (toPool < 3 || toPool > 14) continue;
						for (Direction a : Direction.Plane.HORIZONTAL) {
							Plan p = plan(o, a, pr);
							if (p == null) continue;
							double score = p.cost() + r * 0.5 + Math.abs(dy) * 2;
							if (score < bestScore) {
								bestScore = score;
								best = p;
							}
						}
					}
				}
		return best;
	}
}
