package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the current goal into a short list of concrete options. The first option is what the
 * rules would do (the mock brain picks it); LLM brains may pick any option on the list.
 */
public final class Planner {
	private final WorldMemory memory;

	public Planner(WorldMemory memory) {
		this.memory = memory;
	}

	public List<Option> options(Goal goal, Perception seen) {
		Map<String, Option> out = new LinkedHashMap<>();
		Option main = goalStep(goal, seen, 0);
		// Safety first when it matters; these go ahead of the goal step.
		for (Option o : urgent(seen)) out.putIfAbsent(o.label(), o);
		if (main != null) out.putIfAbsent(main.label(), main);
		for (Option o : extras(seen)) out.putIfAbsent(o.label(), o);
		List<Option> list = new ArrayList<>(out.values());
		return list.size() > 12 ? list.subList(0, 12) : list;
	}

	/** True if the goal is finished (the strategist should move on). */
	public boolean goalDone(Goal goal) {
		String dim = Mc.dimension();
		return switch (goal) {
			case NETHER_PORTAL -> dim.equals("the_nether") || memory.nearest("nether_portal") != null && Goal.have("obsidian") < 10;
			case IRON_ARMOR -> wornIronCount() == 4;
			case FIND_STRONGHOLD -> memory.nearest("end_portal_frame") != null || dim.equals("the_end");
			case ENTER_END -> dim.equals("the_end");
			case KILL_DRAGON, SURVIVE_NIGHT, EXPLORE -> false;
			default -> goal.itemsDone();
		};
	}

	private Option goalStep(Goal goal, Perception seen, int depth) {
		String dim = Mc.dimension();
		switch (goal) {
			case FOOD -> {
				for (String raw : Items2.RAW_MEAT) {
					if (Mc.count(raw) > 0) {
						Option o = smeltStep("cooked_" + (raw.equals("beef") ? "beef" : raw), Mc.count(raw), 0);
						if (o != null) return o;
					}
				}
				for (String animal : List.of("cow", "pig", "sheep", "chicken")) {
					if (seen.nearest(animal) != null) return new Option("attack", animal, "hunt for meat");
				}
				return new Option("explore", "random", "look for animals to hunt");
			}
			case IRON_ARMOR -> {
				Option o = itemsStep(goal);
				if (o != null) return o;
				return new Option("equip", "armor", "wear the iron armor");
			}
			case NETHER_PORTAL -> {
				if (dim.equals("the_nether")) return null;
				if (memory.nearest("nether_portal") != null) return new Option("enter_portal", "nether", "walk into the nether portal");
				Option o = itemsStep(goal);
				if (o != null) return o;
				return new Option("build_portal", null, "build and light the nether portal");
			}
			case BLAZE_RODS -> {
				if (!dim.equals("the_nether")) return depth > 2 ? null : goalStep(Goal.NETHER_PORTAL, seen, depth + 1);
				if (seen.nearest("blaze") != null) return new Option("attack", "blaze", "kill the blaze for rods");
				if (memory.nearest("nether_bricks") != null) return new Option("goto", "nether_bricks", "go into the fortress to find blazes");
				return new Option("explore", "random", "look for a nether fortress");
			}
			case ENDER_PEARLS -> {
				if (seen.nearest("enderman") != null) return new Option("attack", "enderman", "kill the enderman for a pearl");
				return new Option("explore", "random", "look for endermen (more at night)");
			}
			case FIND_STRONGHOLD -> {
				if (dim.equals("the_nether") && memory.nearest("nether_portal") != null) return new Option("enter_portal", "overworld", "go back to the overworld");
				if (memory.nearest("end_portal_frame") != null) return null;
				if (Mc.count("ender_eye") == 0) return itemStep("ender_eye", 1, 0);
				return new Option("locate_stronghold", null, "throw an eye of ender and follow it");
			}
			case ENTER_END -> {
				if (dim.equals("the_end")) return null;
				if (memory.nearest("end_portal") != null) return new Option("enter_portal", "end", "jump into the end portal");
				if (memory.nearest("end_portal_frame") != null) {
					if (Mc.count("ender_eye") == 0) return itemStep("ender_eye", 1, 0);
					return new Option("fill_end_portal", null, "put eyes of ender in the empty frames");
				}
				return depth > 2 ? null : goalStep(Goal.FIND_STRONGHOLD, seen, depth + 1);
			}
			case KILL_DRAGON -> {
				if (!dim.equals("the_end")) return depth > 2 ? null : goalStep(Goal.ENTER_END, seen, depth + 1);
				boolean bow = Mc.count("bow") > 0 && Mc.count("arrow") > 0;
				if (bow && seen.nearest("end_crystal") != null) return new Option("shoot", "end_crystal", "crystals heal the dragon; destroy them first");
				Perception.Seen dragon = seen.nearest("ender_dragon");
				if (dragon != null && dragon.dist() < 6) return new Option("attack", "ender_dragon", "hit the dragon while it is close");
				if (bow && dragon != null) return new Option("shoot", "ender_dragon", "shoot the dragon");
				return new Option("goto", "end_center", "wait near the portal for the dragon to perch");
			}
			case SURVIVE_NIGHT -> {
				if (Mc.count("bed") > 0 || memory.nearestStation("bed") != null) return new Option("sleep", null, "sleep through the night");
				return new Option("shelter", null, "dig a small shelter and wait for morning");
			}
			case EXPLORE -> {
				return new Option("explore", "random", "explore new ground");
			}
			default -> {
				return itemsStep(goal);
			}
		}
	}

