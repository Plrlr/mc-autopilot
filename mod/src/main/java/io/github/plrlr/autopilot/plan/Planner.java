package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.skills.CastPortal;
import io.github.plrlr.autopilot.skills.SmeltSkill;
import io.github.plrlr.autopilot.skills.Station;
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

	/** Animals worth hunting for food, best first. */
	public static final String ANIMALS = "cow,pig,sheep,chicken";

	/**
	 * Options in priority order: survival, getting dropped items back, cheap upkeep that a good
	 * player does on the way (food, a bed, coal), then the goal's next step, then fallbacks.
	 */
	public List<Option> options(Goal goal, Perception seen) {
		Map<String, Option> out = new LinkedHashMap<>();
		for (Option o : urgent(seen)) out.putIfAbsent(o.label(), o);
		Option recover = recoverStep();
		if (recover != null) out.putIfAbsent(recover.label(), recover);
		Option main = goalStep(goal, seen, 0);
		// Lost in a cave (a minute down here with nothing gained), or the next step needs the
		// surface (trees, animals, walking to explore): go back up the way we came first.
		boolean deep = !onSurface() && Mc.dimension().equals("overworld") && memory.surfaceEntry() != null
				&& memory.surfaceEntry().getY() - Mc.player().getBlockY() > 8;
		if (lostUnderground || (deep && needsSurface(main)))
			out.putIfAbsent("goto surface", new Option("goto", "surface",
					lostUnderground ? "a minute underground without progress: back up the way we came" : "the next step is on the surface"));
		for (Option o : upkeep(seen, main)) out.putIfAbsent(o.label(), o);
		if (main != null) out.putIfAbsent(main.label(), main);
		for (Option o : extras(seen, main)) out.putIfAbsent(o.label(), o);
		List<Option> list = new ArrayList<>(out.values());
		return list.size() > 10 ? list.subList(0, 10) : list;
	}

	/** Set by the main loop: underground a while with nothing gained (see Autopilot.cave). */
	public boolean lostUnderground;

	/** Steps that only work on the surface: walking to explore, trees, animals. */
	private static boolean needsSurface(Option o) {
		if (o == null) return false;
		String a = o.arg() == null ? "" : o.arg();
		return o.skill().equals("explore") || (o.skill().equals("collect") && (a.startsWith("log") || a.startsWith("sand")))
				|| (o.skill().equals("attack") && ANIMALS.contains(a.split(",")[0]));
	}

	/** The goal's own next step, without upkeep or safety options (null if the goal needs nothing now). */
	public Option mainStep(Goal goal, Perception seen) {
		return goalStep(goal, seen, 0);
	}

	/** True if the goal is finished (the strategist should move on). */
	public boolean goalDone(Goal goal) {
		String dim = Mc.dimension();
		return switch (goal) {
			case NETHER_PORTAL -> dim.equals("the_nether") || memory.nearest("nether_portal") != null && Goal.have("obsidian") < 10;
			case IRON_ARMOR -> wornIronCount() == 4;
			case FIND_STRONGHOLD -> memory.nearest("end_portal_frame") != null || dim.equals("the_end");
			case ENTER_END -> dim.equals("the_end");
			case SURVIVE_NIGHT -> !Mc.dimension().equals("overworld") || !Mc.isNight();
			case KILL_DRAGON, EXPLORE -> false;
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
				for (String animal : ANIMALS.split(",")) {
					if (seen.nearest(animal) != null) return new Option("attack", animal, "hunt for meat");
				}
				return new Option("explore", ANIMALS, "look for animals to hunt");
			}
			case IRON_ARMOR -> {
				Option o = itemsStep(goal);
				if (o != null) return o;
				return new Option("equip", "armor", "wear the iron armor");
			}
			case NETHER_PORTAL -> {
				if (dim.equals("the_nether")) return null;
				if (memory.nearest("nether_portal") != null) return new Option("enter_portal", "nether", "walk into the nether portal");
				// Survive first (outside review #2, A2): deaths drop the buckets and iron the portal needs,
				// and 2-3 deaths per run was the norm. Chestplate and helmet (13 iron) before the portal,
				// boots too when the ingots are already there.
				for (String piece : new String[]{"iron_chestplate", "iron_helmet"}) {
					if (!Goal.hasArmor(piece)) {
						Option o = itemStep(piece, 1, depth + 1);
						if (o != null) return o;
					}
				}
				if (!Goal.hasArmor("iron_boots") && Mc.count("iron_ingot") >= 4) {
					Option o = itemStep("iron_boots", 1, depth + 1);
					if (o != null) return o;
				}
				// Speedrun route: without a diamond pickaxe, cast the frame from lava and water.
				if (Goal.have("obsidian") < 10 && Items2.bestTier("pickaxe") < 3) return castStep(depth);
				Option o = itemsStep(goal);
				if (o != null) return o;
				// The frame corners can be any block; 10 obsidian covers the rest.
				if (Mc.count("throwaway") + Mc.count("planks") < 4) {
					Option c = itemStep("stone", Mc.count("stone") + 4, depth + 1);
					if (c != null) return c;
				}
				return new Option("build_portal", null, "build and light the nether portal");
			}
			case BLAZE_RODS -> {
				if (!dim.equals("the_nether")) return depth > 2 ? null : goalStep(Goal.NETHER_PORTAL, seen, depth + 1);
				return NetherPlan.blazeStep(memory, seen);
			}
			case ENDER_PEARLS -> {
				if (seen.nearest("enderman") != null) return new Option("attack", "enderman", "kill the enderman for a pearl");
				return new Option("explore", "enderman", "look for endermen (more at night, many in warped forests)");
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
				return new Option("explore", "any", "explore new ground");
			}
			default -> {
				return itemsStep(goal);
			}
		}
	}

	/**
	 * What the cast portal needs: flint and steel, two buckets (one full of water), blocks for
	 * the wall behind the frame, and a lava pool to cast from.
	 */
	private Option castStep(int depth) {
		if (Goal.have("flint_and_steel") == 0) {
			Option o = itemStep("flint_and_steel", 1, depth + 1);
			if (o != null) return o;
		}
		if (Goal.have("bucket") < 2) {
			Option o = itemStep("bucket", 2, depth + 1);
			if (o != null) return o;
		}
		if (Mc.count("water_bucket") == 0) {
			Option o = itemStep("water_bucket", 1, depth + 1);
			if (o != null) return o;
		}
		int blocks = Mc.count("throwaway");
		if (blocks < CastPortal.BLOCKS_NEEDED) {
			Option o = itemStep("stone", Mc.count("stone") + CastPortal.BLOCKS_NEEDED - blocks, depth + 1);
			if (o != null) return o;
		}
		if (memory.nearest("lava") == null && Mc.count("lava_bucket") == 0)
			return new Option("explore", "lava", "find a lava pool to cast the portal from");
		return new Option("build_portal", null, "cast a nether portal from lava and water (no diamonds needed)");
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

		if (item.equals("food")) return goalStep(Goal.FOOD, Perception.look(32), 0);
		if (item.equals("water_bucket")) {
			if (Goal.have("bucket") == 0) return itemStep("bucket", 1, depth + 1);
			if (memory.nearest("water") != null) return new Option("fill_bucket", "water", "fill the bucket with water");
			return new Option("explore", "water", "find water to fill the bucket");
		}

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
				// Make it like a player: water bucket poured beside a lava pool hardens the lava.
				if (Mc.count("water_bucket") == 0) {
					Option o = itemStep("water_bucket", 1, depth + 1);
					if (o != null) return o;
				}
				if (memory.nearest("lava") != null) return new Option("make_obsidian", "obsidian:" + missing, "harden lava with water into obsidian");
				return new Option("explore", "lava,obsidian", "find a lava pool to make obsidian from");
			}
			// Mine a little extra: each trip costs time. Spare cobblestone also covers a
			// night shelter and the blocks Baritone places when it bridges or pillars.
			int extra = switch (item) {
				// Wood runs out at awkward times (deep in a mine); one trip for plenty is faster. But
				// not the very first trip: 9 logs before the first table and pickaxe put the crafting
				// table 15-30 s later on the same seeds (batches 3-6 vs 1-2).
				case "log" -> Items2.bestTier("pickaxe") < 0 ? 2 : Mc.count("log") < 8 ? 8 : 3;
				// All the iron the run needs in one trip down, not 2-3 at a time with a furnace each.
				case "raw_iron" -> Math.max(0, ironStillNeeded() - Mc.count("iron_ingot") - have - missing);
				case "stone" -> Mc.count("stone") < 24 ? 10 : 0;
				case "coal" -> 4;
				default -> 0;
			};
			int want = Math.min(64, missing + extra);
			return new Option("collect", item + ":" + want, "need " + missing + " more " + item);
		}

		List<String> mobs = TechTree.MOB.get(item);
		if (mobs != null) {
			Perception seen = Perception.look(32);
			for (String m : mobs) if (seen.nearest(m) != null) return new Option("attack", m, "drops " + item);
			return new Option("explore", String.join(",", mobs), "find a " + mobs.get(0) + " for " + item);
		}
		return new Option("explore", "any", "find " + item);
	}

	private Option smeltStep(String item, int missing, int depth) {
		String input = TechTree.SMELT.get(item);
		if (input == null) return null;
		// A load of ours is cooking: do useful work nearby until it's done, then collect it.
		SmeltSkill.Job job = SmeltSkill.job(item);
		if (job != null && Mc.count(input) == 0) {
			if (!job.ready()) {
				Option side = sideWork();
				if (side != null) return new Option(side.skill(), side.arg(), side.why() + " while the furnace works");
			}
			return new Option("smelt", item + ":" + job.count(), "collect the " + item + " from the furnace");
		}
		// Smelt every raw iron we carry at once: one furnace load instead of several.
		if (input.equals("raw_iron")) missing = Math.max(missing, Mc.count("raw_iron"));
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

	/**
	 * Work worth doing near the mine while a furnace load cooks, all needed later on the route:
	 * coal for fuel and torches, blocks for the portal-casting wall, gravel for flint.
	 */
	private Option sideWork() {
		if (Items2.bestTier("pickaxe") < 0) return null;
		// Only coal already in view: branch-mining for it would climb toward y 45, away from the furnace.
		WorldMemory.Seen coal = memory.nearest("coal_ore");
		if (Mc.count("coal") < 8 && coal != null && coal.pos().distSqr(Mc.player().blockPosition()) < 16 * 16)
			return new Option("collect", "coal:" + (8 - Mc.count("coal")), "coal for fuel");
		if (Mc.count("throwaway") < CastPortal.BLOCKS_NEEDED)
			// collect's count is how many more to get, not a total.
			return new Option("collect", "stone:" + (CastPortal.BLOCKS_NEEDED - Mc.count("throwaway")), "blocks for the portal wall");
		if (Mc.count("flint_and_steel") == 0 && Mc.count("flint") == 0) return new Option("collect", "flint:1", "flint for flint and steel");
		return null;
	}

	/**
	 * Iron ingots the fast route still needs: iron pickaxe 3, iron sword 2, shield 1, two
	 * buckets 3 each (water, and lava to cast the portal), flint and steel 1.
	 */
	public static int ironStillNeeded() {
		int n = 0;
		if (Items2.bestTier("pickaxe") < 2) n += 3;
		if (Items2.bestTier("sword") < 2) n += 2;
		if (Mc.count("shield") == 0) n += 1;
		n += 3 * Math.max(0, 2 - Goal.have("bucket"));
		if (Mc.count("flint_and_steel") == 0) n += 1;
		if (!Goal.hasArmor("iron_chestplate")) n += 8;
		if (!Goal.hasArmor("iron_helmet")) n += 5;
		// Iron already in a furnace we left cooking is on its way.
		return Math.max(0, n - SmeltSkill.pending("iron_ingot"));
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
		Perception.Seen hostile = seen.nearestHostile();
		// A monster an attack just failed to reach: offering to attack it again only fails again
		// (10 NOT_FOUND fails in batch 6). The reflexes still handle it if it comes close.
		if (hostile != null && io.github.plrlr.autopilot.skills.CombatSkills.unreachable(hostile.entity())) hostile = null;
		if (hostile != null && hostile.dist() < 10) {
			// Creepers explode in melee range: back off instead of swinging at them.
			if (hostile.type().equals("creeper")) out.add(new Option("retreat", null, "a creeper is " + Math.round(hostile.dist()) + " blocks away"));
			else if (pl.getHealth() <= 8) out.add(escape("low health and a " + hostile.type() + " is close"));
			// Underground, two or more closing in wear us down in a tunnel: wall in and heal first.
			else if (!onSurface() && pl.getHealth() <= 12 && seen.hostilesWithin(6) >= 2) out.add(escape("hurt with monsters closing in"));
			// Skeletons outshoot a fleeing player; closing in fast is safer than running.
			// Blazes hover and shoot: the fortress fight waits for them at the spawner instead of chasing.
			else if (NetherPlan.fightInFortress(Mc.dimension(), hostile)) out.add(NetherPlan.blazeStep(memory, seen));
			else out.add(new Option("attack", hostile.type(), hostile.type() + " is " + Math.round(hostile.dist()) + " blocks away"));
		}
		if (wantsToEat()) {
			// Health only regenerates with 18+ hunger, so hurt means eat a little earlier.
			boolean hurt = pl.getHealth() < pl.getMaxHealth();
			if (food <= 14 || seen.hostilesWithin(8) == 0)
				out.add(new Option("eat", null, hurt && food > 14 ? "heal up: health " + Math.round(pl.getHealth()) + "/20, hunger " + food + "/20" : "hunger " + food + "/20"));
		}
		if (Mc.dimension().equals("overworld") && Mc.isNight()) {
			boolean bed = Mc.count("bed") > 0 || memory.nearestStation("bed") != null;
			// Sleeping skips the night and sets the respawn point: always worth it.
			if (bed) out.add(new Option("sleep", null, "sleep through the night"));
			// No bed but the wool for one: make it now rather than dig in or fight all night.
			else if (Items2.mostOfOneColor("wool") >= 3) {
				Option o = itemStep("bed", 1, 0);
				if (o != null && !o.skill().equals("explore")) out.add(new Option(o.skill(), o.arg(), "night: make a bed (" + o.why() + ")"));
			}
			// On the surface with little armor, monsters win at night; underground or armored, keep working.
			else if (onSurface() && pl.getArmorValue() < 10 && seen.hostilesWithin(16) > 0)
				out.add(new Option("shelter", null, "night with monsters around and little armor"));
		}
		return out;
	}

	/**
	 * Eat at hunger 14 or less, or 17 or less when hurt (regeneration needs 18+). Never at full
	 * hunger: normal food can't be eaten then and the eat skill would just time out.
	 */
	public static boolean wantsToEat() {
		LocalPlayer pl = Mc.player();
		int food = pl.getFoodData().getFoodLevel();
		if (food >= 20 || Mc.count(Items2::isAnyFood) == 0) return false;
		return food <= 14 || (food <= 17 && pl.getHealth() < pl.getMaxHealth());
	}

	/** Cooked food to keep in stock (upkeep hunts and cooks toward it). */
	public static final int FOOD_STOCK = 8;

	/** Items dropped at the last death, while they still exist (they vanish after 5 minutes). */
	private Option recoverStep() {
		if (memory.nearest("death") == null) return null;
		return new Option("goto", "death", "get back the items dropped when we died");
	}

	/**
	 * Cheap things a good player does on the way: keep some cooked food, get a bed early (it skips
	 * nights and sets the respawn point), grab coal that's in view. Only when the target is close,
	 * so upkeep never turns into a long detour.
	 */
	private List<Option> upkeep(Perception seen, Option main) {
		List<Option> out = new ArrayList<>();
		if (seen.hostilesWithin(12) > 0) return out;
		// Take our crafting table and furnace along before walking off: leaving them behind
		// meant crafting new ones (8 cobblestone each) after every trip in trials.
		boolean usingStation = main != null && (main.skill().equals("craft") || main.skill().equals("smelt"));
		if (!usingStation && Station.placedNear(Mc.player().blockPosition(), 6))
			out.add(new Option("pickup", "stations", "take our crafting table and furnace along"));
		// Keep 8+ cooked food. Raw meat counts toward the stock: it gets cooked in one furnace
		// load once there are a few, so hunting more before then would only waste time.
		int readyFood = Mc.count("food");
		if (readyFood < FOOD_STOCK) {
			int raw = Mc.count("meat");
			String most = null;
			for (String r : Items2.RAW_MEAT) if (Mc.count(r) > 0 && (most == null || Mc.count(r) > Mc.count(most))) most = r;
			if (most != null && (raw >= 3 || readyFood < 2) && canSmeltNow())
				out.add(new Option("smelt", "cooked_" + most + ":" + Mc.count(most), "cook the raw " + most + " in one load"));
			// Batch the hunt: animals in view get taken while the stock is short (3-5 per trip).
			if (readyFood + raw < FOOD_STOCK) {
				for (String animal : ANIMALS.split(",")) {
					Perception.Seen a = seen.nearest(animal);
					if (a != null && a.dist() < 20) {
						out.add(new Option("attack", animal, "food stock " + (readyFood + raw) + "/" + FOOD_STOCK + " and a " + animal + " is close"));
						break;
					}
				}
			}
			// Getting hungry with nothing ready to eat: food comes first (cook, hunt or search).
			int hunger = Mc.player().getFoodData().getFoodLevel();
			if (hunger <= 14 && readyFood == 0) {
				Option f = goalStep(Goal.FOOD, seen, 0);
				// Searching far for animals only when really hungry: at 9-14 it beat a ready
				// build_portal seven times in a night (batch 9, seed b) and found nothing.
				if (f != null && (!f.skill().equals("explore") || hunger <= 8))
					out.add(new Option(f.skill(), f.arg(), "hungry (" + hunger + "/20): " + f.why()));
			}
		}
		if (Mc.dimension().equals("overworld") && Items2.bestTier("pickaxe") >= 0 && Mc.count("bed") == 0
				&& memory.nearestStation("bed") == null) {
			if (Items2.mostOfOneColor("wool") >= 3) {
				Option o = itemStep("bed", 1, 0);
				if (o != null && !o.skill().equals("explore")) out.add(new Option(o.skill(), o.arg(), "make a bed: " + o.why()));
			} else {
				Perception.Seen sheep = seen.nearest("sheep");
				if (sheep != null && sheep.dist() < 20) out.add(new Option("attack", "sheep", "wool for a bed (skips nights)"));
			}
		}
		// Late game: a bow is what destroys the dragon's healing crystals. Make one as soon as
		// spider string allows (skeleton kills supply the arrows).
		if (Items2.bestTier("pickaxe") >= 3 && Mc.count("bow") == 0 && Mc.count("string") >= 3) {
			Option o = itemStep("bow", 1, 0);
			if (o != null && o.skill().equals("craft")) out.add(new Option(o.skill(), o.arg(), "make a bow for the dragon fight: " + o.why()));
		}
		// Armor only protects when worn: put it on as soon as it's in the bag.
		if (betterArmorInInventory()) out.add(new Option("equip", "armor", "wear the armor we carry"));
		// The shield blocks arrows and creeper blasts only from the off hand: put it there at once.
		if (Mc.count("shield") > 0 && !Items2.id(Mc.player().getOffhandItem()).equals("shield"))
			out.add(new Option("equip", "shield", "shield into the off hand"));
		if (Mc.count("coal") < 4 && Items2.bestTier("pickaxe") >= 0) {
			WorldMemory.Seen coal = memory.nearest("coal_ore");
			if (coal != null && coal.pos().distSqr(Mc.player().blockPosition()) < 12 * 12)
				out.add(new Option("collect", "coal:6", "coal ore in view (fuel for smelting)"));
		}
		return out;
	}

	private boolean canSmeltNow() {
		boolean furnace = memory.nearestStation("furnace") != null || Mc.count("furnace") > 0;
		boolean fuel = Mc.count("coal") > 0 || Mc.count("planks") >= 2 || Mc.count("log") >= 2;
		return furnace && fuel;
	}

	/**
	 * Getting away from monsters: underground, block up the gaps around us and heal (running
	 * through tunnels got the bot shot and cornered in trials); on the surface, run.
	 */
	public static Option escape(String why) {
		// Healing needs 18+ hunger: without food to get there, hiding is just waiting to be found.
		boolean canHeal = Mc.player().getFoodData().getFoodLevel() >= 18 || Mc.count(Items2.matcher("food")) > 0;
		if (!onSurface() && canHeal && Mc.count("throwaway") >= 6) return new Option("shelter", "heal", why + ": wall in and heal");
		return new Option("retreat", null, why);
	}

	private static boolean onSurface() {
		LocalPlayer pl = Mc.player();
		return pl.level().canSeeSky(pl.blockPosition().above());
	}

	/** Always-available, sometimes-useful options. */
	private List<Option> extras(Perception seen, Option main) {
		List<Option> out = new ArrayList<>();
		if (betterArmorInInventory()) out.add(new Option("equip", "armor", "armor in the bag that isn't worn"));
		if (Mc.count("shield") > 0 && !Items2.id(Mc.player().getOffhandItem()).equals("shield"))
			out.add(new Option("equip", "shield", "hold the shield in the off hand"));
		if (!seen.items.isEmpty()) out.add(new Option("pickup", null, seen.items.size() + " dropped items nearby"));
		if (Mc.isNight() && Mc.dimension().equals("overworld")) {
			if (Mc.count("bed") > 0 || memory.nearestStation("bed") != null) out.add(new Option("sleep", null, "skip the night"));
			else out.add(new Option("shelter", null, "wait out the night safely"));
		}
		out.add(new Option("explore", exploreTarget(main), "look for new ground"));
		return out;
	}

	/** What an explore should look for when the goal step can't be done here. */
	public static String exploreTarget(Option main) {
		if (main == null || main.arg() == null) return "any";
		if (main.skill().equals("explore") || main.skill().equals("attack")) return main.arg();
		if (main.skill().equals("make_obsidian")) return "lava";
		if (main.skill().equals("fill_bucket")) return "water";
		if (main.skill().equals("collect")) {
			String a = main.arg();
			String item = a.contains(":") ? a.substring(0, a.indexOf(':')) : a;
			// Surface blocks can be found by walking; ores are underground and found by mining.
			return switch (item) {
				case "log", "sand", "gravel", "obsidian" -> item;
				case "flint" -> "gravel";
				default -> "any";
			};
		}
		return "any";
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
