package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.skills.CastPortal;
import io.github.plrlr.autopilot.state.Perception;
import net.minecraft.world.entity.EquipmentSlot;
final class RouteSteps {
	private final Planner p;

	RouteSteps(Planner p) { this.p = p; }

	Option goalStep(Goal goal, Perception seen, int depth) {
		String dim = Mc.dimension();
		switch (goal) {
			case FOOD -> {
				for (String raw : Items2.RAW_MEAT) {
					if (Mc.count(raw) > 0) {
						Option o = p.smeltStep("cooked_" + (raw.equals("beef") ? "beef" : raw), Mc.count(raw), 0);
						if (o != null) return o;
					}
				}
				for (String animal : Planner.ANIMALS.split(",")) {
					if (seen.nearest(animal) != null) return new Option("attack", animal, "hunt for meat");
				}
				return SurvivalPlan.food(new Option("explore", Planner.ANIMALS, "look for animals to hunt"));
			}
			case IRON_ARMOR -> {
				Option o = itemsStep(goal);
				if (o != null) return o;
				return new Option("equip", "armor", "wear the iron armor");
			}
			case NETHER_PORTAL -> {
				if (dim.equals("the_nether")) return null;
				if (p.memory.nearest("nether_portal") != null) return new Option("enter_portal", "nether", "walk into the nether portal");
				// Finish the iron kit first. The iron-tools rung counts as reached with the pickaxe alone
				// and stays done, so the shield and iron sword were never made: no run in batch 11 ever
				// held a shield, though the iron for them was mined (seed g carried 7 spare ingots).
				for (String item : Goal.IRON_TOOLS.needs.keySet()) {
					if (Goal.have(item) < Goal.IRON_TOOLS.needs.get(item)) {
						Option o = p.itemStep(item, Goal.IRON_TOOLS.needs.get(item), depth + 1);
						if (o != null) return o;
					}
				}
				// Survive first (outside review #2, A2): deaths drop the buckets and iron the portal needs,
				// and 2-3 deaths per run was the norm. Chestplate and helmet (13 iron) before the portal,
				// boots too when the ingots are already there.
				for (String piece : new String[]{"iron_chestplate", "iron_helmet"}) {
					if (Tune.on("route.armor_before_portal") && !Goal.hasArmor(piece)) {
						Option o = p.itemStep(piece, 1, depth + 1);
						if (o != null) return o;
					}
				}
				if (Tune.on("route.boots_before_portal") && !Goal.hasArmor("iron_boots") && Mc.count("iron_ingot") >= 4) {
					Option o = p.itemStep("iron_boots", 1, depth + 1);
					if (o != null) return o;
				}
				// Brain v2: the strategist picks cast, diamonds or a ruined portal by learned expected time.
				Option st = strategic("in_nether", seen, depth);
				if (st != null) return st;
				// Speedrun route: without a diamond pickaxe, cast the frame from lava and water.
				// Two routes to the frame: cast it from lava and water (no diamonds; the speedrunners'
				// way), or the classic way: diamond pickaxe, harden lava into obsidian, mine 10 blocks.
				// A gene picks; the race decides which gets to the Nether sooner.
				// Diamonds found on the way down for lava: three make a diamond pickaxe, and with it the
				// classic portal (water on a lava pool, mine 10 obsidian) needs only the lava we're after.
				if (Tune.on("tools.diamond_pick_if_found") && Goal.have("obsidian") < 10 && Items2.bestTier("pickaxe") < 3
						&& Mc.count("diamond") >= 3) {
					Option o = p.itemStep("diamond_pickaxe", 1, depth + 1);
					if (o != null) return o;
				}
				if (Tune.on("route.diamond_portal") && Tune.on("route.deep_portal")) return deepStep(depth);
				if (Goal.have("obsidian") < 10 && Items2.bestTier("pickaxe") < 3) {
					if (!Tune.on("route.diamond_portal")) return castStep(depth);
					Option o = p.itemStep("diamond_pickaxe", 1, depth + 1);
					if (o != null) return o;
				}
				Option pool = PortalPlan.obsidian(p.memory);
				if (pool != null) return pool;
				Option o = itemsStep(goal);
				if (o != null) return o;
				// The frame corners can be any block; 10 obsidian covers the rest.
				if (Mc.count("throwaway") + Mc.count("planks") < 4) {
					Option c = p.itemStep("stone", Mc.count("stone") + 4, depth + 1);
					if (c != null) return c;
				}
				return new Option("build_portal", null, "build and light the nether portal");
			}
			case BLAZE_RODS -> {
				if (!dim.equals("the_nether")) return depth > 2 ? null : goalStep(Goal.NETHER_PORTAL, seen, depth + 1);
				Option st = strategicItem("blaze_rod", 7, seen, depth);
				if (st != null) return st;
				return NetherPlan.blazeStep(p.memory, seen);
			}
			case ENDER_PEARLS -> {
				Option st = strategicItem("ender_pearl", Tune.i("pearls.target"), seen, depth);
				if (st != null) return st;
				Option wave3 = NetherPlan.pearls(seen);
				if (wave3 != null) return wave3;
				Option trade = barterStep(seen, depth);
				if (trade != null) return trade;
				// The boat trap (speedrunners'): an enderman in a boat can't move, teleport or hit back.
				if (Tune.on("pearls.boat_trap") && io.github.plrlr.autopilot.skills.EndermanBoat.endermanInSight()) {
					if (Mc.count("boat") > 0) return new Option("enderman_boat", null, "trap the enderman in our boat, then kill it");
					Option b = p.itemStep("boat", 1, depth + 1);
					if (b != null && !b.skill().equals("explore")) return new Option(b.skill(), b.arg(), "a boat to trap endermen: " + b.why());
				}
				if (seen.nearest("enderman") != null) return new Option("attack", "enderman", "kill the enderman for a pearl");
				return new Option("explore", "enderman", "look for endermen (more at night, many in warped forests)");
			}
			case FIND_STRONGHOLD -> {
				if (dim.equals("the_nether") && p.memory.nearest("nether_portal") != null) return NetherPlan.home(new Option("enter_portal", "overworld", "go back to the overworld"));
				if (dim.equals("the_nether") && Tune.on("skill.portal_return")) return NetherPlan.home(null);
				if (p.memory.nearest("end_portal_frame") != null) return null;
				Option st = strategic("frame_known", seen, depth);
				if (st != null) return st;
				// Inside the stronghold already: its portal room is somewhere down the corridors.
				if (io.github.plrlr.autopilot.skills.SearchStronghold.inStronghold())
					return LatePlan.search(new Option("search_stronghold", null, "explore the stronghold's corridors for the portal room"));
				if (Mc.count("ender_eye") == 0) return p.itemStep("ender_eye", 1, 0);
				return LatePlan.locate(new Option("locate_stronghold", null, "throw an eye of ender and follow it"));
			}
			case ENTER_END -> {
				if (dim.equals("the_end")) return null;
				Option st = strategic("in_end", seen, depth);
				if (st != null) return st;
				if (p.memory.nearest("end_portal") != null) return new Option("enter_portal", "end", "jump into the end portal");
				if (p.memory.nearest("end_portal_frame") != null) {
					if (Mc.count("ender_eye") == 0) return p.itemStep("ender_eye", 1, 0);
					return LatePlan.beforeFill(new Option("fill_end_portal", null, "put eyes of ender in the empty frames"), p.memory);
				}
				return depth > 2 ? null : goalStep(Goal.FIND_STRONGHOLD, seen, depth + 1);
			}
			case KILL_DRAGON -> {
				if (!dim.equals("the_end")) return depth > 2 ? null : goalStep(Goal.ENTER_END, seen, depth + 1);
				// A different way to kill it (bed bombs, once raced) replaces the fight; the plain
				// fight keeps its crystals-first order below.
				Option st = strategic("dragon_dead", seen, depth);
				if (st != null && !st.skill().equals("dragon")) return st;
				Option endStep = LatePlan.end();
				if (endStep != null) return endStep;
				boolean bow = Mc.count("bow") > 0 && Mc.count("arrow") > 0;
				// Crystals stand high on the pillars, beyond the usual 32-block look: look farther.
				if (bow && Perception.look(96).nearest("end_crystal") != null) return new Option("shoot", "end_crystal", "crystals heal the dragon; destroy them first");
				// The fight itself is one deterministic skill: stay off the edges and out of the
				// breath, wait by the fountain, hit the head while it sits (skills/DragonFight).
				return new Option("dragon", null, "fight the dragon: wait by the fountain, hit its head when it lands");
			}
			case SURVIVE_NIGHT -> {
				if (Mc.count("bed") > 0 || p.memory.nearestStation("bed") != null) return new Option("sleep", null, "sleep through the night");
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

	/** Brain v2: the route the strategist chose last, as one line (Autopilot logs it when it changes). */

	/** The strategist's step toward a fact (gene brain.strategist); null when off or there's no route. */
	private Option strategic(String fact, Perception seen, int depth) {
		if (!Tune.on("brain.strategist")) return null;
		return adopt(new Strategist(new GameWorld(p.memory), (item, n) -> p.itemStep(item, n, depth + 1)).towardFact(fact), seen, depth);
	}

	/** The strategist's step toward n of an item (gene brain.strategist). */
	private Option strategicItem(String item, int n, Perception seen, int depth) {
		if (!Tune.on("brain.strategist")) return null;
		return adopt(new Strategist(new GameWorld(p.memory), (it, k) -> p.itemStep(it, k, depth + 1)).towardItem(item, n), seen, depth);
	}

	/**
	 * Takes the strategist's step, except that a route the rules already play well runs on the
	 * rules' own tuned code for it: the cast portal's preparation (deep lava, the block count),
	 * bartering (gold helmet first), the blaze fight at the spawner. The strategist decides which
	 * route; that code decides how. Then the readiness gate (gene plan.readiness).
	 */
	private Option adopt(Strategist.Step s, Perception seen, int depth) {
		if (s == null) return null;
		p.lastPlan = String.format("%s (%.1f min)", s.plan(), s.seconds() / 60);
		Option o = s.option();
		if (s.uses("build_portal", null) && !o.skill().equals("enter_portal")) {
			Option c = castStep(depth);
			if (c != null) o = c;
		} else if (s.uses("barter", null)) {
			Option b = barterStep(seen, depth);
			if (b != null) o = b;
		} else if (o.skill().equals("fortress") && s.uses("fortress", "blazes:7")) {
			o = NetherPlan.blazeStep(p.memory, seen);
		}
		if (Tune.on("plan.readiness")) {
			Option r = readiness(o, depth);
			if (r != null) return r;
		}
		return o;
	}

	/**
	 * Before a one-way door (the Nether, the End): the kit a player packs. Blocks to bridge and
	 * wall in, food, and for the Nether flint and steel to relight the portal home. Missing items
	 * come first; thresholds are genes.
	 */
	private Option readiness(Option o, int depth) {
		if (o == null || !o.skill().equals("enter_portal")) return null;
		boolean nether = "nether".equals(o.arg());
		if (!nether && !"end".equals(o.arg())) return null;
		String where = nether ? "the Nether" : "the End";
		int blocks = Tune.i(nether ? "ready.nether_blocks" : "ready.end_blocks");
		if (Mc.count("throwaway") < blocks) {
			Option b = p.itemStep("stone", Mc.count("stone") + blocks - Mc.count("throwaway"), depth + 1);
			if (b != null) return new Option(b.skill(), b.arg(), "ready for " + where + ": blocks (" + b.why() + ")");
		}
		int food = Tune.i(nether ? "ready.nether_food" : "ready.end_food");
		if (Goal.have("food") < food) {
			Option f = p.itemStep("food", food, depth + 1);
			if (f != null) return new Option(f.skill(), f.arg(), "ready for " + where + ": food (" + f.why() + ")");
		}
		if (nether && Goal.have("flint_and_steel") == 0) {
			Option f = p.itemStep("flint_and_steel", 1, depth + 1);
			if (f != null) return new Option(f.skill(), f.arg(), "ready for the Nether: flint and steel to relight the way home");
		}
		return null;
	}

	/**
	 * The deep route to the Nether (the user's call, 2026-09-29): casting a frame from buckets
	 * placed obsidian in few runs and lit a portal in almost none (history: portal_drill), so go
	 * the way most players do. Iron pickaxe, then down to diamond depth; the caves there sit on
	 * lava (cave air below y -55 is lava), so the diamond hunt turns up the pool as well. Three
	 * diamonds make the pickaxe, water on the pool makes obsidian, and 10 of it make the frame,
	 * built right there in a room we dig out (dig_portal), then lit.
	 *
	 * What can't be had down there is taken along first: flint and steel, a water bucket, a
	 * crafting table and sticks for the pickaxe (a table crafted at y -54 means a climb for wood).
	 */
	private Option deepStep(int depth) {
		Option head = PortalPlan.early(p.memory);
		if (head != null) return head;
		boolean up = Mc.player().getBlockY() > 0;
		if (Goal.have("flint_and_steel") == 0) {
			Option o = p.itemStep("flint_and_steel", 1, depth + 1);
			if (o != null) return o;
		}
		boolean pick = Items2.bestTier("pickaxe") >= 3;
		// Two buckets before going down: water to harden lava, and lava for the one-block mold
		// (obsidian_mold). Down there or with the pickaxe made, one will do (the pool hardens).
		int buckets = up && !pick ? 2 : 1;
		if (Goal.have("bucket") < buckets) {
			Option o = p.itemStep("bucket", buckets, depth + 1);
			if (o != null) return o;
		}
		if (up && Goal.have("obsidian") < 10) {
			if (Mc.count("water_bucket") == 0) {
				Option o = p.itemStep("water_bucket", 1, depth + 1);
				if (o != null) return o;
			}
			if (!pick && Mc.count("crafting_table") == 0) {
				Option o = p.itemStep("crafting_table", 1, depth + 1);
				if (o != null) return o;
			}
			if (!pick && Mc.count("stick") < Tune.i("deep.sticks")) {
				Option o = p.itemStep("stick", Tune.i("deep.sticks"), depth + 1);
				if (o != null) return o;
			}
		}
		if (!pick && Goal.have("obsidian") < 10) {
			Option o = p.itemStep("diamond_pickaxe", 1, depth + 1);
			if (o != null) return o;
		}
		if (Goal.have("obsidian") < 10) {
			if (PortalPlan.poolNear(p.memory))
				return new Option("obsidian_pool", null, "harden the lava pool and mine 10 obsidian, checking under each block");
			// No pool in reach yet: the diamond-depth caves are full of lava; mining on finds one.
			return new Option("collect", "diamond:1:lava", "mine on at diamond depth for a lava pool");
		}
		if (Mc.count("throwaway") + Mc.count("planks") < 4) {
			Option c = p.itemStep("stone", Mc.count("stone") + 4, depth + 1);
			if (c != null) return c;
		}
		return new Option("dig_portal", null, "dig a room and build the frame from our 10 obsidian, then light it");
	}

	/**
	 * What the cast portal needs: flint and steel, two buckets (one full of water), blocks for
	 * the wall behind the frame, and a lava pool to cast from.
	 */
	private Option castStep(int depth) {
		Option head = PortalPlan.early(p.memory);
		if (head != null) return head;
		if (Goal.have("flint_and_steel") == 0) {
			Option o = p.itemStep("flint_and_steel", 1, depth + 1);
			if (o != null) return o;
		}
		if (Goal.have("bucket") < 2) {
			Option o = p.itemStep("bucket", 2, depth + 1);
			if (o != null) return o;
		}
		if (Mc.count("water_bucket") == 0) {
			Option o = p.itemStep("water_bucket", 1, depth + 1);
			if (o != null) return o;
		}
		int blocks = Mc.count("throwaway");
		if (blocks < CastPortal.BLOCKS_NEEDED) {
			Option o = p.itemStep("stone", Mc.count("stone") + CastPortal.BLOCKS_NEEDED - blocks, depth + 1);
			if (o != null) return o;
		}
		if (p.memory.nearest("lava") == null && Mc.count("lava_bucket") == 0) {
			// Surface pools are rare (0 of 8 natural runs saw one in batch 12), but cave air below
			// y -55 is lava. Branch-mining at diamond depth finds a pool the close scan remembers,
			// and the step turns into build_portal right there. A diamond on the way is a bonus.
			if (Tune.on("route.deep_for_lava") && Items2.bestTier("pickaxe") >= 2 && Mc.dimension().equals("overworld"))
				return new Option("collect", "diamond:1:lava", "go deep for lava: cave air below y -55 is lava");
			return PortalPlan.lava(new Option("explore", "lava", "find a lava pool to cast the portal from"));
		}
		return PortalPlan.cast(new Option("build_portal", null, "cast a nether portal from lava and water (no diamonds needed)"));
	}

	/**
	 * Pearls from piglins (route.barter): a gold helmet first (piglins attack players without gold
	 * armor), then gold ingots up to the budget (nether gold ore -> nuggets -> ingots), then trade
	 * with adult piglins in sight, or look for some. Null when trading isn't the step now; the
	 * enderman hunt covers the rest.
	 */
	private Option barterStep(Perception seen, int depth) {
		if (!Tune.on("route.barter") || !Mc.dimension().equals("the_nether")) return null;
		if (Mc.count("ender_pearl") >= Tune.i("pearls.target")) return null;
		boolean goldWorn = false;
		for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
			if (Items2.id(Mc.player().getItemBySlot(s)).startsWith("golden_")) goldWorn = true;
		if (!goldWorn) {
			if (Mc.count("golden_helmet") > 0) return new Option("equip", "armor", "gold helmet on: piglins leave you alone");
			Option o = p.itemStep("golden_helmet", 1, depth + 1);
			if (o != null) return new Option(o.skill(), o.arg(), "gold helmet for trading with piglins: " + o.why());
		}
		int budget = Tune.i("pearls.barter_ingots");
		// Same test as the skill (adult, in sight): a baby or a piglin behind a wall made barter fail
		// "no adult piglin in sight" 14 times in the second barter test.
		boolean piglin = io.github.plrlr.autopilot.skills.Barter.piglinToTrade();
		if (Mc.count("gold_ingot") > 0 && piglin) return new Option("barter", null, "trade gold with the piglins for pearls");
		if (io.github.plrlr.autopilot.skills.Barter.traded() + Mc.count("gold_ingot") < budget) {
			Option o = p.itemStep("gold_ingot", Math.min(budget - io.github.plrlr.autopilot.skills.Barter.traded(), 16), depth + 1);
			if (o != null) return new Option(o.skill(), o.arg(), "gold to trade for pearls: " + o.why());
		}
		if (Mc.count("gold_ingot") > 0) return new Option("explore", "piglin", "find piglins to trade with");
		return null;
	}

	/** First missing need of an item goal, resolved down to an action. */
	private Option itemsStep(Goal goal) {
		for (var e : goal.needs.entrySet()) {
			if (Goal.have(e.getKey()) >= e.getValue()) continue;
			// Gene brain.utility (docs/brain-v3.md): every goal's items go through the strategist,
			// not only the late game's. With only the plain way (mine, craft, smelt) it returns the
			// rules' own step; where the specs hold other ways (food: hunt, fish, secure; iron:
			// restock), the cheapest by measured cost wins.
			if (Tune.on("brain.utility")) {
				Option st = adopt(new Strategist(new GameWorld(p.memory), (it, k) -> p.itemStep(it, k, 1)).towardItem(e.getKey(), e.getValue()),
						Perception.look(32), 0);
				if (st != null) return st;
			}
			Option o = p.itemStep(e.getKey(), e.getValue(), 0);
			if (o != null) return o;
		}
		return null;
	}

}
