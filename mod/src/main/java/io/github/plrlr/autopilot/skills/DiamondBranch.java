package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalYLevel;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.plan.Option;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.Set;

/** Gated diamond search: collect exposed veins, otherwise stay in narrow level tunnels. */
final class DiamondBranch extends Composite {
	private final Set<BlockPos> trail = new HashSet<>(), blocked = new HashSet<>();
	private int want, steps, turns, descent;
	private boolean descending;
	private Direction along;
	private BlockPos back, collectingAt;
	private BranchStep step;

	@Override public String name() { return "diamond_branch"; }

	@Override public boolean workingInPlace() { return step != null && step.breaking(); }

	@Override protected void start() {
		timeoutTicks = 20 * 60 * 10;
		want = argCount(3);
		if (!Tune.on("deep.branch")) { fail(Fail.WRONG_PLACE, "deep.branch is off"); return; }
		if (Items2.bestTier("pickaxe") < 2) { fail(Fail.NEED_ITEM, "diamonds need an iron pickaxe"); return; }
		if (!Mc.dimension().equals("overworld")) { fail(Fail.WRONG_PLACE, "diamonds are in the overworld"); return; }
		Bari.stop();
		Bari.setLegitMine(true);
		Bari.setMineY(BranchPattern.Y);
		if (Mc.player().getBlockY() != BranchPattern.Y) {
			descending = true;
			Bari.path(new GoalYLevel(BranchPattern.Y));
		}
		along = Mc.player().getDirection();
	}

	@Override protected boolean ownTick() {
		if (Mc.count("diamond") >= want) { done("have " + Mc.count("diamond") + " diamonds"); return true; }
		if (descending) {
			if (Mc.player().getBlockY() == BranchPattern.Y && Mc.player().onGround()) {
				Bari.stop();
				descending = false;
			} else if (++descent > 20 * 120 || (descent > 40 && !Bari.pathing())) {
				fail(Fail.UNREACHABLE, "couldn't reach branch depth");
			}
			return true;
		}
		if (step == null) return false;
		BranchStep.Status status = step.tick();
		if (status == BranchStep.Status.BUSY) return true;
		step.stop();
		BlockPos feet = Mc.player().blockPosition();
		if (status == BranchStep.Status.STUCK) { fail(Fail.STUCK, "couldn't take a safe tunnel step"); return true; }
		if (status == BranchStep.Status.MOVED) {
			back = feet.relative(BranchPattern.direction(steps, along).getOpposite());
			trail.add(feet);
			steps++;
		} else {
			blocked.add(step.next);
			if (++turns > 8) { fail(Fail.HAZARD, "too many blocked branches"); return true; }
			along = BranchPattern.direction(steps, along).getClockWise();
			steps = 0;
			back = null;
		}
		step = null;
		return false;
	}

	@Override protected Option next() {
		if (Mc.count("diamond") >= want) return null;
		BlockPos feet = Mc.player().blockPosition();
		if (collectingAt != null) {
			// Picking up a vein can shift our feet; don't back towards an old, nonadjacent cell.
			if (!feet.equals(collectingAt)) { back = null; steps = 0; }
			collectingAt = null;
		}
		if (feet.getY() != BranchPattern.Y) {
			fail(Fail.WRONG_PLACE, "left the level tunnel while collecting");
			return WAIT;
		}
		if (SeenMiner.anyVisible(memory, "diamond_ore", 8)) {
			collectingAt = feet;
			return new Option("collect", "diamond:" + (want - Mc.count("diamond")) + ":seen", "take exposed diamonds before tunnelling on");
		}
		trail.add(feet);
		Direction dir = BranchPattern.direction(steps, along);
		for (int i = 0; i < 4 && (blocked.contains(feet.relative(dir)) || trail.contains(feet.relative(dir))); i++) {
			dir = dir.getClockWise();
			along = dir;
			steps = 0;
		}
		if (blocked.contains(feet.relative(dir)) || trail.contains(feet.relative(dir))) {
			fail(Fail.HAZARD, "no fresh branch direction");
			return WAIT;
		}
		step = new BranchStep(feet, dir, back, trail);
		return WAIT;
	}

	@Override protected void finish() {
		if (Mc.count("diamond") >= want) done("have " + Mc.count("diamond") + " diamonds");
		else fail(Fail.NOT_FOUND, "not enough diamonds");
	}

	@Override protected void cleanup() {
		if (step != null) step.stop();
		super.cleanup();
	}
}
