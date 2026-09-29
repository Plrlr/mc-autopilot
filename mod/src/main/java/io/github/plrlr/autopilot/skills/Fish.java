package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;

/**
 * fish[:n]: catch n fish with a rod at known water. Safe food with no hunting, and water is near
 * almost every spawn.
 *
 * Cast at the water a few blocks out, watch the bobber (an entity the player sees), and reel in
 * when it dips: a bite pulls it down sharply below where it had settled. A cast with no bite in
 * 40 s is reeled in and cast again.
 */
public final class Fish extends Skill {
	private enum Phase {WALK, CAST, WAIT, REEL}

	/** How far below its resting height a dip must pull the bobber to count as a bite. */
	static final double DIP = 0.12;

	private Phase phase = Phase.WALK;
	private BlockPos water;
	private int want, startCount, phaseTicks;
	private double restY = Double.NaN;

	@Override
	public String name() {
		return "fish";
	}

	@Override
	public boolean workingInPlace() {
		return true;
	}

	static int fishCount() {
		return Mc.count(s -> Items2.RAW_MEAT.contains(Items2.id(s)) && (Items2.id(s).equals("cod") || Items2.id(s).equals("salmon")))
				+ Mc.count("cooked_cod") + Mc.count("cooked_salmon");
	}

	/** A bite: the bobber, settled at restY, is now well below it. */
	static boolean bite(double restY, double y) {
		return !Double.isNaN(restY) && y < restY - DIP;
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60 * 5;
		want = argCount(3);
		if (Mc.count("fishing_rod") == 0) {
			fail(Fail.NEED_ITEM, "no fishing rod");
			return;
		}
		WorldMemory.Seen w = memory.nearest("water");
		if (w == null) {
			fail(Fail.NOT_FOUND, "no water known");
			return;
		}
		water = w.pos();
		startCount = fishCount();
		Bari.path(new GoalNear(water, 3));
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		phaseTicks++;
		if (fishCount() - startCount >= want) {
			done("caught " + (fishCount() - startCount) + " fish");
			return;
		}
		switch (phase) {
			case WALK -> {
				if (Bari.pathing() && phaseTicks < 20 * 40) return;
				if (Act.flatDist(water) > 6) {
					fail(Fail.UNREACHABLE, "couldn't get to the water");
					return;
				}
				to(Phase.CAST);
			}
			case CAST -> {
				if (!Mc.holdItem(s -> Items2.id(s).equals("fishing_rod"))) {
					fail(Fail.NEED_ITEM, "rod gone");
					return;
				}
				Mc.lookAt(Vec3.atCenterOf(water).add(0, 0.4, 0));
				if (phaseTicks < 5) return;
				Mc.useItem();
				restY = Double.NaN;
				to(Phase.WAIT);
			}
			case WAIT -> {
				FishingHook hook = pl.fishing;
				if (hook == null) {
					if (phaseTicks > 20) to(Phase.CAST);
					return;
				}
				// Let it land and settle (2 s), then watch for the dip.
				if (phaseTicks == 40) restY = hook.getY();
				if (phaseTicks > 40 && hook.isInWater()) {
					restY = Math.max(restY, hook.getY());
					if (bite(restY, hook.getY())) {
						Mc.useItem();
						to(Phase.REEL);
						return;
					}
				}
				if (phaseTicks > 20 * 40) {
					Mc.useItem();
					to(Phase.REEL);
				}
			}
			case REEL -> {
				if (phaseTicks > 15) to(Phase.CAST);
			}
		}
	}

	private void to(Phase p) {
		phase = p;
		phaseTicks = 0;
	}
}
