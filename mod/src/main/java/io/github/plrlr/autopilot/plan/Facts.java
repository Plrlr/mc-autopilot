package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The facts the strategist reasons over, beyond the inventory: where we are and what we've seen.
 * Everything here is what the player has seen (WorldMemory) or been told by a skill that watched
 * it happen (report): "stronghold_known" after two eye throws crossed, "crystals_down" after the
 * last crystal broke. No x-ray: a fact about a place is only true once the place was in sight.
 *
 * Names are plain strings so specs (skills/SkillSpecs) can use new ones without touching this
 * class; a skill that learns something the planner needs calls Facts.report("name").
 */
public final class Facts {
	private Facts() {}

	/** Facts skills reported this world (cleared on a new world or when the skill retracts them). */
	private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

	public static void report(String fact) {
		REPORTED.add(fact);
	}

	public static void retract(String fact) {
		REPORTED.remove(fact);
	}

	/** New world: nothing reported yet. */
	public static void clear() {
		REPORTED.clear();
	}

	/** The facts true right now. Game thread (reads the player and memory). */
	public static Set<String> now(WorldMemory memory) {
		Set<String> f = new HashSet<>(REPORTED);
		String dim = Mc.dimension();
		switch (dim) {
			case "the_nether" -> f.add("in_nether");
			case "the_end" -> f.add("in_end");
			default -> f.add("overworld");
		}
		if (Mc.isNight()) f.add("night");
		// Memory's lookups are per dimension, so "lava_pool" in the Nether means Nether lava.
		if (memory.nearest("lava") != null) f.add("lava_pool");
		if (memory.nearest("water") != null) f.add("water_known");
		if (memory.nearest("obsidian") != null) f.add("obsidian_known");
		if (memory.nearest("nether_portal") != null) f.add("portal_known");
		if (memory.nearest("nether_bricks") != null) f.add("fortress_known");
		if (memory.nearest("spawner") != null) f.add("spawner_known");
		if (memory.nearest("end_portal_frame") != null) f.add("frame_known");
		if (memory.nearest("end_portal") != null) f.add("end_portal_open");
		if (memory.nearestStation("bed") != null) f.add("bed_known");
		if (memory.nearestStation("chest") != null) f.add("chest_known");
		if (io.github.plrlr.autopilot.skills.SearchStronghold.inStronghold()) f.add("in_stronghold");
		return Collections.unmodifiableSet(f);
	}
}
