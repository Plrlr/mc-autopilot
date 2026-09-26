package io.github.plrlr.autopilot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Item groups the planner and brains talk about ("log" means any log, "planks" any planks, ...),
 * and simple tool-tier rules. Named Items2 so it doesn't clash with Minecraft's Items class.
 */
public final class Items2 {
	private Items2() {}

	/** Foods worth eating. Raw chicken, rotten flesh and spider eyes can poison, so they're last resorts. */
	private static final Set<String> BAD_FOOD = Set.of("rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish",
			"chicken", "suspicious_stew");

	public static final Set<String> THROWAWAY = Set.of("dirt", "cobblestone", "cobbled_deepslate", "netherrack",
			"stone", "andesite", "diorite", "granite", "tuff", "blackstone");

	public static String id(ItemStack s) {
		return Mc.id(s.getItem());
	}

	public static Predicate<ItemStack> matcher(String name) {
		return switch (name) {
			case "log" -> s -> { String i = id(s); return i.endsWith("_log") || i.endsWith("_stem"); };
			case "planks" -> s -> id(s).endsWith("_planks");
			case "wool" -> s -> id(s).endsWith("_wool");
			case "bed" -> s -> id(s).endsWith("_bed");
			case "stone" -> s -> { String i = id(s); return i.equals("cobblestone") || i.equals("cobbled_deepslate") || i.equals("blackstone"); };
			case "coal" -> s -> { String i = id(s); return i.equals("coal") || i.equals("charcoal"); };
			case "fuel" -> s -> isFuel(id(s));
			case "food" -> s -> isGoodFood(s) && !RAW_MEAT.contains(id(s)); // ready to eat, not raw
			case "meat" -> s -> RAW_MEAT.contains(id(s));
			case "throwaway" -> s -> THROWAWAY.contains(id(s));
			case "pickaxe", "axe", "sword", "shovel", "hoe" -> s -> id(s).endsWith("_" + name);
			default -> s -> id(s).equals(name);
		};
	}

	/**
	 * The biggest stack total of one item in a colored group ("wool" -> most white or most red
	 * wool). A bed needs three wool of the same color.
	 */
	public static int mostOfOneColor(String suffix) {
		java.util.Map<String, Integer> per = new java.util.HashMap<>();
		for (ItemStack s : Mc.player().getInventory().getNonEquipmentItems()) {
			if (!s.isEmpty() && id(s).endsWith("_" + suffix)) per.merge(id(s), s.getCount(), Integer::sum);
		}
		return per.values().stream().max(Integer::compare).orElse(0);
	}

	public static final Set<String> RAW_MEAT = Set.of("beef", "porkchop", "mutton", "chicken", "rabbit", "cod", "salmon");

	public static boolean isFuel(String i) {
		return i.equals("coal") || i.equals("charcoal") || i.endsWith("_planks") || i.endsWith("_log") || i.equals("stick")
				|| i.equals("coal_block") || i.equals("lava_bucket");
	}

	public static FoodProperties food(ItemStack s) {
		return s.get(DataComponents.FOOD);
	}

	public static boolean isGoodFood(ItemStack s) {
		return food(s) != null && !BAD_FOOD.contains(id(s));
	}

	public static boolean isAnyFood(ItemStack s) {
		return food(s) != null && !id(s).equals("pufferfish") && !id(s).equals("poisonous_potato");
	}

	/** 0 none/wood/gold, 1 stone, 2 iron, 3 diamond, 4 netherite. Matches vanilla mining tiers. */
	public static int tier(String itemId) {
		if (itemId.startsWith("netherite_")) return 4;
		if (itemId.startsWith("diamond_")) return 3;
		if (itemId.startsWith("iron_")) return 2;
		if (itemId.startsWith("stone_")) return 1;
		if (itemId.startsWith("wooden_") || itemId.startsWith("golden_")) return 0;
		return -1;
	}

	/** Best tier of a tool type ("pickaxe", "sword", "axe") in the inventory, -1 if none. */
	public static int bestTier(String toolType) {
		int best = -1;
		var inv = Mc.player().getInventory();
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			String id = id(s);
			if (!s.isEmpty() && id.endsWith("_" + toolType)) best = Math.max(best, tier(id));
		}
		ItemStack off = Mc.player().getOffhandItem();
		if (!off.isEmpty() && id(off).endsWith("_" + toolType)) best = Math.max(best, tier(id(off)));
		return best;
	}
}
