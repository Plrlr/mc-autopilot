package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.AutopilotMod;
import io.github.plrlr.autopilot.Exposure;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.DeathClock;
import io.github.plrlr.autopilot.state.Notes;
import io.github.plrlr.autopilot.state.RunNotes;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * death_recovery (2026-11-09, gene death.recovery_v2): go back for the drops the way a persistent
 * player does. A research run finished the game through 605 deaths by always walking back before
 * the despawn, re-arming, and resuming the same step; our bot died ~2 times a game and mostly
 * left its kit lying there (recover_items even reported success with an empty bag).
 *
 *   ASSESS   the newest death mark (this dimension or the other); its loaded-chunk clock (DeathClock)
 *   REARM    a cheap sword or pickaxe from what's carried (skipped when close or short on time)
 *   TRAVEL   drops in the other dimension: through the known portal (no portal known: give up)
 *   APPROACH, CLEAR   recover_items' own phases (this class extends it)
 *   COLLECT  goto death, retried twice when Baritone stops short; pickup of every drop in view
 *   VERIFY   done only if the bag gained; the death is noted recovered
 *
 * Gives up (the place is noted dangerous, the mark forgotten) after UNREACHABLE twice, when the
 * clock runs out, or when we die on the way (Autopilot.handleDeath).
 */
public final class DeathRecovery extends RecoverItems {
	private enum Pre {REARM, TRAVEL, READY}

	private static final int REARM_NEAR = 40;
	private static BlockPos unreachableSpot;
	private static int unreachableCount;

	private Pre pre = Pre.REARM;
	private String deathDim;
	private int crafts, gotoRetries;
	private boolean travelled;

	@Override
	public String name() {
		return "death_recovery";
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60 * 6;
		maxChildFails = 5;
		snapshot();
		Exposure.mark("death.recovery_v2", "assess");
		WorldMemory.Seen d = memory.nearest("death");
		if (d == null) for (WorldMemory.Seen s : memory.everywhere("death")) if (!s.dim().equals(Mc.dimension())) d = s;
		if (d == null) {
			fail(Fail.NOT_FOUND, "no death spot remembered");
			return;
		}
		spot = d.pos();
		deathDim = d.dim();
		if (!DeathClock.shared().active()) {
			giveUp(memory, "the drops' clock has run out");
			fail(Fail.TIMEOUT, "the drops have despawned by now");
			return;
		}
		if (io.github.plrlr.autopilot.Tune.on("safety.spawner_room") && deathDim.equals(Mc.dimension()) && SpawnerRoom.near(memory, spot, 8))
			fail(Fail.HAZARD, "death spot is in a remembered spawner room");
	}

	@Override
	protected boolean ownTick() {
		return pre == Pre.READY && super.ownTick();
	}

	@Override
	protected Option next() {
		switch (pre) {
			case REARM -> {
				Option o = rearm();
				if (o != null) return o;
				pre = Pre.TRAVEL;
				return next();
			}
			case TRAVEL -> {
				if (Mc.dimension().equals(deathDim)) {
					pre = Pre.READY;
					approachFrom = ticks;
					return WAIT;
				}
				if (travelled || deathDim.equals("the_end") || Mc.dimension().equals("the_end")
						|| memory.nearest("nether_portal") == null) {
					// No way across that we know of: not the place's fault, so not "dangerous".
					fail(Fail.NOT_FOUND, "drops in " + deathDim + " and no known portal there");
					return null;
				}
				travelled = true;
				Exposure.mark("death.recovery_v2", "cross-dimension");
				return new Option("enter_portal", deathDim.equals("the_nether") ? "nether" : "overworld",
						"our items are in the " + deathDim + ": back through the portal");
			}
			default -> {
				// Baritone stopped short of the spot: try again (goto keeps the mark under this gene).
				Skill.Result r = lastResult();
				if (phase == Phase.PICKUP && r != null && !r.ok() && lastOption() != null && "goto".equals(lastOption().skill())
						&& gotoRetries < 2 && memory.nearest("death") != null) {
					gotoRetries++;
					return new Option("goto", "death", "walk in for our items (again)");
				}
				Option o = super.next();
				if (o != null && o.skill().equals("pickup")) return new Option("pickup", "all", o.why());
				return o;
			}
		}
	}

