package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** One level 1x2 dig, rechecking exposed fluids before every swing and footstep. */
final class BranchStep {
	enum Status { BUSY, MOVED, BLOCKED, STUCK }
	private final Act.Breaker breaker = new Act.Breaker();
	final BlockPos next;
	private final BlockPos back;
	private final Direction dir;
	private final Set<BlockPos> trail;
	private boolean retreating, walking;
	private int walk;

	BranchStep(BlockPos feet, Direction dir, BlockPos back, Set<BlockPos> trail) {
		this.next = feet.relative(dir);
		this.dir = dir;
		this.back = back;
		this.trail = trail;
	}

	boolean breaking() { return !walking && !retreating; }

	Status tick() {
		FairProbe probe = new FairProbe();
		if (retreating) return back(probe);
		Mc.mc().options.keyUp.setDown(false);
		if (!BranchPattern.diggable(next, probe)) {
			stop();
			retreating = true;
			walk = 0;
			return back(probe);
		}
		for (BlockPos p : BranchPattern.cut(next)) {
			// Never inspect or swing at the hidden lower block through the upper one.
			if (!probe.visible(p)) return Status.STUCK;
			if (!probe.free(p)) {
				if (breaker.ticks() > 20 * 12) { stop(); return Status.BLOCKED; }
				breaker.tick(p);
				return Status.BUSY;
			}
		}
		breaker.stop();
		// Once opened, the floor must actually be visible: an assumed rock floor isn't a bridge.
		if (!probe.visible(next.below()) || !BranchPattern.walkable(next, probe) || cave(probe)) {
			retreating = true;
			walk = 0;
			return back(probe);
		}
		walking = true;
		if (arrived(next)) { stop(); return Status.MOVED; }
		if (++walk > 40) { stop(); return Status.STUCK; }
		move(next);
		return Status.BUSY;
	}

	private boolean cave(FairProbe probe) {
		for (Direction side : new Direction[]{dir.getClockWise(), dir.getCounterClockWise()}) {
			BlockPos p = next.relative(side);
			// Known tunnel junctions are fine; an open cave invites the fights that broke diamond-a.
			if (!trail.contains(p) && (probe.free(p) || probe.free(p.above()))) return true;
		}
		return false;
	}

	private Status back(FairProbe probe) {
		if (back == null || arrived(back)) { stop(); return Status.BLOCKED; }
		if (!probe.visible(back.below()) || !BranchPattern.walkable(back, probe) || ++walk > 40) {
			stop();
			return Status.STUCK;
		}
		move(back);
		return Status.BUSY;
	}

	private static boolean arrived(BlockPos p) {
		return Mc.player().onGround() && Mc.player().blockPosition().equals(p)
				&& Math.abs(Mc.player().getX() - p.getX() - 0.5) < 0.2
				&& Math.abs(Mc.player().getZ() - p.getZ() - 0.5) < 0.2;
	}

	private static void move(BlockPos p) {
		Mc.lookAt(Vec3.atCenterOf(p).add(0, 1.12, 0));
		Mc.mc().options.keyUp.setDown(true);
	}

	void stop() {
		breaker.stop();
		Mc.mc().options.keyUp.setDown(false);
	}
}
