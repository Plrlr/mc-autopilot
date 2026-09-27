package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;

/**
 * The Nether stage's plan (rung 8, blaze rods): find a fortress, then fight blazes there. Kept
 * apart from Planner so the Nether stage can change without touching the early game.
 */
public final class NetherPlan {
	/** Rods the rung needs: 12 eyes take 6 rods (one rod makes two powder), plus 2 spare for eyes that break when thrown (outside review A3). */
	public static final int RODS = 8;

	private NetherPlan() {}

	/** The next step toward blaze rods, for a player already in the Nether. */
	public static Option blazeStep(WorldMemory memory, Perception seen) {
		return blazeStep(memory.nearest("spawner") != null || memory.nearest("nether_bricks") != null, seen.nearest("blaze") != null);
	}

	/** The decision itself, pure so unit tests can check it. */
	public static Option blazeStep(boolean knownFortress, boolean blazeSeen) {
		if (knownFortress || blazeSeen)
			return new Option("fortress", "blazes:" + RODS, "fight blazes at the fortress for " + RODS + " rods");
		return new Option("fortress", "find", "look for a nether fortress");
	}

	/** In the Nether, a blaze is handled by the fortress fight (it waits at the spawner), not a chase. */
	public static boolean fightInFortress(String dim, Perception.Seen hostile) {
		return dim.equals("the_nether") && hostile != null && hostile.type().equals("blaze");
	}
}