	/** A cheap weapon or pickaxe the planner can craft from the bag right now (gear.sword_early's way). */
	private Option rearm() {
		boolean close = deathDim.equals(Mc.dimension()) && Act.flatDist(spot) <= REARM_NEAR;
		if (close || crafts >= 2 || ticks > 20 * 60 || DeathClock.shared().remaining() < 20 * 90) return null;
		var planner = AutopilotMod.instance() == null ? null : AutopilotMod.instance().planner;
		if (planner == null) return null;
		String want = Items2.bestTier("sword") < 1 ? "stone_sword" : Items2.bestTier("pickaxe") < 1 ? "stone_pickaxe" : null;
		if (want == null) return null;
		Option o = planner.itemStep(want, 1, 0);
		if (o == null || !o.skill().equals("craft") || lastOption() != null && o.arg() != null && o.arg().equals(lastOption().arg())) return null;
		crafts++;
		return new Option(o.skill(), o.arg(), "re-arm before going back: " + o.why());
	}

	@Override
	protected void finish() {
		memory.forget("death", spot);
		if (gainedItems()) {
			Notes.get().markRecovered(Notes.get().newestUnrecovered());
			DeathClock.shared().stop();
			unreachableSpot = null;
			Exposure.mark("death.recovery_v2", "recovered");
			Facts.report("items_recovered");
			done("went back for the items and have them");
			return;
		}
		DeathClock.shared().stop();
		Skill.Result r = lastResult();
		fail(r != null && !r.ok() && r.code() != null ? r.code() : Fail.NO_PROGRESS, "walked back but picked nothing up");
	}

	@Override
	protected void cleanup() {
		Skill.Result r = result();
		super.cleanup();
		if (r == null || r.code() != Fail.UNREACHABLE || spot == null) return;
		if (spot.equals(unreachableSpot)) unreachableCount++;
		else {
			unreachableSpot = spot;
			unreachableCount = 1;
		}
		if (unreachableCount >= 2) giveUp(memory, "unreachable twice");
	}

	/** Stop going back: note the place dangerous, forget the drops everywhere, stop their clock. */
	public static void giveUp(WorldMemory memory, String why) {
		Notes.get().markDangerous(Notes.get().newestUnrecovered());
		memory.forgetEverywhere("death");
		DeathClock.shared().stop();
		unreachableSpot = null;
		Exposure.mark("death.recovery_v2", "give up: " + why);
		Notes.save();
	}

	/** Each live, unpaused tick (Autopilot, gene on): age the drops only while their chunks are loaded. */
	public static void clockTick(WorldMemory memory, LocalPlayer pl, long tick) {
		DeathClock c = DeathClock.shared();
		if (c.tick(Mc.dimension(), pl.getX(), pl.getY(), pl.getZ())) {
			giveUp(memory, "despawn clock ran out");
			return;
		}
		// Keep the age in the notes so a restart resumes the count (saved with the notes' debounce).
		if (tick % 200 == 0 && c.active()) {
			RunNotes.Death d = Notes.get().newestUnrecovered();
			if (d != null && d.aged != c.used()) {
				d.aged = c.used();
				Notes.get().touch();
			}
		}
	}

	/** Planner (gene on): drops in the other dimension, and a portal here to reach them. */
	public static Option crossOption(WorldMemory memory) {
		if (!DeathClock.shared().active() || Mc.dimension().equals("the_end") || memory.nearest("nether_portal") == null) return null;
		for (WorldMemory.Seen s : memory.everywhere("death"))
			if (!s.dim().equals(Mc.dimension()) && !s.dim().equals("the_end"))
				return new Option("death_recovery", null, "our items are in the " + s.dim() + ": back through the portal for them");
		return null;
	}
}