	/** First missing need of an item goal, resolved down to an action. */
	private Option itemsStep(Goal goal) {
		for (var e : goal.needs.entrySet()) {
			if (Goal.have(e.getKey()) >= e.getValue()) continue;
			Option o = itemStep(e.getKey(), e.getValue(), 0);
			if (o != null) return o;
		}
		return null;
	}

	/**
	 * What to do next to own `count` of `item`. Walks the tech tree: craft if ingredients exist,
	 * otherwise get the first missing ingredient; smelt, mine (with the right pickaxe) or hunt.
	 */
	public Option itemStep(String item, int count, int depth) {
		if (depth > 10) return null;
		int have = Goal.have(item);
		if (have >= count) return null;
		int missing = count - have;

		if (item.equals("food")) return goalStep(Goal.FOOD, Perception.look(24), 0);

		TechTree.Recipe r = TechTree.CRAFT.get(item);
		if (r != null) {
			int crafts = (missing + r.yield() - 1) / r.yield();
			for (var ing : r.in().entrySet()) {
				// Own current stock plus what this craft needs, so we don't spend planks meant for sticks.
				int need = ing.getValue() * crafts;
				if (Mc.count(ing.getKey()) < need) {
					Option o = itemStep(ing.getKey(), need, depth + 1);
					if (o != null) return o;
				}
			}
			if (r.table() && !tableAvailable()) {
				Option o = itemStep("crafting_table", 1, depth + 1);
				if (o != null) return o;
			}
			return new Option("craft", item + ":" + crafts * r.yield(), "have the ingredients for " + item);
		}

		if (TechTree.SMELT.containsKey(item)) return smeltStep(item, missing, depth);

		TechTree.Source src = TechTree.MINE.get(item);
		if (src != null) {
			if (Items2.bestTier("pickaxe") < src.tier()) {
				return itemStep(TechTree.pickaxeForTier(src.tier()), 1, depth + 1);
			}
			if (item.equals("obsidian") && memory.nearest("obsidian") == null) {
				return new Option("explore", "random", "find obsidian (lava pools touched by water, ruined portals)");
			}
			// Mine a little extra: each trip costs time.
			int want = Math.min(64, missing + (item.equals("log") ? 2 : 0));
			return new Option("collect", item + ":" + want, "need " + missing + " more " + item);
		}

		List<String> mobs = TechTree.MOB.get(item);
		if (mobs != null) {
			Perception seen = Perception.look(24);
			for (String m : mobs) if (seen.nearest(m) != null) return new Option("attack", m, "drops " + item);
			return new Option("explore", "random", "find a " + mobs.get(0) + " for " + item);
		}
		return new Option("explore", "random", "find " + item);
	}

	private Option smeltStep(String item, int missing, int depth) {
		String input = TechTree.SMELT.get(item);
		if (input == null) return null;
		if (Mc.count(input) < missing) {
			Option o = itemStep(input, missing, depth + 1);
			if (o != null) return o;
		}
		// Fuel: coal smelts 8 items, planks 1.5. Use whatever we have, else make planks.
		int coalNeed = (missing + 7) / 8;
		int planksNeed = (missing * 2 + 2) / 3;
		boolean fuelOk = Mc.count("coal") >= coalNeed || Mc.count("planks") >= planksNeed
				|| (!input.equals("log") && Mc.count("log") >= coalNeed * 2);
		if (!fuelOk) {
			Option o = itemStep("planks", planksNeed, depth + 1);
			if (o != null) return o;
		}
		if (memory.nearestStation("furnace") == null && Mc.count("furnace") == 0) {
			Option o = itemStep("furnace", 1, depth + 1);
			if (o != null) return o;
		}
		return new Option("smelt", item + ":" + missing, "smelt " + input + " into " + item);
	}

