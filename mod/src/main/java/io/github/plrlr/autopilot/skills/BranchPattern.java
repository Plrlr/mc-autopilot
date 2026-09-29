package io.github.plrlr.autopilot.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Short connected lanes: two rock columns between tunnels expose both columns' faces. */
final class BranchPattern {
	private BranchPattern() {}

	// 26.3's ore_diamond placements rise towards the world bottom (-64); bedrock occupies
	// the bottom five layers. Feet at -58 leave a solid floor above that bedrock band.
	static final int Y = -58;
	static final int LENGTH = 24;
	static final int SPACING = 3;

	static Direction direction(int step, Direction along) {
		int lane = step / (LENGTH + SPACING);
		if (step % (LENGTH + SPACING) >= LENGTH) return along.getClockWise();
		return lane % 2 == 0 ? along : along.getOpposite();
	}

	static BlockPos[] cut(BlockPos next) {
		return new BlockPos[]{next.above(), next};
	}

	/** Hidden cells are assumed rock by FairProbe; safety is checked again after each break. */
	static boolean diggable(BlockPos next, PortalSite.Probe probe) {
		for (BlockPos p : cut(next))
			if (probe.hard(p) || probe.fluid(p) || probe.fluidNear(p)) return false;
		return true;
	}

	static boolean walkable(BlockPos next, PortalSite.Probe probe) {
		return probe.free(next) && probe.free(next.above()) && probe.solid(next.below())
				&& !probe.fluid(next.below()) && diggable(next, probe);
	}
}
