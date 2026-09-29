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
		// ---- early survival
		s.add(SkillSpec.of("kite", null).stage("any").makes("threat_cleared").prior(12, 0.7)
				.gene("skill.kite").help("face a nearby melee pursuer and trade charged hits behind a shield").build());
		s.add(SkillSpec.of("pillar", null).stage("early").needs("throwaway", 3)
				.makes("threat_cleared").prior(40, 0.6).gene("skill.pillar")
				.help("rise above clustered melee mobs and come down once the ground is clear").build());
		s.add(SkillSpec.of("block_arrows", null).stage("any").makes("threat_cleared").prior(20, 0.6)
				.gene("skill.block_arrows").help("shield up and close in on a skeleton").build());
		s.add(SkillSpec.of("creeper_defuse", null).stage("any").makes("threat_cleared").prior(10, 0.7)
				.gene("skill.creeper_defuse").help("sprint clear of a lit creeper or knock it back").build());
		s.add(SkillSpec.of("panic_box", null).stage("any").needs("throwaway", 9).makes("threat_cleared").prior(45, 0.7)
				.gene("skill.panic_box").help("box in with 9 blocks and heal").build());
		s.add(SkillSpec.of("respawn_reset", null).stage("early").facts("overworld").makes("safe_restart").prior(60, 0.7)
				.gene("skill.respawn_reset").help("after a night respawn, hide until morning").build());
		s.add(SkillSpec.of("recover_items", null).stage("any").makes("items_recovered").prior(90, 0.5)
				.gene("skill.recover_items").help("go back for dropped items carefully").build());
		s.add(SkillSpec.of("make_bed", null).stage("early").facts("overworld").gives("bed", 1).prior(120, 0.5)
				.gene("skill.make_bed").help("wool from sheep, then a bed").build());
		s.add(SkillSpec.of("food_secure", "6").stage("early").facts("overworld").gives("food", 6).prior(150, 0.6)
				.gene("skill.food_secure").help("food from a ladder of sources").build());
		s.add(SkillSpec.of("fish", "4").stage("early").needs("fishing_rod", 1).facts("water_known").gives("food", 4).prior(180, 0.6)
				.gene("skill.fish").help("fish with a rod at known water").build());
		s.add(SkillSpec.of("secure_camp", null).stage("any").needs("torch", 4).makes("camp_safe").prior(20, 0.8)
				.gene("skill.secure_camp").help("torch the dark ground around the work spot").build());
		s.add(SkillSpec.of("stair_down", "-54").stage("any").makes("at_depth").prior(90, 0.8)
				.gene("skill.stair_down").help("safe stairs down to diamond depth").build());
		// ---- wave 2 (docs/skills-40.md): the portal
		s.add(SkillSpec.of("lava_scout", null).stage("portal").facts("overworld").makes("castable_lava").prior(200, 0.5)
				.gene("skill.lava_scout").help("find a pool with 10+ lava sources").build());
		s.add(SkillSpec.of("cast_portal", null).stage("portal")
				.needs("bucket", 2, "water_bucket", 1, "flint_and_steel", 1, "throwaway", 40).facts("overworld", "lava_pool")
				.makes("portal_known").prior(480, 0.2).gene("skill.cast_portal")
				.help("dig and floor a site by the lava, then cast the portal there").build());
		s.add(SkillSpec.of("ruined_portal", null).stage("portal").needs("diamond_pickaxe", 1).facts("ruined_portal_known")
				.gives("obsidian", 6).prior(240, 0.4).gene("skill.ruined_portal").help("loot and mine a ruined portal").build());
		s.add(SkillSpec.of("obsidian_pool", null).stage("portal").needs("diamond_pickaxe", 1, "water_bucket", 1).facts("lava_pool")
				.gives("obsidian", 10).prior(300, 0.4).gene("skill.obsidian_pool").help("harden a pool, mine 10 obsidian").build());
		s.add(SkillSpec.of("diamond_hunt", "3").stage("portal").needs("iron_pickaxe", 1).facts("overworld")
				.gives("diamond", 3).prior(600, 0.35).gene("skill.diamond_hunt").help("stairs to y -54, branch-mine").build());
		s.add(SkillSpec.of("portal_repair", null).stage("portal").needs("flint_and_steel", 1).facts("obsidian_known")
				.makes("portal_known").prior(60, 0.3).gene("skill.portal_repair").help("light or finish a frame").build());
		s.add(SkillSpec.of("stash", null).stage("any").facts("overworld").makes("spares_safe").prior(30, 0.9)
				.gene("skill.stash").help("spares into a chest at base").build());
		s.add(SkillSpec.of("restock", null).stage("any").facts("chest_known").gives("iron_ingot", 3).prior(60, 0.5)
				.gene("skill.restock").help("spares back from our chest").build());
		// ---- wave 3: the Nether
		s.add(SkillSpec.of("nether_arrival", null).stage("nether").facts("in_nether").makes("nether_base").prior(15, 0.9)
				.gene("skill.nether_arrival").help("look around, floor the portal's exit").build());
		s.add(SkillSpec.of("nether_bridge", null).stage("nether").needs("throwaway", 32).facts("in_nether", "fortress_known")
				.makes("at_fortress").prior(60, 0.6).gene("skill.nether_bridge").help("bridge straight to the fortress").build());
		s.add(SkillSpec.of("fortress_scout", null).stage("nether").facts("in_nether").makes("fortress_known").prior(360, 0.35)
				.gene("skill.fortress_scout").help("vantage sweeps, then long legs").build());
		s.add(SkillSpec.of("blaze_farm", "8").stage("nether").needs("throwaway", 12).facts("in_nether", "spawner_known")
				.gives("blaze_rod", 8).prior(300, 0.35).gene("skill.blaze_farm").help("nook at the spawner").build());
		s.add(SkillSpec.of("ghast_defense", null).stage("nether").facts("in_nether").makes("threat_cleared").prior(15, 0.7)
				.gene("skill.ghast_defense").help("fireballs back, or out of sight").build());
		s.add(SkillSpec.of("gold_armor", null).stage("nether").facts("in_nether").gives("golden_helmet", 1).prior(180, 0.6)
				.gene("skill.gold_armor").help("one gold piece worn").build());
		s.add(SkillSpec.of("barter_loop", "12").stage("nether").needs("gold_ingot", 12).facts("in_nether")
				.gives("ender_pearl", 4).prior(300, 0.4).gene("skill.barter_loop").help("gold on, trade until the pearls are in").build());
		s.add(SkillSpec.of("portal_return", null).stage("nether").facts("in_nether").makes("overworld").prior(120, 0.7)
				.gene("skill.portal_return").help("home through our portal").build());
		s.add(SkillSpec.of("enderman_warped", "4").stage("any").needs("throwaway", 5).gives("ender_pearl", 4).prior(300, 0.35)
				.gene("skill.enderman_warped").help("a hut endermen can't enter").build());

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