	private boolean tableAvailable() {
		return Mc.count("crafting_table") > 0 || memory.nearestStation("crafting_table") != null;
	}

	private int wornIronCount() {
		int n = 0;
		for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack st = Mc.player().getItemBySlot(s);
			if (!st.isEmpty() && Items2.tier(Items2.id(st)) >= 2) n++;
		}
		return n;
	}

	/** Life-or-death options that should be considered before the goal. */
	private List<Option> urgent(Perception seen) {
		List<Option> out = new ArrayList<>();
		LocalPlayer pl = Mc.player();
		int food = pl.getFoodData().getFoodLevel();
		boolean hasFood = Mc.count(Items2::isAnyFood) > 0;
		Perception.Seen hostile = seen.nearestHostile();
		if (hostile != null && hostile.dist() < 10) {
			// Creepers explode in melee range: back off instead of swinging at them.
			if (hostile.type().equals("creeper")) out.add(new Option("retreat", null, "a creeper is " + Math.round(hostile.dist()) + " blocks away"));
			else if (pl.getHealth() <= 8) out.add(new Option("retreat", null, "low health and a " + hostile.type() + " is close"));
			else out.add(new Option("attack", hostile.type(), hostile.type() + " is " + Math.round(hostile.dist()) + " blocks away"));
		}
		if (food <= 14 && hasFood) out.add(new Option("eat", null, "hunger " + food + "/20"));
		if (Mc.dimension().equals("overworld") && Mc.isNight() && seen.hostilesWithin(16) > 0) {
			if (Mc.count("bed") > 0) out.add(new Option("sleep", null, "night with monsters around"));
			else out.add(new Option("shelter", null, "night with monsters around"));
		}
		return out;
	}

	/** Always-available, sometimes-useful options. */
	private List<Option> extras(Perception seen) {
		List<Option> out = new ArrayList<>();
		if (betterArmorInInventory()) out.add(new Option("equip", "armor", "armor in the bag that isn't worn"));
		if (Mc.count("shield") > 0 && !Items2.id(Mc.player().getOffhandItem()).equals("shield"))
			out.add(new Option("equip", "shield", "hold the shield in the off hand"));
		if (!seen.items.isEmpty()) out.add(new Option("pickup", null, seen.items.size() + " dropped items nearby"));
		if (Mc.player().getFoodData().getFoodLevel() < 20 && Mc.count(Items2::isAnyFood) > 0) out.add(new Option("eat", null, "top up hunger"));
		if (Mc.isNight() && Mc.dimension().equals("overworld")) {
			if (Mc.count("bed") > 0 || memory.nearestStation("bed") != null) out.add(new Option("sleep", null, "skip the night"));
			else out.add(new Option("shelter", null, "wait out the night safely"));
		}
		out.add(new Option("explore", "random", "look around"));
		out.add(new Option("explore", "north", "look around to the north"));
		out.add(new Option("explore", "south", "look around to the south"));
		out.add(new Option("idle", null, "stand still"));
		return out;
	}

	private boolean betterArmorInInventory() {
		for (String kind : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
			EquipmentSlot slot = switch (kind) {
				case "helmet" -> EquipmentSlot.HEAD;
				case "chestplate" -> EquipmentSlot.CHEST;
				case "leggings" -> EquipmentSlot.LEGS;
				default -> EquipmentSlot.FEET;
			};
			ItemStack worn = Mc.player().getItemBySlot(slot);
			int wornTier = worn.isEmpty() ? -2 : armorTier(Items2.id(worn));
			int best = -2;
			var inv = Mc.player().getInventory();
			for (int i = 0; i < 36; i++) {
				String id = Items2.id(inv.getItem(i));
				if (id.endsWith("_" + kind)) best = Math.max(best, armorTier(id));
			}
			if (best > wornTier) return true;
		}
		return false;
	}

	public static int armorTier(String id) {
		if (id.startsWith("leather_")) return 0;
		if (id.startsWith("golden_") || id.startsWith("chainmail_")) return 1;
		if (id.startsWith("iron_")) return 2;
		if (id.startsWith("diamond_")) return 3;
		if (id.startsWith("netherite_")) return 4;
		if (id.equals("turtle_helmet")) return 2;
		return -1;
	}
}
