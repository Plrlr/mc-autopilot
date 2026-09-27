package io.github.plrlr.autopilot.plan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the planner knows about how to get items. Only used for planning; the actual crafting goes
 * through the game's recipe book, so a small mistake here costs a failed step, not a cheat.
 */
public final class TechTree {
	private TechTree() {}

	/** out = item made, yield = items per craft, in = ingredients per craft, table = needs a 3x3 grid. */
	public record Recipe(String out, int yield, Map<String, Integer> in, boolean table) {}

	/** blocks = what Baritone mines, tier = pickaxe tier needed (-1 = hands ok), mineY = good Y for legit mining. */
	public record Source(List<String> blocks, int tier, Integer mineY) {}

	public static final Map<String, Recipe> CRAFT = new LinkedHashMap<>();
	public static final Map<String, String> SMELT = new LinkedHashMap<>();
	public static final Map<String, Source> MINE = new LinkedHashMap<>();
	public static final Map<String, List<String>> MOB = new LinkedHashMap<>();

	private static void craft(String out, int yield, boolean table, Object... in) {
		Map<String, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i < in.length; i += 2) m.put((String) in[i], (Integer) in[i + 1]);
		CRAFT.put(out, new Recipe(out, yield, m, table));
	}

	static {
		craft("planks", 4, false, "log", 1);
		craft("stick", 4, false, "planks", 2);
		craft("crafting_table", 1, false, "planks", 4);
		craft("wooden_pickaxe", 1, true, "planks", 3, "stick", 2);
		craft("wooden_sword", 1, true, "planks", 2, "stick", 1);
		craft("stone_pickaxe", 1, true, "stone", 3, "stick", 2);
		craft("stone_sword", 1, true, "stone", 2, "stick", 1);
		craft("stone_axe", 1, true, "stone", 3, "stick", 2);
		craft("furnace", 1, true, "stone", 8);
		craft("torch", 4, false, "coal", 1, "stick", 1);
		craft("iron_pickaxe", 1, true, "iron_ingot", 3, "stick", 2);
		craft("iron_sword", 1, true, "iron_ingot", 2, "stick", 1);
		craft("bucket", 1, true, "iron_ingot", 3);
		craft("shield", 1, true, "planks", 6, "iron_ingot", 1);
		craft("iron_helmet", 1, true, "iron_ingot", 5);
		craft("iron_chestplate", 1, true, "iron_ingot", 8);
		craft("iron_leggings", 1, true, "iron_ingot", 7);
		craft("iron_boots", 1, true, "iron_ingot", 4);
		craft("diamond_pickaxe", 1, true, "diamond", 3, "stick", 2);
		craft("diamond_sword", 1, true, "diamond", 2, "stick", 1);
		craft("flint_and_steel", 1, false, "iron_ingot", 1, "flint", 1);
		craft("bow", 1, true, "stick", 3, "string", 3);
		craft("arrow", 4, true, "flint", 1, "stick", 1, "feather", 1);
		// Any bed counts; the recipe book picks the color that matches the wool we have.
		craft("bed", 1, true, "wool", 3, "planks", 3);
		craft("blaze_powder", 2, false, "blaze_rod", 1);
		craft("ender_eye", 1, false, "blaze_powder", 1, "ender_pearl", 1);
		// In the Nether, ingots come from nuggets (the planner only uses this there; the
		// overworld smelts raw gold).
		craft("gold_ingot", 1, true, "gold_nugget", 9);
		craft("golden_helmet", 1, true, "gold_ingot", 5);

		SMELT.put("iron_ingot", "raw_iron");
		SMELT.put("gold_ingot", "raw_gold");
		SMELT.put("charcoal", "log");
		SMELT.put("cooked_beef", "beef");
		SMELT.put("cooked_porkchop", "porkchop");
		SMELT.put("cooked_mutton", "mutton");
		SMELT.put("cooked_chicken", "chicken");
		SMELT.put("cooked_rabbit", "rabbit");
		SMELT.put("cooked_cod", "cod");
		SMELT.put("cooked_salmon", "salmon");

		MINE.put("log", new Source(List.of("*_log"), -1, null));
		MINE.put("stone", new Source(List.of("stone", "cobblestone", "deepslate", "cobbled_deepslate"), 0, null));
		MINE.put("coal", new Source(List.of("coal_ore", "deepslate_coal_ore"), 0, 45));
		MINE.put("raw_iron", new Source(List.of("iron_ore", "deepslate_iron_ore"), 1, 16));
		MINE.put("diamond", new Source(List.of("diamond_ore", "deepslate_diamond_ore"), 2, -58));
		MINE.put("raw_gold", new Source(List.of("gold_ore", "deepslate_gold_ore"), 2, -16));
		// Nether gold ore drops nuggets (2-6), not raw gold, and any pickaxe mines it. Legit mining
		// at y 45: ores in sight first, else branch-mining above the lava sea.
		MINE.put("gold_nugget", new Source(List.of("nether_gold_ore"), 0, 45));
		MINE.put("flint", new Source(List.of("gravel"), -1, null));
		MINE.put("obsidian", new Source(List.of("obsidian"), 3, null));
		MINE.put("dirt", new Source(List.of("dirt", "grass_block"), -1, null));
		MINE.put("sand", new Source(List.of("sand"), -1, null));

		MOB.put("beef", List.of("cow"));
		MOB.put("porkchop", List.of("pig"));
		MOB.put("mutton", List.of("sheep"));
		MOB.put("chicken", List.of("chicken"));
		MOB.put("wool", List.of("sheep"));
		MOB.put("feather", List.of("chicken"));
		MOB.put("string", List.of("spider"));
		MOB.put("blaze_rod", List.of("blaze"));
		MOB.put("ender_pearl", List.of("enderman"));
		// Raw fish and rabbit are in Items2.RAW_MEAT and get cooked, so the tree has to know where
		// they come from: without these, planning for cooked_cod/rabbit/salmon fell through to
		// "explore any" forever.
		MOB.put("rabbit", List.of("rabbit"));
		MOB.put("cod", List.of("cod"));
		MOB.put("salmon", List.of("salmon"));
		MOB.put("meat", List.of("cow", "pig", "sheep", "chicken"));
	}

	public static String pickaxeForTier(int tier) {
		return switch (tier) {
			case 0 -> "wooden_pickaxe";
			case 1 -> "stone_pickaxe";
			case 2 -> "iron_pickaxe";
			default -> "diamond_pickaxe";
		};
	}
}
