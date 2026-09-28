package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * collect flint, the way a player does it: one gravel is enough. With gravel in the bag, place it
 * beside us and break it again until flint drops (one time in ten, so ~10 tries, a few seconds
 * each). Without, dig one gravel we have actually seen (WorldMemory: in line of sight, lake and
 * river beds included), on dry land or under at most two blocks of water. None seen: fail, and the
 * planner explores for gravel or water.
 *
 * This replaces Baritone's search of loaded chunks, which found gravel the player never saw and
 * tunneled down to it.
 */
final class FlintSteps {
	private static final int MAX_WATER_ABOVE = 2;
	private static final int MAX_TRIES = 40;

	private enum Phase { FIND, WALK, DIG, PICKUP, PLACE, BREAK, COLLECT }

	private final CollectSkill skill;
	private final WorldMemory memory;
	private final int before;
	private Phase phase = Phase.FIND;
	private BlockPos target;
	private int wait;
	private int tries;
	/** Gravel in the bag while the block is being broken: the drop counts once this goes up. */
	private int gravelHeld;

	FlintSteps(CollectSkill skill, WorldMemory memory) {
		this.skill = skill;
		this.memory = memory;
		this.before = Mc.count("flint");
		skill.timeoutTicks = 20 * 240;
	}

	void tick() {
		if (Mc.count("flint") > before) {
			skill.done("flint after " + tries + " gravel breaks");
			return;
		}
		wait++;
		switch (phase) {
			case FIND -> find();
			case WALK -> walk();
			case DIG, BREAK -> dig();
			case PICKUP, COLLECT -> pickup();
			case PLACE -> place();
		}
	}

	private void to(Phase p) {
		phase = p;
		wait = 0;
	}

	private void find() {
		if (Mc.count("gravel") > 0) {
			to(Phase.PLACE);
			return;
		}
		target = nearestReachableGravel();
		if (target == null) {
			skill.fail(Fail.NOT_FOUND, "no gravel seen yet (lake and river beds often have it)");
			return;
		}
		Bari.path(new GoalGetToBlock(target));
		to(Phase.WALK);
	}

	/** Seen gravel, nearest first, a block of water above counting as 16 blocks of walking. */
	private BlockPos nearestReachableGravel() {
		BlockPos me = Mc.player().blockPosition();
		BlockPos best = null;
		double bestCost = Double.MAX_VALUE;
		for (WorldMemory.Seen s : memory.all("gravel")) {
			if (!s.dim().equals(Mc.dimension())) continue;
			int water = 0;
			while (water <= MAX_WATER_ABOVE && !Mc.state(s.pos().above(water + 1)).getFluidState().isEmpty()) water++;
			if (water > MAX_WATER_ABOVE) continue;
			double cost = Math.sqrt(s.pos().distSqr(me)) + 16 * water;
			if (cost < bestCost) {
				bestCost = cost;
				best = s.pos();
			}
		}
		return best;
	}

	private void walk() {
		if (!Mc.id(Mc.state(target).getBlock()).equals("gravel")) {
			// Gone (mined, or it fell): pick another.
			Bari.stop();
			to(Phase.FIND);
			return;
		}
		if (inReach(target)) {
			Bari.stop();
			gravelHeld = Mc.count("gravel");
			to(Phase.DIG);
			return;
		}
		if (wait > 20 * 60 || (wait > 40 && !Bari.pathing())) {
			memory.forget("gravel", target);
			Bari.stop();
			to(Phase.FIND);
		}
	}

	private static boolean inReach(BlockPos p) {
		return Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(p)) < Mc.reach() - 0.5 && Mc.canSee(p);
	}

	/** Break the target like a player: look at it, hold the best tool, keep swinging. */
	private void dig() {
		if (!Mc.id(Mc.state(target).getBlock()).equals("gravel")) {
			Mc.mc().gameMode.stopDestroyBlock();
			to(phase == Phase.DIG ? Phase.PICKUP : Phase.COLLECT);
			return;
		}
		if (wait > 20 * 15) {
			Mc.mc().gameMode.stopDestroyBlock();
			skill.fail(Fail.USE_FAILED, "couldn't break the gravel");
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

	/** The drop lands where the block was: walk onto it if it didn't come to us. */
	private void pickup() {
		// Breaking isn't collecting: only the bag counts. (With spare gravel the old check went
		// straight back to placing and left every drop, flint included, on the ground.)
		if (Mc.count("gravel") > gravelHeld) {
			Bari.stop();
			to(Phase.PLACE);
			return;
		}
		var drop = SeenMiner.nearestDrop(target);
		if (drop != null && wait % 10 == 1) Bari.path(new GoalNear(drop.blockPosition(), 0));
		if (drop == null && wait > 20 && Mc.count("gravel") > 0) {
			// Nothing left on the ground (it may have been the flint, which tick() counts).
			Bari.stop();
			to(Phase.PLACE);
			return;
		}
		if (wait > 20 * 8) {
			Bari.stop();
			if (phase == Phase.COLLECT && tries >= MAX_TRIES) {
				skill.fail(Fail.NO_PROGRESS, "no flint in " + tries + " tries");
				return;
			}
			to(Phase.FIND);
		}
	}

	/** Put the gravel on solid ground next to us (not where we stand), ready to break again. */
	private void place() {
		if (wait == 1) {
			if (tries >= MAX_TRIES) {
				skill.fail(Fail.NO_PROGRESS, "no flint in " + tries + " tries");
				return;
			}
			target = placeSpot();
			if (target == null) {
				// In water or on a ledge: step somewhere flat first.
				if (!Bari.pathing()) Bari.path(new GoalNear(Mc.player().blockPosition(), 3));
				wait = 0;
				if (skill.ticks > skill.timeoutTicks - 20) skill.fail(Fail.NO_ROOM, "no flat spot to place gravel");
				return;
			}
			Bari.stop();
			if (!Mc.holdItem(s -> Items2.id(s).equals("gravel"))) {
				to(Phase.FIND);
				return;
			}
			Mc.lookAt(Vec3.atCenterOf(target.below()));
			Mc.placeAt(target);
			return;
		}
		if (Mc.id(Mc.state(target).getBlock()).equals("gravel")) {
			tries++;
			gravelHeld = Mc.count("gravel");
			to(Phase.BREAK);
		} else if (wait > 20) {
			to(Phase.PLACE);
		}
	}

	private static BlockPos placeSpot() {
		var pl = Mc.player();
		if (!pl.onGround() || pl.isInWater()) return null;
		BlockPos feet = pl.blockPosition();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = feet.relative(d);
			if (Mc.free(p) && Mc.clearOfPlayer(p) && Mc.solid(p.below()) && Mc.free(p.above())) return p;
		}
		return null;
	}
}
