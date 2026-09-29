package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The world as the player can see it, for planning digs (a portal room, an obsidian mold): a
 * block in line of sight (Mc.canSee) shows its real state; one we can't see is taken for plain
 * stone, the way a player assumes rock behind rock. No reading hidden lava or caves (CLAUDE.md:
 * no x-ray). What digging uncovers is seen then, and the skills re-check it as they go.
 *
 * One probe per plan: sight lines are cached, so a site search costs one raycast per cell.
 */
final class FairProbe implements PortalSite.Probe {
	private final Map<BlockPos, Boolean> seen = new HashMap<>();
	private final PortalSite.Probe world;
	private final Predicate<BlockPos> sight;
	private int rays;

	/**
	 * Sight lines one probe may trace: a portal-room search asked for ~5,800 in one tick (Codex's
	 * count). Past the budget a cell counts as unseen, i.e. rock, which is still the fair answer.
	 */
	static final int RAY_BUDGET = 600;

	FairProbe() {
		this(CastPortalSite.WORLD, Mc::canSee);
	}

	FairProbe(PortalSite.Probe world, Predicate<BlockPos> sight) {
		this.world = world;
		this.sight = sight;
	}

	boolean visible(BlockPos p) {
		Boolean v = seen.get(p);
		if (v != null) return v;
		v = rays++ < RAY_BUDGET && sight.test(p);
		seen.put(p.immutable(), v);
		return v;
	}

	@Override
	public boolean free(BlockPos p) {
		return visible(p) && world.free(p);
	}

	@Override
	public boolean solid(BlockPos p) {
		return !visible(p) || world.solid(p);
	}

	@Override
	public boolean fluid(BlockPos p) {
		return visible(p) && world.fluid(p);
	}

	@Override
	public boolean fluidNear(BlockPos p) {
		for (Direction d : Direction.values()) if (fluid(p.relative(d))) return true;
		return false;
	}

	boolean lavaNear(BlockPos p) {
		for (Direction d : Direction.values()) {
			BlockPos n = p.relative(d);
			if (visible(n) && world.lava(n)) return true;
		}
		return false;
	}

	@Override
	public boolean hard(BlockPos p) {
		return visible(p) && world.hard(p);
	}

	/** Lava we can see in p or touching it (the stairs' check before opening a cell). */
	static boolean lavaSeenNear(BlockPos p) {
		if (Mc.canSee(p) && Mc.state(p).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return true;
		for (Direction d : Direction.values()) {
			BlockPos n = p.relative(d);
			if (Mc.canSee(n) && Mc.state(n).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return true;
		}
		return false;
	}
}
