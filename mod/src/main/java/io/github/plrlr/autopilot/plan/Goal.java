package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * The milestone ladder toward beating the game. The strategist (Opus or rules) picks one of these;
 * the planner turns it into concrete skill options.
 */
public enum Goal {
	WOOD_TOOLS(1, "Get a crafting table and a wooden pickaxe", needs("wooden_pickaxe", 1)),
	STONE_TOOLS(2, "Get a stone pickaxe and a stone sword", needs("stone_pickaxe", 1, "stone_sword", 1)),
	FOOD(3, "Hunt animals and cook at least 5 food; survive the night", needs("food", 5)),
	IRON_TOOLS(4, "Mine and smelt iron: iron pickaxe, iron sword, shield, bucket", needs("iron_pickaxe", 1, "shield", 1, "bucket", 1, "iron_sword", 1)),
	IRON_ARMOR(5, "Craft and wear full iron armor", needs("iron_helmet", 1, "iron_chestplate", 1, "iron_leggings", 1, "iron_boots", 1)),
	DIAMONDS(6, "Mine diamonds deep underground and make a diamond pickaxe", needs("diamond_pickaxe", 1)),
	NETHER_PORTAL(7, "Build and light a nether portal and enter the Nether: cast it from a lava pool with a water bucket and a second bucket (no diamonds needed), or place 10 mined obsidian", needs("obsidian", 10, "flint_and_steel", 1)),
	BLAZE_RODS(8, "In the Nether, find a fortress and kill blazes for 6 blaze rods", needs("blaze_rod", 7)),
	// 14 eyes: 2-3 to triangulate the stronghold, 12 in case no frame has an eye yet.
	ENDER_PEARLS(9, "Get 14 ender pearls: trade gold with piglins, kill endermen", needs("ender_pearl", 14)),
	EYES_OF_ENDER(10, "Craft 14 eyes of ender (blaze powder + ender pearl)", needs("ender_eye", 14)),
	FIND_STRONGHOLD(11, "Throw eyes of ender to find the stronghold and its end portal room", needs()),
	ENTER_END(12, "Put eyes of ender in every end portal frame and jump into the portal", needs()),
	KILL_DRAGON(13, "Shoot the end crystals, then kill the Ender Dragon", needs()),
	SURVIVE_NIGHT(0, "Get safe for the night: sleep in a bed or dig a shelter", needs()),
	EXPLORE(0, "Explore to find trees, animals, ores or structures", needs());

	/**
	 * Needs in a fixed order: the planner works through them first to last. Map.of iterates in a
	 * different order on every game start, which made the iron-tools order (and so run times)
	 * vary between otherwise identical trials. The shield comes right after the pickaxe.
	 */
	private static Map<String, Integer> needs(Object... kv) {
		Map<String, Integer> m = new java.util.LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
		return java.util.Collections.unmodifiableMap(m);
	}

	public final int milestone;
	public final String description;
	public final Map<String, Integer> needs;

	Goal(int milestone, String description, Map<String, Integer> needs) {
		this.milestone = milestone;
		this.description = description;
		this.needs = needs;
	}

	public String key() {
		return name().toLowerCase();
	}

	public static Goal byKey(String key) {
		for (Goal g : values()) if (g.key().equalsIgnoreCase(key)) return g;
		return null;
	}

	/** Items already turned into something later on the ladder still count. */
	public static int have(String item) {
		return switch (item) {
			case "wooden_pickaxe" -> Items2.bestTier("pickaxe") >= 0 ? 1 : 0;
			case "stone_pickaxe" -> Items2.bestTier("pickaxe") >= 1 ? 1 : 0;
			case "iron_pickaxe" -> Items2.bestTier("pickaxe") >= 2 ? 1 : 0;
			case "diamond_pickaxe" -> Items2.bestTier("pickaxe") >= 3 ? 1 : 0;
			case "stone_sword" -> Items2.bestTier("sword") >= 1 ? 1 : 0;
			case "iron_sword" -> Items2.bestTier("sword") >= 2 ? 1 : 0;
			case "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots" -> hasArmor(item) ? 1 : 0;
			case "blaze_rod" -> Mc.count("blaze_rod") + (Mc.count("blaze_powder") + Mc.count("ender_eye")) / 2;
			case "ender_pearl" -> Mc.count("ender_pearl") + Mc.count("ender_eye");
			case "food" -> Mc.count("food");
			// Obsidian already set into our half-built portal frame still counts.
			case "obsidian" -> Mc.count("obsidian") + io.github.plrlr.autopilot.skills.PortalSkills.placedFrameObsidian();
			// A filled bucket is still our bucket.
			case "bucket" -> Mc.count("bucket") + Mc.count("water_bucket") + Mc.count("lava_bucket");
			default -> Mc.count(item);
		};
	}

	/** Worn or carried, iron or better, in the right slot type. */
	public static boolean hasArmor(String ironPiece) {
		String kind = ironPiece.substring("iron_".length());
		EquipmentSlot slot = switch (kind) {
			case "helmet" -> EquipmentSlot.HEAD;
			case "chestplate" -> EquipmentSlot.CHEST;
			case "leggings" -> EquipmentSlot.LEGS;
			default -> EquipmentSlot.FEET;
		};
		ItemStack worn = Mc.player().getItemBySlot(slot);
		if (!worn.isEmpty() && Items2.tier(Items2.id(worn)) >= 2) return true;
		return Mc.count(s -> Items2.id(s).endsWith("_" + kind) && Items2.tier(Items2.id(s)) >= 2) > 0;
	}

	public boolean itemsDone() {
		for (var e : needs.entrySet()) if (have(e.getKey()) < e.getValue()) return false;
		return true;
	}

	/**
	 * Tool and armor rungs stay done once reached, even after a death loses the items: the
	 * planner rebuilds any missing tool on the way to the next rung, so dropping back down the
	 * ladder only wastes time. Later rungs depend on where you are, so they are checked live.
	 */
	public boolean sticky() {
		// 8-10 too: rods become powder, pearls become eyes, and eyes get thrown on purpose while
		// finding the stronghold (the stronghold test went back to "craft 14 eyes" at 12). If they
		// run out, the stronghold step makes more itself.
		return milestone >= 1 && milestone <= 6 || milestone >= 8 && milestone <= 10;
	}

	/**
	 * Rungs a fast run skips: the portal is cast from lava without a diamond pickaxe, full iron
	 * armor takes 24 more iron, and food is gathered when hunger calls for it (upkeep) instead
	 * of stocking up first, which cost 3-9 minutes in trials. Reaching them still counts.
	 */
	public boolean optional() {
		return this == FOOD || this == IRON_ARMOR || this == DIAMONDS;
	}

	public static List<Goal> ladder() {
		return List.of(WOOD_TOOLS, STONE_TOOLS, FOOD, IRON_TOOLS, IRON_ARMOR, DIAMONDS, NETHER_PORTAL, BLAZE_RODS,
				ENDER_PEARLS, EYES_OF_ENDER, FIND_STRONGHOLD, ENTER_END, KILL_DRAGON);
	}
}
