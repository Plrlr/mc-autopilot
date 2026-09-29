package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Goal;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.Perception;
import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * food_secure[:n]: end with n cooked food, trying the sources in order and never looping on one.
 *
 * The food search was open-ended: `explore cow` ran ten times in a row in some games. This walks a
 * ladder and stops at the first rung that works:
 *   1. raw meat carried: cook it
 *   2. an animal in sight: hunt it, pick up the drop
 *   3. an animal seen earlier in this run (remembered with where and when): walk back there
 *   4. a fishing rod and known water: fish
 *   5. explore for animals, at most three times
 */
public final class FoodSecure extends Composite {
	static final Set<String> ANIMALS = Set.of("cow", "pig", "sheep", "chicken", "rabbit");

	private int want;
	private int explores;
	private boolean pickupNext;
	private BlockPos lastAnimal;
	private boolean walking;

	@Override
	public String name() {
		return "food_secure";
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60 * 6;
		maxChildFails = 6;
		want = argCount(6);
		if (!Mc.dimension().equals("overworld")) fail(Fail.WRONG_PLACE, "hunting and fishing are for the overworld");
	}

	@Override
	protected boolean ownTick() {
		// Remember the last animal seen, with where it was: rung 3.
		if (ticks % 20 == 0) {
			for (Perception.Seen s : Perception.look(48).mobs)
				if (ANIMALS.contains(s.type())) {
					lastAnimal = s.entity().blockPosition();
					break;
				}
		}
		if (!walking) return false;
		if (lastAnimal == null || Act.flatDist(lastAnimal) < 6 || !Bari.pathing() && ticks % 20 == 0) {
			walking = false;
			Bari.stop();
			return false;
		}
		return true;
	}

	@Override
	protected Option next() {
		if (Goal.have("food") >= want) return null;
		if (pickupNext) {
			pickupNext = false;
			return new Option("pickup", null, "pick up the meat");
		}
		for (String raw : Items2.RAW_MEAT) {
			int n = Mc.count(raw);
			if (n > 0) return new Option("smelt", "cooked_" + raw + ":" + n, "cook the " + raw);
		}
		Perception seen = Perception.look(32);
		for (Perception.Seen s : seen.mobs) {
			if (ANIMALS.contains(s.type()) && !(s.type().equals("sheep") && seen.nearest("cow") != null)) {
				pickupNext = true;
				return new Option("attack", s.type(), "hunt for food");
			}
		}
		if (lastAnimal != null && Act.flatDist(lastAnimal) >= 6) {
			walking = true;
			Bari.path(new GoalNear(lastAnimal, 4));
			lastAnimal = null;
			return WAIT;
		}
		if (Mc.count("fishing_rod") == 0 && Mc.count("string") >= 2 && Mc.count("stick") >= 3 && memory.nearest("water") != null)
			return new Option("craft", "fishing_rod:1", "a rod to fish with");
		if (Mc.count("fishing_rod") > 0 && memory.nearest("water") != null)
			return new Option("fish", String.valueOf(Math.max(2, want - Goal.have("food"))), "fish for food");
		if (explores++ >= 3) {
			fail(Fail.NOT_FOUND, "no food source found after 3 searches");
			return null;
		}
		return new Option("explore", "cow,pig,sheep,chicken,rabbit", "look for animals");
	}

	@Override
	protected void finish() {
		if (Goal.have("food") >= want) done("have " + Goal.have("food") + " food");
		else fail(Fail.NO_PROGRESS, "food " + Goal.have("food") + " of " + want);
	}
}
