package io.github.plrlr.autopilot.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The route library: every way we know to get a key item or reach a key fact, beyond plain
 * crafting, smelting, mining and hunting (plan/TechTree covers those). The strategist
 * (plan/Strategist) compares the routes that give what the goal needs by expected time, using
 * the learned stats for each key when there are enough (brains/SkillStats), else these priors.
 *
 * Adding a skill: add its spec here, in its stage's block, with honest priors (what a first
 * version really manages) and its gene. Keys must match what the brain logs (SkillSpec.keyOf).
 * Routes the bot already plays have no gene: choosing between them is the strategist's job, by
 * learned cost. A new skill's gene keeps its route out until the loop has raced it.
 *
 * Priors from the loop's data where it exists (generations 41-46, 2026-09-28): the portal drill
 * saw lava in 26 of 26 tries and placed obsidian once, so casting starts at 5%.
 */
public final class SkillSpecs {
	private SkillSpecs() {}

	public static final List<SkillSpec> ALL;

	static {
		List<SkillSpec> s = new ArrayList<>();

		// ---- finding things and filling buckets: what the routes below need first
		s.add(SkillSpec.of("explore", "lava").stage("portal")
				.facts("overworld").makes("lava_pool").prior(240, 0.5)
				.help("walk and look for a lava pool (surface pools, caves in sight)").build());
		s.add(SkillSpec.of("explore", "water").stage("any")
				.makes("water_known").prior(90, 0.7)
				.help("walk and look for water").build());
		s.add(SkillSpec.of("fill_bucket", "water").stage("any")
				.needs("bucket", 1).facts("water_known").gives("water_bucket", 1).prior(30, 0.8)
				.help("fill a bucket at known water").build());

		// ---- portal: 10 obsidian and a lit frame
		s.add(SkillSpec.of("build_portal", null).stage("portal")
				.needs("bucket", 2, "water_bucket", 1, "flint_and_steel", 1, "throwaway", 28).facts("overworld", "lava_pool")
				.makes("portal_known").prior(420, 0.05)
				.help("cast the frame from a lava pool with two buckets (no diamonds)").build());
		s.add(SkillSpec.of("build_portal", "placed").stage("portal")
				.needs("obsidian", 10, "flint_and_steel", 1).facts("overworld")
				.makes("portal_known").prior(60, 0.6)
				.help("place 10 carried obsidian as a frame and light it").build());
		s.add(SkillSpec.of("make_obsidian", "obsidian").stage("portal")
				.needs("water_bucket", 1, "diamond_pickaxe", 1).facts("overworld", "lava_pool")
				.makes("obsidian_known").prior(90, 0.3)
				.help("pour water on a lava pool to harden it, for mining with a diamond pickaxe").build());
		s.add(SkillSpec.of("collect", "obsidian").stage("portal")
				.needs("diamond_pickaxe", 1).facts("obsidian_known")
				.gives("obsidian", 10).prior(150, 0.4)
				.help("mine seen obsidian with a diamond pickaxe").build());

		// ---- nether: in, fortress, blaze rods, pearls by trade
		s.add(SkillSpec.of("enter_portal", "nether").stage("portal")
				.facts("overworld", "portal_known").makes("in_nether").prior(20, 0.8)
				.help("walk into the lit nether portal").build());
		s.add(SkillSpec.of("fortress", "find").stage("nether")
				.facts("in_nether").makes("fortress_known").prior(400, 0.25)
				.help("walk long legs through the Nether looking for fortress bricks").build());
		s.add(SkillSpec.of("fortress", "blazes:7").stage("nether")
				.facts("in_nether", "fortress_known").gives("blaze_rod", 7).prior(300, 0.2)
				.help("hold a spot at the blaze spawner and hit the blazes that come close").build());
		s.add(SkillSpec.of("barter", null).stage("nether")
				.needs("gold_ingot", 12).facts("in_nether").gives("ender_pearl", 4).prior(240, 0.3)
				.help("trade gold ingots with piglins (about 1 pearl per 3 ingots on average)").build());
		s.add(SkillSpec.of("enderman_boat", null).stage("any")
				.needs("boat", 1).gives("ender_pearl", 1).prior(60, 0.3)
				.help("trap an enderman in a boat and kill it").build());
		s.add(SkillSpec.of("attack", "enderman").stage("any")
				.gives("ender_pearl", 1).prior(40, 0.35)
				.help("fight an enderman in the open").build());
		s.add(SkillSpec.of("enter_portal", "overworld").stage("nether")
				.facts("in_nether", "portal_known").makes("overworld").prior(60, 0.7)
				.help("walk back to our nether portal and through it").build());

		// ---- stronghold
		s.add(SkillSpec.of("locate_stronghold", null).stage("stronghold")
				.needs("ender_eye", 3).facts("overworld").makes("in_stronghold").prior(600, 0.2)
				.help("triangulate with 2-3 eye throws, walk there, dig down").build());
		s.add(SkillSpec.of("search_stronghold", null).stage("stronghold")
				.facts("in_stronghold").makes("frame_known").prior(300, 0.3)
				.help("explore the corridors until the portal room shows up").build());
		s.add(SkillSpec.of("fill_end_portal", null).stage("stronghold")
				.needs("ender_eye", 12).facts("frame_known").makes("end_portal_open").prior(40, 0.8)
				.help("put eyes into the empty frames").build());
		s.add(SkillSpec.of("enter_portal", "end").stage("stronghold")
				.facts("end_portal_open").makes("in_end").prior(20, 0.9)
				.help("jump into the open end portal").build());

		// ---- the End
		s.add(SkillSpec.of("shoot", "end_crystal").stage("end")
				.needs("bow", 1, "arrow", 24).facts("in_end").makes("crystals_down").prior(240, 0.2)
				.help("shoot the end crystals off their pillars").build());
		s.add(SkillSpec.of("dragon", null).stage("end")
				.facts("in_end").makes("dragon_dead").prior(900, 0.05)
				.help("wait by the fountain, hit the head while the dragon perches").build());

		// ---- new skills (docs/skills-40.md): add each one's spec below, in its stage's block.

		ALL = Collections.unmodifiableList(s);
	}

	/** Every spec that gives the item, in declaration order. */
	public static List<SkillSpec> giving(String item) {
		List<SkillSpec> out = new ArrayList<>();
		for (SkillSpec s : ALL) if (s.gives().containsKey(item)) out.add(s);
		return out;
	}

	/** Every spec that makes the fact true. */
	public static List<SkillSpec> making(String fact) {
		List<SkillSpec> out = new ArrayList<>();
		for (SkillSpec s : ALL) if (s.makes().contains(fact)) out.add(s);
		return out;
	}

	/** Specs by key, for the stats the loop reports (first spec wins on a shared key). */
	public static Map<String, SkillSpec> byKey() {
		Map<String, SkillSpec> m = new LinkedHashMap<>();
		for (SkillSpec s : ALL) m.putIfAbsent(s.key(), s);
		return m;
	}
}
