package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;

/**
 * The Nether stage's plan (rung 8, blaze rods): find a fortress, then fight blazes there. Kept
 * apart from Planner so the Nether stage can change without touching the early game.
 *
 * Wave 3 of docs/skills-40.md hooks in here too, each behind its gene: arrival, bridging to a far
 * fortress, scouting from a vantage, the spawner nook, ghasts, gold armor, bartering, the enderman
 * hut, and the way home.
 */
public final class NetherPlan {
	/** Rods the rung needs: 12 eyes take 6 rods (one rod makes two powder), plus 2 spare for eyes that break when thrown (outside review A3). */
	public static final int RODS = 8;

	private NetherPlan() {}

	/** The next step toward blaze rods, for a player already in the Nether. */
	public static Option blazeStep(WorldMemory memory, Perception seen) {
		Option o = blazeStep(memory.nearest("spawner") != null || memory.nearest("nether_bricks") != null, seen.nearest("blaze") != null);
		return withSkills(o, memory);
	}

	/** The decision itself, pure so unit tests can check it. */
	public static Option blazeStep(boolean knownFortress, boolean blazeSeen) {
		if (knownFortress || blazeSeen)
			return new Option("fortress", "blazes:" + RODS, "fight blazes at the fortress for " + RODS + " rods");
		return new Option("fortress", "find", "look for a nether fortress");
	}

	/** In the Nether, a blaze is handled by the fortress fight (it waits at the spawner), not a chase. */
	public static boolean fightInFortress(String dim, Perception.Seen hostile) {
		return dim.equals("the_nether") && hostile != null && (hostile.type().equals("blaze"));
	}

	/** The wave-3 skills in place of the fortress skill's two modes. */
	static Option withSkills(Option o, WorldMemory memory) {
		if (o.arg().equals("find")) {
			return Tune.on("skill.fortress_scout") ? new Option("fortress_scout", null, "look for a fortress from high up, then walk long legs") : o;
		}
		if (Tune.on("skill.nether_bridge")) {
			WorldMemory.Seen bricks = memory.nearest("nether_bricks");
			if (bricks != null && bricks.pos().distSqr(Mc.player().blockPosition()) > 32 * 32 && Mc.count("throwaway") >= 16)
				return new Option("nether_bridge", null, "get to the fortress in a straight line, bridging over lava");
		}
		if (Tune.on("skill.blaze_farm") && memory.nearest("spawner") != null)
			return new Option("blaze_farm", String.valueOf(RODS), "hold a nook at the spawner and hit the blazes that come");
		return o;
	}

	// ---- arrival and the way home

	private static String lastDim = "";
	private static boolean arrivalPending;

	/** Called each decision: the first decision after entering the Nether offers nether_arrival. */
	public static Option arrival() {
		String dim = Mc.dimension();
		if (!dim.equals(lastDim)) {
			arrivalPending = dim.equals("the_nether");
			lastDim = dim;
		}
		if (!arrivalPending || !Tune.on("skill.nether_arrival")) return null;
		return new Option("nether_arrival", null, "just arrived: look around and make the portal's exit safe");
	}

	/** nether_arrival started (or the moment passed). */
	public static void arrived() {
		arrivalPending = false;
	}

	/** Going back to the overworld: portal_return (it rebuilds a lost portal from carried obsidian). */
	public static Option home(Option old) {
		if (!Tune.on("skill.portal_return") || !Mc.dimension().equals("the_nether")) return old;
		return new Option("portal_return", null, "back to the overworld through our portal (or a new one)");
	}

	// ---- threats and trades

	/** In the Nether: a ghast in sight, or piglins while we wear no gold. First in the urgent list. */
	public static Option urgent(Perception seen) {
		if (!Mc.dimension().equals("the_nether")) return null;
		Perception.Seen g = seen.nearest("ghast");
		if (Tune.on("skill.ghast_defense") && g != null && g.dist() < 48 && Mc.canSee(g.entity()))
			return new Option("ghast_defense", null, "a ghast has us in sight");
		if (Tune.on("skill.gold_armor") && seen.nearest("piglin") != null && seen.nearest("piglin").dist() < 16
				&& !io.github.plrlr.autopilot.skills.NetherRoutes.wearingGold())
			return new Option("gold_armor", null, "piglins near and no gold on");
		return null;
	}

	/** Pearls: barter round after round in the Nether, or the enderman hut where endermen are. */
	public static Option pearls(Perception seen) {
		int target = Tune.i("pearls.target");
		if (Tune.on("skill.barter_loop") && Mc.dimension().equals("the_nether") && (seen.nearest("piglin") != null || Mc.count("gold_ingot") > 0))
			return new Option("barter_loop", String.valueOf(target), "trade gold with piglins until we have the pearls");
		if (Tune.on("skill.enderman_warped") && !Mc.dimension().equals("the_end") && Mc.count("throwaway") >= 5
				&& countOf(seen, "enderman") >= 2)
			return new Option("enderman_warped", String.valueOf(target), "endermen about: a hut they can't enter, hit their legs");
		return null;
	}

	private static int countOf(Perception seen, String type) {
		int n = 0;
		for (Perception.Seen s : seen.mobs) if (s.type().equals(type)) n++;
		return n;
	}
}
