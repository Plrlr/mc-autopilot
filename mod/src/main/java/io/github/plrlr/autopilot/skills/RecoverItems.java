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
public final class RecoverItems extends Composite {
	private enum Phase {APPROACH, CLEAR, COLLECT, PICKUP, DONE}

	private Phase phase = Phase.APPROACH;
	private BlockPos spot;
	private int fights;
	private boolean walking;

	@Override
	public String name() {
		return "recover_items";
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 150;
		maxChildFails = 3;
		WorldMemory.Seen d = memory.nearest("death");
		if (d == null) {
			fail(Fail.NOT_FOUND, "no death spot remembered");
			return;
		}
		spot = d.pos();
		if (io.github.plrlr.autopilot.Tune.on("safety.spawner_room") && SpawnerRoom.near(memory, spot, 8))
			fail(Fail.HAZARD, "death spot is in a remembered spawner room");
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
		if (ticks > 20 * 90) fail(Fail.UNREACHABLE, "couldn't get near the death spot");
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
		Facts.report("items_recovered");
		done("went back for the items");
	}
}
