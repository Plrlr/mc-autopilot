package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;

/**
 * recover_items: go back for what we dropped, carefully.
 *
 * `goto death` walked straight back to where we died and was chosen 273 times in 120 games,
 * often into the monsters that had just killed us. A player stops short, looks, deals with what's
 * there (or lights the spot), then walks in and picks everything up:
 *
 *   APPROACH  walk to 14 blocks from the spot
 *   CLEAR     up to 3 fights with hostiles standing near it; light it if it's dark
 *   COLLECT   goto death, then pickup
 */
public class RecoverItems extends Composite {
	enum Phase {APPROACH, CLEAR, COLLECT, PICKUP, DONE}

	// Package-private so death_recovery (DeathRecovery, gene death.recovery_v2) reuses APPROACH and
	// CLEAR instead of a copy (2026-11-09).
	Phase phase = Phase.APPROACH;
	BlockPos spot;
	private int fights;
	private boolean walking;
	/** Tick APPROACH began (0 here; later in death_recovery, after its re-arm and portal trip). */
	long approachFrom;
	/** Items carried and their worth when the skill began: finish() checks the pickup really gained. */
	int countAtStart, valueAtStart;

	@Override
	public String name() {
		return "recover_items";
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 150;
		maxChildFails = 3;
		snapshot();
		WorldMemory.Seen d = memory.nearest("death");
		if (d == null) {
			fail(Fail.NOT_FOUND, "no death spot remembered");
			return;
		}
		spot = d.pos();
		if (io.github.plrlr.autopilot.Tune.on("safety.spawner_room") && SpawnerRoom.near(memory, spot, 8))
			fail(Fail.HAZARD, "death spot is in a remembered spawner room");
	}

	final void snapshot() {
		countAtStart = Mc.count(st -> true);
		valueAtStart = io.github.plrlr.autopilot.plan.SurvivalPlan.kitValue(Mc::count);
	}

	/** True when the bag holds more items, or more worth, than when the skill began. */
	final boolean gainedItems() {
		return Mc.count(st -> true) > countAtStart || io.github.plrlr.autopilot.plan.SurvivalPlan.kitValue(Mc::count) > valueAtStart;
	}

	@Override
	protected boolean ownTick() {
		if (phase != Phase.APPROACH) return false;
		if (Act.flatDist(spot) <= 15) {
			Bari.stop();
			walking = false;
			phase = Phase.CLEAR;
			return false;
		}
		if (!walking || !Bari.pathing() && ticks % 40 == 0) {
			walking = true;
			Bari.path(new GoalNear(spot, 14));
		}
		if (ticks - approachFrom > 20 * 90) fail(Fail.UNREACHABLE, "couldn't get near the death spot");
		return true;
	}

	@Override
	protected Option next() {
		switch (phase) {
			case CLEAR -> {
				Perception seen = Perception.look(24);
				for (Perception.Seen m : seen.mobs) {
					if (!m.hostile() || m.entity().blockPosition().distSqr(spot) > 12 * 12) continue;
					if (fights++ >= 3) break;
					if (m.type().equals("creeper")) return new Option("creeper_defuse", null, "a creeper at our items");
					return new Option("attack", m.type(), "clear the " + m.type() + " by our items");
				}
				phase = Phase.COLLECT;
				if (Act.blockLight(spot) < 4 && Mc.count("torch") > 0 && Mc.isNight())
					return new Option("place", "torch", "light the spot before walking in");
				return next();
			}
			case COLLECT -> {
				phase = Phase.PICKUP;
				return new Option("goto", "death", "walk in for our items");
			}
			case PICKUP -> {
				phase = Phase.DONE;
				return new Option("pickup", null, "pick up our items");
			}
			default -> {
				return null;
			}
		}
	}

	@Override
	protected void finish() {
		memory.forget("death", spot);
		// 2026-11-09: "done" only when the bag really gained. It reported items_recovered after any
		// walk back, even when goto failed and pickup found nothing (the children's results were ignored).
		if (gainedItems()) {
			Facts.report("items_recovered");
			done("went back for the items");
			return;
		}
		Skill.Result r = lastResult();
		fail(r != null && !r.ok() && r.code() != null ? r.code() : Fail.NO_PROGRESS, "walked back but picked nothing up");
	}
}
