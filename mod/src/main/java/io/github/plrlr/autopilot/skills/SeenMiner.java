package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

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
	private final boolean visibleOnly;
	/**
	 * Blocks we couldn't reach or break, until when (ms). Shared across runs of the skill and with
	 * explore: otherwise explore says "already see a log" and collect says "can't get it", forever
	 * (a test run bounced 7 times). Not forgotten: they're still there, just not for now.
	 */
	private static final Map<BlockPos, Long> UNREACHABLE = new HashMap<>();
	private static final long UNREACHABLE_MS = 3 * 60 * 1000;
	private Phase phase = Phase.FIND;
	private BlockPos target;
	/** The last block of our kind we broke: its neighbours are where the vein goes on (gene gather.vein_follow). */
	private BlockPos lastBroken;
	/** A leaf (or other soft block) between us and the target: broken first, like a player does. */
	private BlockPos blocker;
	private int wait;
	private int mined;

	SeenMiner(WorldMemory memory, String group, int range) {
		this(memory, group, range, false);
	}

	SeenMiner(WorldMemory memory, String group, int range, boolean visibleOnly) {
		this.memory = memory;
		this.group = group;
		this.range = range;
		this.visibleOnly = visibleOnly;
	}

	/** Breaking a block (or the leaves in front of it): no walking expected. */
	boolean breaking() {
		return phase == Phase.BREAK;
	}

	int mined() {
		return mined;
	}

	/** Is any seen block of this group within range? */
	static boolean anySeen(WorldMemory memory, String group, int range) {
		return nearest(memory, group, range) != null;
	}

	static boolean anyVisible(WorldMemory memory, String group, int range) {
		return nearest(memory, group, range, true) != null;
	}

	/** The stair fallback needs this even when the separate dry-target gene is off. */
	static boolean anyDryStone(WorldMemory memory, int range) {
		BlockPos me = Mc.player().blockPosition();
		for (WorldMemory.Seen s : memory.all("stone"))
			if (s.dim().equals(Mc.dimension()) && !unreachable(s.pos())
					&& s.pos().distSqr(me) <= (double) range * range && dry(s.pos())) return true;
		return false;
	}

	/** Tried lately and couldn't be reached or broken. */
	static boolean unreachable(BlockPos p) {
		Long until = UNREACHABLE.get(p);
		if (until == null) return false;
		if (until > System.currentTimeMillis()) return true;
		UNREACHABLE.remove(p);
		return false;
	}

	/** Leave this block alone for a few minutes (unreachable, or water already looked into). */
	static void setAside(BlockPos p) {
		UNREACHABLE.put(p, System.currentTimeMillis() + UNREACHABLE_MS);
	}

	/** The skill was cut off (stuck) on the way to this block: don't head straight back to it. */
	void setAsideTarget() {
		if (target != null) setAside(target);
	}

	private void giveUp() {
		setAside(target);
		Bari.stop();
		to(Phase.FIND);
	}

	Status tick() {
		wait++;
		if (visibleOnly && target != null && (phase == Phase.WALK || phase == Phase.BREAK)
				&& !visibleCandidate(target)) {
			Mc.mc().gameMode.stopDestroyBlock();
			giveUp();
			return Status.WORKING;
		}
		switch (phase) {
			case FIND -> {
				// Gene gather.vein_follow: the block next to the one we just broke first, as a player follows
				// a vein. Watching the local trial (2026-09-29), the bot mined one coal, then walked back to a
				// coal it had seen earlier instead of the coal the break had just uncovered behind it.
				BlockPos next = visibleOnly || Tune.on("gather.vein_follow") ? veinNext(lastBroken, group) : null;
				if (visibleOnly && next != null && !visibleCandidate(next)) next = null;
				target = next != null ? next : nearest(memory, group, range, visibleOnly);
				if (target == null) return Status.NONE_LEFT;
				if (visibleOnly) {
					Bari.stop();
					blocker = null;
					to(Phase.BREAK);
				} else {
					Bari.path(new GoalGetToBlock(target));
					to(Phase.WALK);
				}
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
			blocker = null;
			to(Phase.BREAK);
			return;
		}
		if (closeEnough(target)) {
			// Near, but leaves (or grass, a vine) hide it: clear the way first.
			BlockPos b = blockerTo(target);
			if (b != null) {
				Bari.stop();
				blocker = b;
				to(Phase.BREAK);
				return;
			}
		}
		if (wait > 20 * 40 || (wait > 40 && !Bari.pathing())) giveUp();
	}

	private static boolean closeEnough(BlockPos p) {
		return Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(p)) < Mc.reach() - 0.5;
	}

	/** The soft block the line of sight hits before the target, or null. */
	private static BlockPos blockerTo(BlockPos p) {
		var pl = Mc.player();
		var r = pl.level().clip(new net.minecraft.world.level.ClipContext(pl.getEyePosition(), Vec3.atCenterOf(p),
				net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, pl));
		if (r.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || r.getBlockPos().equals(p)) return null;
		BlockPos hit = r.getBlockPos();
		String id = Mc.id(Mc.state(hit).getBlock());
		boolean soft = id.endsWith("_leaves") || id.equals("vine") || Mc.state(hit).getDestroySpeed(pl.level(), hit) <= 0.6f;
		return soft && Mc.state(hit).getDestroySpeed(pl.level(), hit) >= 0 ? hit.immutable() : null;
	}

	static boolean inReach(BlockPos p) {
		return Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(p)) < Mc.reach() - 0.5 && Mc.canSee(p);
	}

	private void breakIt() {
		if (blocker != null) {
			if (Mc.free(blocker)) {
				Mc.mc().gameMode.stopDestroyBlock();
				blocker = null;
				to(Phase.WALK);
				return;
			}
			if (wait > 20 * 6) {
				Mc.mc().gameMode.stopDestroyBlock();
				blocker = null;
				giveUp();
				return;
			}
			hit(blocker);
			return;
		}
		if (!stillThere()) {
			Mc.mc().gameMode.stopDestroyBlock();
			memory.forget(group, target);
			mined++;
			lastBroken = target;
			to(Phase.PICKUP);
			return;
		}
		if (wait > 20 * 12) {
			// Wrong tool or out of reach after all.
			Mc.mc().gameMode.stopDestroyBlock();
			giveUp();
			return;
		}
		hit(target);
	}

	/** One tick of breaking a block like a player: look, best tool, keep swinging. */
	private void hit(BlockPos p) {
		Mc.lookAt(Vec3.atCenterOf(p));
		Swim.whileBreaking(p);
		// Every tick, not just the first: a torch placed mid-break left the torch in hand, and the
		// rest of the block was mined at bare-hand speed (seen on the laptop, coal by fist).
		NightSkills.Shelter.holdBestTool(Mc.state(p));
		if (wait == 1) {
			Mc.mc().gameMode.startDestroyBlock(p, Direction.UP);
		} else {
			Mc.mc().gameMode.continueDestroyBlock(p, Direction.UP);
		}
		Mc.swing();
	}

	/** Drops land near the block (a high log's fall to the ground): walk over the nearest one. */
	private void pickup() {
		ItemEntity drop = nearestDrop(target);
		if (visibleOnly && drop != null) {
			FairProbe probe = new FairProbe();
			BlockPos p = drop.blockPosition();
			if (!Mc.canSee(drop) || p.distSqr(Mc.player().blockPosition()) > 4
					|| p.getY() != Mc.player().getBlockY()
					|| !probe.visible(p.below()) || !BranchPattern.walkable(p, probe)) {
				Bari.stop();
				// A floor ore's drop can still be collected from the adjacent ledge without stepping in.
				if (wait > 20) to(Phase.FIND);
				return;
			}
		}
		if (drop == null) {
			if (wait > 10) to(Phase.FIND);
			return;
		}
		if (wait % 10 == 1) Bari.path(new GoalNear(drop.blockPosition(), 0));
		if (wait > 20 * 10) {
			Bari.stop();
			to(Phase.FIND);
		}
	}

	/** The nearest dropped item within 6 blocks of a spot, or null. */
	static ItemEntity nearestDrop(BlockPos near) {
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

	/** A visible block of this group touching `at` (faces, edges and corners), nearest to us; null if none. */
	private static BlockPos veinNext(BlockPos at, String group) {
		if (at == null) return null;
		BlockPos me = Mc.player().blockPosition(), best = null;
		for (int dx = -1; dx <= 1; dx++)
			for (int dy = -1; dy <= 1; dy++)
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos p = at.offset(dx, dy, dz);
					if (p.equals(at) || unreachable(p) || !Mc.canSee(p)) continue;
					if (!group.equals(WorldMemory.groupOf(Mc.id(Mc.state(p).getBlock())))) continue;
					if (best == null || p.distSqr(me) < best.distSqr(me)) best = p;
				}
		return best;
	}

	private static BlockPos nearest(WorldMemory memory, String group, int range) {
		return nearest(memory, group, range, false);
	}

	private static BlockPos nearest(WorldMemory memory, String group, int range, boolean visibleOnly) {
		BlockPos me = Mc.player().blockPosition();
		BlockPos best = null;
		double bestCost = Double.MAX_VALUE;
		for (WorldMemory.Seen s : memory.all(group)) {
			if (!s.dim().equals(Mc.dimension()) || unreachable(s.pos())) continue;
			if (visibleOnly && !visibleCandidate(s.pos())) continue;
			if (group.equals("stone") && Tune.on("gather.dry_stone") && !dry(s.pos())) continue;
			// Range on the plain distance, like explore's "already see one": with the height
			// penalty in the range check, a tree on a slope 20 blocks off was "not seen" while
			// explore saw it, and the two bounced the job (17x 'no log seen' in one batch).
			if (s.pos().distSqr(me) > (double) range * range) continue;
			// Under deep water a block is out of reach for the pickaxe (and the air runs out).
			if (!visibleOnly && !Mc.state(s.pos().above()).getFluidState().isEmpty() && !Mc.state(s.pos().above(2)).getFluidState().isEmpty()) continue;
			// Low blocks first: a log above head height needs a pillar, the one at the trunk's foot doesn't.
			double up = Math.max(0, s.pos().getY() - me.getY() - 2);
			double cost = s.pos().distSqr(me) + 64 * up * up;
			if (cost < bestCost) {
				bestCost = cost;
				best = s.pos();
			}
		}
		return best;
	}

	private static boolean visibleCandidate(BlockPos p) {
		// A floor vein beside us is reachable from the ledge; never remove our own support.
		return !p.equals(Mc.player().blockPosition().below()) && inReach(p) && !new FairProbe().fluidNear(p);
	}

	/** Water touching exposed stone makes the approach and the freshly opened cell unsafe. */
	static boolean dry(BlockPos p) {
		for (Direction d : Direction.values())
			if (Mc.canSee(p.relative(d)) && !Mc.state(p.relative(d)).getFluidState().isEmpty()) return false;
		return true;
	}
}
