package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * search_stronghold: find the portal room inside a stronghold, the way a player explores one.
 * The eye of ender leads to the stronghold, not to its portal room, which can be 50+ blocks of
 * corridors away. Frontier exploration: remember which 4x4x4 cells we've walked through; look (in
 * line of sight only, no x-ray) for open corridor cells next to stronghold bricks; walk to the
 * nearest unvisited one; repeat until an end portal frame is in view (WorldMemory sees it).
 */
public final class SearchStronghold extends Skill {
	/** Stronghold building blocks: corridors are the air next to these. */
	private static final Set<String> BRICKS = Set.of("stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
			"infested_stone_bricks", "infested_mossy_stone_bricks", "infested_cracked_stone_bricks", "iron_bars", "oak_door",
			"iron_door", "bookshelf", "end_portal_frame");
	/** Cells walked through, across runs of this skill (the same stronghold). */
	private static final Set<Long> VISITED = new HashSet<>();

	private BlockPos goal;
	private int legTicks;
	private int legs;

	@Override
	public String name() {
		return "search_stronghold";
	}

	@Override
	public boolean interruptible() {
		return false;
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60 * 6;
		if (!Mc.dimension().equals("overworld")) {
			fail(Fail.WRONG_PLACE, "strongholds are in the overworld");
			return;
		}
		if (bricksNear(Mc.player().blockPosition(), 8) < 6) fail(Fail.WRONG_PLACE, "not inside a stronghold");
	}

	/** True when the player is in or right beside a stronghold (the planner's check). */
	public static boolean inStronghold() {
		return Mc.dimension().equals("overworld") && bricksNear(Mc.player().blockPosition(), 8) >= 6;
	}

	private static int bricksNear(BlockPos c, int r) {
		int n = 0;
		for (int dx = -r; dx <= r; dx += 2)
			for (int dy = -3; dy <= 3; dy += 2)
				for (int dz = -r; dz <= r; dz += 2)
					if (BRICKS.contains(Mc.id(Mc.state(c.offset(dx, dy, dz)).getBlock()))) n++;
		return n;
	}

	private static long cell(BlockPos p) {
		return BlockPos.asLong(p.getX() >> 2, p.getY() >> 2, p.getZ() >> 2);
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		VISITED.add(cell(pl.blockPosition()));
		if (memory.nearest("end_portal_frame") != null) {
			done("found the portal room after " + legs + " corridors");
			return;
		}
		legTicks++;
		if (goal != null && (pl.blockPosition().distSqr(goal) < 4 || legTicks > 20 * 25 || (legTicks > 20 && !Bari.pathing()))) {
			VISITED.add(cell(goal));
			goal = null;
		}
		if (goal != null) return;
		goal = nextFrontier(pl);
		if (goal == null) {
			fail(Fail.NOT_FOUND, "no unexplored corridor in sight (" + legs + " walked)");
			return;
		}
		legs++;
		legTicks = 0;
		Bari.path(new GoalBlock(goal));
	}

	/**
	 * The nearest open cell we can see, next to stronghold bricks, in a 4x4x4 cell we haven't
	 * walked through. Standing room (air at feet and head, solid below) so Baritone can go there.
	 */
	private static BlockPos nextFrontier(LocalPlayer pl) {
		BlockPos c = pl.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		int rays = 0;
		for (int dx = -16; dx <= 16; dx++) {
			for (int dz = -16; dz <= 16; dz++) {
				for (int dy = -6; dy <= 6; dy++) {
					BlockPos p = c.offset(dx, dy, dz);
					double d = dx * dx + dy * dy * 2 + dz * dz;
					if (d >= bestD || d < 9 || VISITED.contains(cell(p))) continue;
					if (!Mc.free(p) || !Mc.free(p.above()) || !Mc.solid(p.below())) continue;
					if (!BRICKS.contains(Mc.id(Mc.state(p.below()).getBlock())) && bricksNear(p, 2) < 2) continue;
					if (++rays > 400) return best;
					if (!Mc.canSee(p) && !Mc.canSee(p.above())) continue; // only corridors in sight
					best = p.immutable();
					bestD = d;
				}
			}
		}
		return best;
	}

	/** Forget the walked cells (a new stronghold, a new world). */
	public static void reset() {
		VISITED.clear();
	}
}
