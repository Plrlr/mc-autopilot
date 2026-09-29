package io.github.plrlr.autopilot.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Set;

/**
 * Where to make obsidian one block at a time (obsidian_mold). Seen from above, in a row:
 *
 *   S  stand here (free for feet and head, solid floor)
 *   A  the cell in front, open: we pour lava down through it and later reach the obsidian
 *   P  the floor block under A, dug out into a pit: walled on four sides and floored, so the lava
 *      poured in stays put and the obsidian it turns into lies on solid ground (mined obsidian
 *      over a lava lake drops into the lava and burns)
 *   W  the cell past A, over solid floor: water poured here runs over A into the pit
 *
 * Pure over PortalSite.Probe, so it's unit-tested on made-up terrain.
 */
final class MoldSite {
	private MoldSite() {}

	record Site(BlockPos stand, Direction dir) {
		BlockPos open() {
			return stand.relative(dir);
		}

		BlockPos pit() {
			return open().below();
		}

		BlockPos water() {
			return open().relative(dir);
		}
	}

	/** The site with stand at s facing d, or null if it can't hold lava and water safely. */
	static Site at(BlockPos s, Direction d, PortalSite.Probe pr) {
		if (!pr.free(s) || !pr.free(s.above()) || !pr.solid(s.below()) || pr.fluidNear(s) || pr.fluidNear(s.above())) return null;
		Site site = new Site(s.immutable(), d);
		BlockPos a = site.open(), p = site.pit(), w = site.water();
		if (!pr.free(a) || !pr.free(a.above()) || pr.fluidNear(a)) return null;
		if (!pr.solid(p) || pr.hard(p) || pr.fluid(p)) return null;
		if (!pr.solid(p.below()) || pr.fluid(p.below())) return null;
		for (Direction h : Direction.Plane.HORIZONTAL) {
			BlockPos side = p.relative(h);
			if (!pr.solid(side) || pr.fluid(side)) return null;
		}
		if (!pr.free(w) || pr.fluidNear(w) || !pr.solid(w.below())) return null;
		return site;
	}

	/** The nearest site within 5 blocks of feet (2 up or down), skipping stands in `bad`. */
	static Site find(BlockPos feet, PortalSite.Probe pr, Set<BlockPos> bad) {
		for (int r = 0; r <= 5; r++)
			for (int dy = 0; dy <= 2; dy = dy <= 0 ? 1 - dy : -dy)
				for (int dx = -r; dx <= r; dx++)
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
						BlockPos s = feet.offset(dx, dy, dz);
						if (bad.contains(s)) continue;
						for (Direction d : Direction.Plane.HORIZONTAL) {
							Site site = at(s, d, pr);
							if (site != null) return site;
						}
					}
		return null;
	}
}
