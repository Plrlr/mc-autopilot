package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/** A small, visible one-block-deep floor repair for a cast site beside a lava pool. */
final class PortalWorkArea {
	record Site(BlockPos origin, Direction along, List<BlockPos> floor) {}

	private PortalWorkArea() {}

	static Site choose(BlockPos feet, int spareBlocks) {
		Site best = null;
		int bestScore = Integer.MAX_VALUE;
		if (spareBlocks < 1) return null;
		// Stay near the pool: every obsidian block still needs a trip for lava.
		for (int r = 2; r <= 8; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					BlockPos origin = feet.offset(dx, 0, dz);
					if (!Mc.canSee(origin)) continue;
					for (Direction along : Direction.Plane.HORIZONTAL) {
						List<BlockPos> floor = missingFloor(origin, along, Math.min(5, spareBlocks));
						if (floor == null || floor.isEmpty()) continue;
						int score = floor.size() * 100 + dx * dx + dz * dz;
						if (score < bestScore) {
							best = new Site(origin.immutable(), along, List.copyOf(floor));
							bestScore = score;
						}
					}
				}
			}
		}
		return best;
	}

	/** Null means the site is obstructed or would require digging/placing beside lava. */
	static List<BlockPos> missingFloor(BlockPos origin, Direction along, int limit) {
		Direction front = along.getClockWise();
		List<BlockPos> missing = new ArrayList<>();
		boolean safeStand = false;
		for (int x = 0; x < 4; x++) {
			BlockPos col = origin.relative(along, x);
			for (int y = 0; y < 6; y++) {
				BlockPos frame = col.above(y);
				if (!Mc.free(frame) || CastGeometry.nearLava(frame)) return null;
				BlockPos back = col.relative(front.getOpposite()).above(y);
				if (!Mc.solid(back) && !Mc.free(back)) return null;
			}
			for (int f = -1; f <= 2; f++) {
				BlockPos at = col.relative(front, f);
				if (f > 0 && (!Mc.free(at) || !Mc.free(at.above()))) return null;
				if (f < 0 && Mc.solid(at)) continue; // Existing wall also supports itself.
				BlockPos floor = at.below();
				if (Mc.solid(floor)) {
					if (f == 1 && x == 1) safeStand = true;
					continue;
				}
				if (!Mc.free(floor) || !Mc.solid(floor.below()) || !Mc.canSee(floor)
						|| CastGeometry.nearLava(floor)) return null;
				missing.add(floor.immutable());
				if (missing.size() > limit) return null;
			}
		}
		return safeStand ? missing : null;
	}
}
