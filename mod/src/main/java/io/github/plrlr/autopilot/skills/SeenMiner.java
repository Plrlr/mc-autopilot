package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Mines blocks the player has actually seen (WorldMemory: exposed and in line of sight), one after
 * another, nearest first: walk up to it, break it like a player, pick up what dropped. Breaking a
 * log shows the one above it, so a tree is felled from the bottom up; a vein of ore in a cave wall
 * the same way. Baritone only walks; it is never asked to search for the block, so it can't head
 * for one nobody has seen.
 */
final class SeenMiner {
	enum Status { WORKING, NONE_LEFT }

	private enum Phase { FIND, WALK, BREAK, PICKUP }

	private final WorldMemory memory;
	private final String group;
	private final int range;
	/** Blocks we couldn't reach or break this time: skipped, not forgotten (they're still there). */
	private final Set<BlockPos> skipped = new HashSet<>();
	private Phase phase = Phase.FIND;
	private BlockPos target;
	private int wait;
	private int mined;

	SeenMiner(WorldMemory memory, String group, int range) {
		this.memory = memory;
		this.group = group;
		this.range = range;
	}

	int mined() {
		return mined;
	}

	/** Is any seen block of this group within range? */
	static boolean anySeen(WorldMemory memory, String group, int range) {
		return nearest(memory, group, range, Set.of()) != null;
	}

	Status tick() {
		wait++;
		switch (phase) {
			case FIND -> {
				target = nearest(memory, group, range, skipped);
				if (target == null) return Status.NONE_LEFT;
				Bari.path(new GoalGetToBlock(target));
				to(Phase.WALK);
			}
			case WALK -> walk();
			case BREAK -> breakIt();
			case PICKUP -> pickup();
		}
		return Status.WORKING;
	}

	private void to(Phase p) {
		phase = p;
		wait = 0;
	}

	private boolean stillThere() {
		String g = WorldMemory.groupOf(Mc.id(Mc.state(target).getBlock()));
		return group.equals(g);
	}

	private void walk() {
		if (!stillThere()) {
			memory.forget(group, target);
			Bari.stop();
			to(Phase.FIND);
			return;
		}
		if (inReach(target)) {
			Bari.stop();
			to(Phase.BREAK);
			return;
		}
		if (wait > 20 * 40 || (wait > 40 && !Bari.pathing())) {
			skipped.add(target);
			Bari.stop();
			to(Phase.FIND);
		}
	}

	static boolean inReach(BlockPos p) {
		return Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(p)) < Mc.reach() - 0.5 && Mc.canSee(p);
	}

	private void breakIt() {
		if (!stillThere()) {
			Mc.mc().gameMode.stopDestroyBlock();
			memory.forget(group, target);
			mined++;
			to(Phase.PICKUP);
			return;
		}
		if (wait > 20 * 12) {
			// Wrong tool or out of reach after all.
			Mc.mc().gameMode.stopDestroyBlock();
			skipped.add(target);
			to(Phase.FIND);
			return;
		}
		Mc.lookAt(Vec3.atCenterOf(target));
		if (wait == 1) {
			NightSkills.Shelter.holdBestTool(Mc.state(target));
			Mc.mc().gameMode.startDestroyBlock(target, Direction.UP);
		} else {
			Mc.mc().gameMode.continueDestroyBlock(target, Direction.UP);
		}
		Mc.swing();
	}

	/** Drops land near the block (a high log's fall to the ground): walk over the nearest one. */
	private void pickup() {
		ItemEntity drop = nearestDrop(target);
		if (drop == null) {
			if (wait > 10) to(Phase.FIND);
			return;
		}
		if (wait % 10 == 1) Bari.path(new GoalNear(drop.blockPosition(), 0));
		if (wait > 20 * 6) {
			Bari.stop();
			to(Phase.FIND);
		}
	}

	private static ItemEntity nearestDrop(BlockPos near) {
		ItemEntity best = null;
		double bestD = 6 * 6;
		for (Entity e : Mc.mc().level.entitiesForRendering()) {
			if (!(e instanceof ItemEntity it) || !it.isAlive()) continue;
			double d = it.blockPosition().distSqr(near);
			if (d < bestD) {
				bestD = d;
				best = it;
			}
		}
		return best;
	}

	private static BlockPos nearest(WorldMemory memory, String group, int range, Set<BlockPos> skip) {
		BlockPos me = Mc.player().blockPosition();
		BlockPos best = null;
		double bestD = (double) range * range;
		for (WorldMemory.Seen s : memory.all(group)) {
			if (!s.dim().equals(Mc.dimension()) || skip.contains(s.pos())) continue;
			// Under deep water a block is out of reach for the pickaxe (and the air runs out).
			if (!Mc.state(s.pos().above()).getFluidState().isEmpty() && !Mc.state(s.pos().above(2)).getFluidState().isEmpty()) continue;
			double d = s.pos().distSqr(me);
			if (d < bestD) {
				bestD = d;
				best = s.pos();
			}
		}
		return best;
	}
}
