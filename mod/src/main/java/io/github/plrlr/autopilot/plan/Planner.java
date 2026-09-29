package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.skills.CastPortal;
import io.github.plrlr.autopilot.skills.MoveSkills;
import io.github.plrlr.autopilot.skills.SmeltSkill;
import io.github.plrlr.autopilot.skills.Station;
import io.github.plrlr.autopilot.skills.ShoreSkill;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.Danger;
import io.github.plrlr.autopilot.state.DangerSense;
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
		Option main = axeFirst(goalStep(goal, seen, 0));
		if (Mc.dimension().equals("overworld") && goal.milestone >= Goal.IRON_TOOLS.milestone
				&& goal.milestone <= Goal.NETHER_PORTAL.milestone && Items2.bestTier("pickaxe") >= 1) {
			Option prep = Tune.on("food.early_stock") ? earlyFoodStep(seen) : null;
			if (prep == null && Tune.on("gear.armor_first")) prep = earlyGearStep();
			if (prep == null && Tune.on("gear.shield_early")) prep = earlyShieldStep();
			if (prep != null) main = prep;
		}
		main = SurvivalPlan.beforeWork(PortalPlan.diamonds(main));
		Option recovery = recoverStep();
		List<Option> urgent = urgent(seen, main);
		// Gens 57-58 cut 79 trips short, often for a distant fight or retreat. Once it is safe
		// enough to go, leave those routine choices behind; close mobs and creepers still win.
		if (Tune.on("death.trip_commit") && recovery != null && !SurvivalPlan.recoveryThreat(seen))
			urgent.removeIf(o -> java.util.Set.of("attack", "retreat", "shelter", "panic_box", "kite").contains(o.skill()));
		Option respawn = SurvivalPlan.respawn();
		if (respawn != null) urgent.add(0, respawn);
		Option arrival = NetherPlan.arrival();
		if (arrival != null) urgent.add(0, arrival);
		Option cross = SurvivalPlan.cross();
		if (cross != null && urgent.isEmpty()) urgent.add(cross);
		if (Tune.on("move.shore_first") && ShoreSkill.needed()
				&& (groundWork(main) || urgent.stream().anyMatch(Planner::groundWork))) {
			// One shared decision prevents collect, station, shelter and explore from handing the
			// same watery spot back and forth. Combat and eating can still take precedence.
			List<Option> shore = new ArrayList<>();
			for (Option o : urgent) if (o.skill().equals("attack") || o.skill().equals("retreat") || o.skill().equals("eat")) shore.add(o);
			Option move = new Option("shore", null, "reach seen dry ground before " + (main == null ? "working" : main.label()));
			shore.add(move);
			lastUrgent = shore.stream().map(Option::label).collect(java.util.stream.Collectors.toSet());
			return shore;
		}
		lastUrgent = urgent.stream().map(Option::label).collect(java.util.stream.Collectors.toSet());
		return order(urgent, recovery, surfaceOption(main), upkeep(seen, main), main, extras(seen, main));
	}

	private static boolean groundWork(Option o) {
		return o != null && java.util.Set.of("collect", "craft", "smelt", "shelter", "sleep", "explore").contains(o.skill());
	}

	/** Labels of the life-or-death options in the last list: the learned brain never overrules them. */
	public java.util.Set<String> lastUrgent = java.util.Set.of();

	/** The phase order, first label wins, at most 10. Pure, so unit tests can check it. */
	public static List<Option> order(List<Option> urgent, Option recover, Option surface, List<Option> upkeep, Option main,
									 List<Option> extras) {
		Map<String, Option> out = new LinkedHashMap<>();
		for (Option o : urgent) out.putIfAbsent(o.label(), o);
		if (recover != null) out.putIfAbsent(recover.label(), recover);
		if (surface != null) out.putIfAbsent(surface.label(), surface);
		for (Option o : upkeep) out.putIfAbsent(o.label(), o);
		if (main != null) out.putIfAbsent(main.label(), main);
		for (Option o : extras) out.putIfAbsent(o.label(), o);
		List<Option> list = new ArrayList<>(out.values());
		return list.size() > 10 ? list.subList(0, 10) : list;
	}

	/**
	 * Lost in a cave (a minute down here with nothing gained), or the next step needs the surface
	 * (trees, animals, walking to explore): go back up the way we came first.
	 */
	private Option surfaceOption(Option main) {
		boolean deep = !onSurface() && Mc.dimension().equals("overworld") && memory.surfaceEntry() != null
				&& memory.surfaceEntry().getY() - Mc.player().getBlockY() > 8;
		if (!(lostUnderground || (deep && needsSurface(main))) || MoveSkills.Goto.surfaceBlocked()) return null;
		return new Option("goto", "surface",
				lostUnderground ? "a minute underground without progress: back up the way we came" : "the next step is on the surface");
	}

	/** Set by the main loop: underground a while with nothing gained (see Autopilot.cave). */
	public boolean lostUnderground;

	/** Mining that happens below ground anyway: ores, stone, diamonds for lava depth. */
	private static boolean undergroundWork(Option o) {
		if (o == null || !o.skill().equals("collect") || o.arg() == null) return false;
		String item = o.arg().split(":")[0];
		TechTree.Source src = TechTree.MINE.get(item);
		return src != null && (src.mineY() != null || item.equals("stone") || item.equals("coal"));
	}

	/** Steps that only work on the surface: walking to explore, trees, animals. */
	private static boolean needsSurface(Option o) {
		if (o == null) return false;
		String a = o.arg() == null ? "" : o.arg();
		return o.skill().equals("explore") || (o.skill().equals("collect") && (a.startsWith("log") || a.startsWith("sand")))
				|| (o.skill().equals("attack") && ANIMALS.contains(a.split(",")[0]));
	}

	/** The optional FOOD rung is skipped by the fast route; this gene makes its stock a real step. */
	private Option earlyFoodStep(Perception seen) {
		int ready = Mc.count("food");
		if (ready >= 8) return null;
		// Loading the furnace removes raw meat from inventory. Keep collecting that batch instead
		// of wandering off to hunt while its cooked food is still waiting at our station.
		for (String meat : Items2.RAW_MEAT) {
			String cooked = "cooked_" + meat;
			SmeltSkill.Job job = SmeltSkill.job(cooked);
			if (job != null) return new Option("smelt", cooked + ":" + job.count(), "collect cooked food before the iron trip");
		}
		int raw = Mc.count("meat");
		String most = null;
		for (String meat : Items2.RAW_MEAT)
			if (Mc.count(meat) > 0 && (most == null || Mc.count(meat) > Mc.count(most))) most = meat;
		if (most != null && (ready + raw >= 8 || Mc.player().getFoodData().getFoodLevel() < 18)) {
			Option cook = smeltStep("cooked_" + most, Mc.count(most), 0);
			if (cook != null) return cook;
		}
		for (String animal : ANIMALS.split(",")) {
			Perception.Seen mob = seen.nearest(animal);
			if (mob != null && Mc.canSee(mob.entity()))
				return new Option("attack", animal, "stock food before the iron trip (" + ready + "/8 cooked)");
		}
		// On sparse starts a compulsory animal search can replace the iron route forever.
		return null;
	}

	/** Spend iron in survivability order; the pickaxe still comes first so iron can be mined. */
	private Option earlyGearStep() {
		if (betterArmorInInventory()) return new Option("equip", "armor", "wear the armor we carry");
		if (Items2.bestTier("pickaxe") < 2) return itemStep("iron_pickaxe", 1, 0);
		for (String item : new String[]{"iron_chestplate", "iron_sword", "iron_helmet", "iron_boots"}) {
			if (item.endsWith("_sword") ? Items2.bestTier("sword") >= 2 : Goal.hasArmor(item)) continue;
			Option step = itemStep(item, 1, 0);
			if (step != null) return step;
		}
		return null;
	}

	/** A shield only helps when equipped in the off hand. */
	private Option earlyShieldStep() {
		if (Items2.id(Mc.player().getOffhandItem()).equals("shield")) return null;
		if (Mc.count("shield") > 0) return new Option("equip", "shield", "shield into the off hand");
		if (Mc.count("iron_ingot") + Mc.count("raw_iron") == 0) return null;
		return itemStep("shield", 1, 0);
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
				return SurvivalPlan.food(new Option("explore", ANIMALS, "look for animals to hunt"));
			}
			case IRON_ARMOR -> {
				Option o = itemsStep(goal);
				if (o != null) return o;
				return new Option("equip", "armor", "wear the iron armor");
			}
			case NETHER_PORTAL -> {
				if (dim.equals("the_nether")) return null;
				if (memory.nearest("nether_portal") != null) return new Option("enter_portal", "nether", "walk into the nether portal");
				// Finish the iron kit first. The iron-tools rung counts as reached with the pickaxe alone
				// and stays done, so the shield and iron sword were never made: no run in batch 11 ever
				// held a shield, though the iron for them was mined (seed g carried 7 spare ingots).
				for (String item : Goal.IRON_TOOLS.needs.keySet()) {
					if (Goal.have(item) < Goal.IRON_TOOLS.needs.get(item)) {
						Option o = itemStep(item, Goal.IRON_TOOLS.needs.get(item), depth + 1);
						if (o != null) return o;
					}
				}
				// Survive first (outside review #2, A2): deaths drop the buckets and iron the portal needs,
				// and 2-3 deaths per run was the norm. Chestplate and helmet (13 iron) before the portal,
				// boots too when the ingots are already there.
				for (String piece : new String[]{"iron_chestplate", "iron_helmet"}) {
					if (Tune.on("route.armor_before_portal") && !Goal.hasArmor(piece)) {
						Option o = itemStep(piece, 1, depth + 1);
						if (o != null) return o;
					}
				}
				if (Tune.on("route.boots_before_portal") && !Goal.hasArmor("iron_boots") && Mc.count("iron_ingot") >= 4) {
					Option o = itemStep("iron_boots", 1, depth + 1);
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
					Option o = itemStep("diamond_pickaxe", 1, depth + 1);
					if (o != null) return o;
				}
				if (Goal.have("obsidian") < 10 && Items2.bestTier("pickaxe") < 3) {
					if (!Tune.on("route.diamond_portal")) return castStep(depth);
					Option o = itemStep("diamond_pickaxe", 1, depth + 1);
					if (o != null) return o;
				}
				Option pool = PortalPlan.obsidian(memory);
				if (pool != null) return pool;
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
				Option st = strategicItem("blaze_rod", 7, seen, depth);
				if (st != null) return st;
				return NetherPlan.blazeStep(memory, seen);
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
					Option b = itemStep("boat", 1, depth + 1);
					if (b != null && !b.skill().equals("explore")) return new Option(b.skill(), b.arg(), "a boat to trap endermen: " + b.why());
				}
				if (seen.nearest("enderman") != null) return new Option("attack", "enderman", "kill the enderman for a pearl");
				return new Option("explore", "enderman", "look for endermen (more at night, many in warped forests)");
			}
			case FIND_STRONGHOLD -> {
				if (dim.equals("the_nether") && memory.nearest("nether_portal") != null) return NetherPlan.home(new Option("enter_portal", "overworld", "go back to the overworld"));
				if (dim.equals("the_nether") && Tune.on("skill.portal_return")) return NetherPlan.home(null);
				if (memory.nearest("end_portal_frame") != null) return null;
				Option st = strategic("frame_known", seen, depth);
				if (st != null) return st;
				// Inside the stronghold already: its portal room is somewhere down the corridors.
				if (io.github.plrlr.autopilot.skills.SearchStronghold.inStronghold())
					return LatePlan.search(new Option("search_stronghold", null, "explore the stronghold's corridors for the portal room"));
				if (Mc.count("ender_eye") == 0) return itemStep("ender_eye", 1, 0);
				return LatePlan.locate(new Option("locate_stronghold", null, "throw an eye of ender and follow it"));
			}
			case ENTER_END -> {
				if (dim.equals("the_end")) return null;
				Option st = strategic("in_end", seen, depth);
				if (st != null) return st;
				if (memory.nearest("end_portal") != null) return new Option("enter_portal", "end", "jump into the end portal");
				if (memory.nearest("end_portal_frame") != null) {
					if (Mc.count("ender_eye") == 0) return itemStep("ender_eye", 1, 0);
					return LatePlan.beforeFill(new Option("fill_end_portal", null, "put eyes of ender in the empty frames"), memory);
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

	/** Brain v2: the route the strategist chose last, as one line (Autopilot logs it when it changes). */
	public volatile String lastPlan = "";

	/** The strategist's step toward a fact (gene brain.strategist); null when off or there's no route. */
	private Option strategic(String fact, Perception seen, int depth) {
		if (!Tune.on("brain.strategist")) return null;
		return adopt(new Strategist(new GameWorld(memory), (item, n) -> itemStep(item, n, depth + 1)).towardFact(fact), seen, depth);
	}

	/** The strategist's step toward n of an item (gene brain.strategist). */
	private Option strategicItem(String item, int n, Perception seen, int depth) {
		if (!Tune.on("brain.strategist")) return null;
		return adopt(new Strategist(new GameWorld(memory), (it, k) -> itemStep(it, k, depth + 1)).towardItem(item, n), seen, depth);
	}

	/**
	 * Takes the strategist's step, except that a route the rules already play well runs on the
	 * rules' own tuned code for it: the cast portal's preparation (deep lava, the block count),
	 * bartering (gold helmet first), the blaze fight at the spawner. The strategist decides which
	 * route; that code decides how. Then the readiness gate (gene plan.readiness).
	 */
	private Option adopt(Strategist.Step s, Perception seen, int depth) {
		if (s == null) return null;
		lastPlan = String.format("%s (%.1f min)", s.plan(), s.seconds() / 60);
		Option o = s.option();
		if (s.uses("build_portal", null) && !o.skill().equals("enter_portal")) {
			Option c = castStep(depth);
			if (c != null) o = c;
		} else if (s.uses("barter", null)) {
			Option b = barterStep(seen, depth);
			if (b != null) o = b;
		} else if (o.skill().equals("fortress") && s.uses("fortress", "blazes:7")) {
			o = NetherPlan.blazeStep(memory, seen);
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
			Option b = itemStep("stone", Mc.count("stone") + blocks - Mc.count("throwaway"), depth + 1);
			if (b != null) return new Option(b.skill(), b.arg(), "ready for " + where + ": blocks (" + b.why() + ")");
		}
		int food = Tune.i(nether ? "ready.nether_food" : "ready.end_food");
		if (Goal.have("food") < food) {
			Option f = itemStep("food", food, depth + 1);
			if (f != null) return new Option(f.skill(), f.arg(), "ready for " + where + ": food (" + f.why() + ")");
		}
		if (nether && Goal.have("flint_and_steel") == 0) {
			Option f = itemStep("flint_and_steel", 1, depth + 1);
			if (f != null) return new Option(f.skill(), f.arg(), "ready for the Nether: flint and steel to relight the way home");
		}
		return null;
	}

	/**
	 * What the cast portal needs: flint and steel, two buckets (one full of water), blocks for
	 * the wall behind the frame, and a lava pool to cast from.
	 */
	private Option castStep(int depth) {
		Option head = PortalPlan.early(memory);
		if (head != null) return head;
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
		if (memory.nearest("lava") == null && Mc.count("lava_bucket") == 0) {
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
			Option o = itemStep("golden_helmet", 1, depth + 1);
			if (o != null) return new Option(o.skill(), o.arg(), "gold helmet for trading with piglins: " + o.why());
		}
		int budget = Tune.i("pearls.barter_ingots");
		// Same test as the skill (adult, in sight): a baby or a piglin behind a wall made barter fail
		// "no adult piglin in sight" 14 times in the second barter test.
		boolean piglin = io.github.plrlr.autopilot.skills.Barter.piglinToTrade();
		if (Mc.count("gold_ingot") > 0 && piglin) return new Option("barter", null, "trade gold with the piglins for pearls");
		if (io.github.plrlr.autopilot.skills.Barter.traded() + Mc.count("gold_ingot") < budget) {
			Option o = itemStep("gold_ingot", Math.min(budget - io.github.plrlr.autopilot.skills.Barter.traded(), 16), depth + 1);
			if (o != null) return new Option(o.skill(), o.arg(), "gold to trade for pearls: " + o.why());
		}
		if (Mc.count("gold_ingot") > 0) return new Option("explore", "piglin", "find piglins to trade with");
		return null;
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
		// Gold ingots from nuggets only in the Nether; in the overworld raw gold gets smelted.
		if (r != null && item.equals("gold_ingot") && !Mc.dimension().equals("the_nether")) r = null;
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
				case "log" -> Items2.bestTier("pickaxe") < 0 ? Tune.i("gather.first_logs")
						: Mc.count("log") < Tune.i("gather.log_stock") ? Tune.i("gather.log_stock") : 3;
				// All the iron the run needs in one trip down, not 2-3 at a time with a furnace each.
				case "raw_iron" -> Math.max(0, ironStillNeeded() - Mc.count("iron_ingot") - have - missing);
				case "stone" -> Mc.count("stone") < Tune.i("gather.stone_stock") ? Tune.i("gather.stone_extra") : 0;
				case "coal" -> Tune.i("gather.coal_extra");
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
		if (input.equals("raw_iron") && !(Tune.on("gear.shield_early") && Mc.count("shield") == 0 && missing == 1))
			missing = Math.max(missing, Mc.count("raw_iron"));
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
	 * A stone axe before more wood: logs break about three times faster than by hand, and it costs
	 * three cobblestone and two sticks once there is a stone pickaxe (gene tools.stone_axe).
	 */
	Option axeFirst(Option main) {
		if (main == null || !main.skill().equals("collect") || main.arg() == null || !main.arg().startsWith("log")) return main;
		if (!Tune.on("tools.stone_axe") || Items2.bestTier("axe") >= 1 || Items2.bestTier("pickaxe") < 1) return main;
		if (Mc.count("throwaway") < 3 && Mc.count("cobblestone") < 3) return main;
		Option step = itemStep("stone_axe", 1, 1);
		return step == null ? main : step;
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
			// collect n means n more: the stone we carry is already in "throwaway".
			return new Option("collect", "stone:" + (CastPortal.BLOCKS_NEEDED - Mc.count("throwaway")), "blocks for the portal wall");
		if (Mc.count("flint_and_steel") == 0 && Mc.count("flint") == 0) return new Option("collect", "flint:1", "flint for flint and steel");
		return null;
	}

	/**
	 * Iron ingots the fast route still needs: iron pickaxe 3, iron sword 2, shield 1, two
	 * buckets 3 each (water, and lava to cast the portal), flint and steel 1. Then chestplate 8
	 * and helmet 5 for the portal step, as a second trip.
	 */
	public static int ironStillNeeded() {
		if (Tune.on("gear.armor_first") && Mc.dimension().equals("overworld")) {
			int next = Items2.bestTier("pickaxe") < 2 ? 3
					: !Goal.hasArmor("iron_chestplate") ? 8
					: Items2.bestTier("sword") < 2 ? 2
					: !Goal.hasArmor("iron_helmet") ? 5
					: !Goal.hasArmor("iron_boots") ? 4 : 0;
			if (next > 0) return Math.max(0, next - SmeltSkill.pending("iron_ingot"));
		}
		int n = 0;
		if (Items2.bestTier("pickaxe") < 2) n += 3;
		if (Items2.bestTier("sword") < 2) n += 2;
		if (Mc.count("shield") == 0) n += 1;
		n += 3 * Math.max(0, 2 - Goal.have("bucket"));
		if (Mc.count("flint_and_steel") == 0) n += 1;
		// Armor in the first batch doubled the ore to 26 before anything was smelted, and iron
		// tools fell from 6/8 runs to 4/8 (batch 11).
		if (n == 0 && Tune.on("route.armor_before_portal")) {
			if (!Goal.hasArmor("iron_chestplate")) n += 8;
			if (!Goal.hasArmor("iron_helmet")) n += 5;
		}
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
	private List<Option> urgent(Perception seen, Option main) {
		List<Option> out = new ArrayList<>();
		if (Tune.on("safety.spawner_room") && io.github.plrlr.autopilot.skills.SpawnerRoom.escapeNeeded(memory))
			out.add(new Option("spawner_escape", null, "fresh mobs keep coming from a seen spawner: wall it off and leave"));
		Option nether = NetherPlan.urgent(seen);
		if (nether != null) out.add(nether);
		LocalPlayer pl = Mc.player();
		int food = pl.getFoodData().getFoodLevel();
		Perception.Seen hostile = seen.nearestHostile();
		// A monster an attack just failed to reach: offering to attack it again only fails again
		// (10 NOT_FOUND fails in batch 6). The reflexes still handle it if it comes close.
		if (hostile != null && io.github.plrlr.autopilot.skills.CombatSkills.unreachable(hostile.entity())) hostile = null;
		// In the Nether only what's on top of us: chasing a magma cube 8 blocks off walked the bot
		// into lava, and it burned to death (freebuff nether run 0455). Blazes go to the fortress fight.
		double range = hostile != null && Mc.dimension().equals("the_nether") && !hostile.type().equals("blaze") ? 4 : Tune.get("plan.hostile_range");
		if (hostile != null && hostile.dist() < range) {
			Option verdict = Tune.on("survival.danger_v2") && !NetherPlan.fightInFortress(Mc.dimension(), hostile)
					? dangerOption(DangerSense.assess(seen), "visible danger") : null;
			if (verdict != null) out.add(verdict);
			// Creepers explode in melee range: back off instead of swinging at them.
			else if (hostile.type().equals("creeper")) out.add(SurvivalPlan.fight(hostile, io.github.plrlr.autopilot.skills.CombatSkills.canHitCreeper(hostile)
					? new Option("attack", "creeper", "hit and back off from a visible creeper")
					: new Option("retreat", null, "a creeper is " + Math.round(hostile.dist()) + " blocks away")));
			// Blazes hover and shoot fire: the fortress fight waits for them at the spawner and backs
			// off out of sight to heal itself. Walling in at low health (the Nether has no sky, so it
			// always counts as underground) burned the bot to death in its first blaze test.
			else if (NetherPlan.fightInFortress(Mc.dimension(), hostile)) out.add(NetherPlan.blazeStep(memory, seen));
			// Low health: run when outnumbered, or while the one monster is still far enough away to
			// get clear. One at arm's length follows and hits our back; the attack's shield does better.
			else if (pl.getHealth() <= Tune.i("combat.flee_hp") && (seen.hostilesWithin(6) >= Tune.i("combat.outnumbered") || hostile.dist() > 4))
				out.add(escape(seen, "low health and a " + hostile.type() + " is close"));
			// Underground, two or more closing in wear us down in a tunnel: wall in and heal first.
			else if (!onSurface() && pl.getHealth() <= Tune.i("plan.hide_hp") && seen.hostilesWithin(6) >= 2) out.add(escape(seen, "hurt with monsters closing in"));
			// Skeletons outshoot a fleeing player; closing in fast is safer than running.
			else out.add(SurvivalPlan.outnumbered(seen, SurvivalPlan.fight(hostile, new Option("attack", hostile.type(), hostile.type() + " is " + Math.round(hostile.dist()) + " blocks away"))));
		}
		if (wantsToEat()) {
			// Health only regenerates with 18+ hunger, so hurt means eat a little earlier.
			boolean hurt = pl.getHealth() < pl.getMaxHealth();
			if (food <= Tune.i("food.eat_at") || seen.hostilesWithin(8) == 0)
				out.add(new Option("eat", null, hurt && food > Tune.i("food.eat_at") ? "heal up: health " + Math.round(pl.getHealth()) + "/20, hunger " + food + "/20" : "hunger " + food + "/20"));
		}
		if (Mc.dimension().equals("overworld") && Mc.isNight()) {
			boolean bed = Mc.count("bed") > 0 || memory.nearestStation("bed") != null;
			boolean addedBedStep = false;
			// Sleeping skips the night and sets the respawn point: always worth it.
			if (bed) {
				out.add(new Option("sleep", null, "sleep through the night"));
				addedBedStep = true;
			} else if (Items2.mostOfOneColor("wool") >= 3) {
				// No bed but the wool for one: make it now rather than dig in or fight all night.
				Option o = itemStep("bed", 1, 0);
				if (o != null && !o.skill().equals("explore")) {
					out.add(new Option(o.skill(), o.arg(), "night: make a bed (" + o.why() + ")"));
					addedBedStep = true;
				}
			}
			// On the surface with little armor, monsters win at night; underground or armored, keep
			// working. Also falls through here when there was wool but itemStep couldn't turn it
			// into a real step (W3: 3 wool used to silently switch off shelter too).
			// A player with work underground doesn't hide in a hole all night: the mine is the safe place.
			boolean mineInstead = Tune.on("night.mine_instead") && undergroundWork(main);
			if (!addedBedStep && !mineInstead && onSurface() && pl.getArmorValue() < Tune.i("night.shelter_armor")
					&& seen.hostilesWithin(Tune.i("night.shelter_radius")) > 0)
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
		if (food >= 20) return false;
		if (Tune.on("food.keep_full")) {
			// Raw chicken and other risky food are a last resort, not routine regeneration fuel.
			if (Mc.count(s -> Items2.isGoodFood(s) || food <= 6 && Items2.isAnyFood(s)) == 0) return false;
			if (food < 18) return true;
		} else if (Mc.count(Items2::isAnyFood) == 0) return false;
		return food <= Tune.i("food.eat_at") || (food <= Tune.i("food.eat_hurt_at") && pl.getHealth() < pl.getMaxHealth());
	}

	/** Searching far for animals only when really hungry (batch 9: at 9-14 it beat a ready portal step). */
	public static boolean foodSearchWorthIt(int hunger) {
		return hunger <= Tune.i("food.search_hunger");
	}

	/** Cooked food to keep in stock (upkeep hunts and cooks toward it). */
	public static int foodStock() {
		return Tune.i("food.stock");
	}

	/** Items dropped at the last death, while they still exist (they vanish after 5 minutes). */
	private Option recoverStep() {
		WorldMemory.Seen death = memory.nearest("death");
		if (death == null) return null;
		if (Tune.on("safety.spawner_room") && io.github.plrlr.autopilot.skills.SpawnerRoom.near(memory, death.pos(), 8)) return null;
		// Not back into the dark without armor: the monsters that killed us are still there, and
		// batch 10's runs died 4-6 times each walking back (goto death interrupted 86 times).
		if (Mc.dimension().equals("overworld") && Mc.isNight() && Mc.player().getArmorValue() < Tune.i("death.recover_night_armor")) return null;
		// Not back down a mine without a stone pickaxe: a trip down there can't climb out again
		// (batch 11, seed d: four deaths at y 0-11, then 12 minutes of goto surface STUCK at y 2).
		// Rebuilding the tools takes about a minute; the items keep for five.
		if (death.pos().getY() < Mc.player().getBlockY() - 12 && Items2.bestTier("pickaxe") < 1) return null;
		return SurvivalPlan.recover(new Option("goto", "death", "get back the items dropped when we died"));
	}

	/**
	 * Cheap things a good player does on the way: keep some cooked food, get a bed early (it skips
	 * nights and sets the respawn point), grab coal that's in view. Only when the target is close,
	 * so upkeep never turns into a long detour.
	 */
	private List<Option> upkeep(Perception seen, Option main) {
		List<Option> out = new ArrayList<>();
		if (seen.hostilesWithin(Tune.i("plan.upkeep_calm_radius")) > 0) return out;
		Option bed = SurvivalPlan.bed(seen);
		if (bed != null) out.add(bed);
		Option restock = PortalPlan.restock(memory);
		if (restock != null) out.add(restock);
		Option stash = PortalPlan.stash(memory);
		if (stash != null) out.add(stash);
		// Take our crafting table and furnace along before walking off: leaving them behind
		// meant crafting new ones (8 cobblestone each) after every trip in trials.
		boolean usingStation = main != null && (main.skill().equals("craft") || main.skill().equals("smelt"));
		if (!usingStation && Station.placedNear(Mc.player().blockPosition(), 6))
			out.add(new Option("pickup", "stations", "take our crafting table and furnace along"));
		// Keep 8+ cooked food. Raw meat counts toward the stock: it gets cooked in one furnace
		// load once there are a few, so hunting more before then would only waste time.
		int readyFood = Mc.count("food");
		int stock = foodStock();
		if (readyFood < stock) {
			int raw = Mc.count("meat");
			String most = null;
			for (String r : Items2.RAW_MEAT) if (Mc.count(r) > 0 && (most == null || Mc.count(r) > Mc.count(most))) most = r;
			if (most != null && (raw >= 3 || readyFood < 2) && canSmeltNow())
				out.add(new Option("smelt", "cooked_" + most + ":" + Mc.count(most), "cook the raw " + most + " in one load"));
			// Batch the hunt: animals in view get taken while the stock is short (3-5 per trip).
			if (readyFood + raw < stock) {
				for (String animal : ANIMALS.split(",")) {
					Perception.Seen a = seen.nearest(animal);
					if (a != null && a.dist() < Tune.i("food.hunt_dist")) {
						out.add(new Option("attack", animal, "food stock " + (readyFood + raw) + "/" + stock + " and a " + animal + " is close"));
						break;
					}
				}
			}
			// Getting hungry with nothing ready to eat: food comes first (cook, hunt or search).
			int hunger = Mc.player().getFoodData().getFoodLevel();
			if (hunger <= Tune.i("food.eat_at") && readyFood == 0) {
				Option f = goalStep(Goal.FOOD, seen, 0);
				// Searching far for animals only when really hungry: at 9-14 it beat a ready
				// build_portal seven times in a night (batch 9, seed b) and found nothing.
				if (f != null && (!f.skill().equals("explore") || foodSearchWorthIt(hunger)))
					out.add(new Option(f.skill(), f.arg(), "hungry (" + hunger + "/20): " + f.why()));
			}
		}
		if (Tune.on("route.bed") && Mc.dimension().equals("overworld") && Items2.bestTier("pickaxe") >= 0 && Mc.count("bed") == 0
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
		// Torches for the caves (gene cave.torches): craft a stock when coal and sticks are at hand.
		if (Tune.on("cave.torches") && Mc.count("torch") < 4 && Mc.count("coal") > 0) {
			Option t = itemStep("torch", 8, 0);
			if (t != null && t.skill().equals("craft")) out.add(new Option(t.skill(), t.arg(), "torches to light caves (mobs spawn only in the dark)"));
		}
		// A sword to match the pickaxe (gear.sword_early): 96 of 138 mob deaths in generations 6-9 had
		// no sword at all; stone costs two cobblestone and a stick.
		if (Tune.on("gear.sword_early") && Items2.bestTier("pickaxe") >= 0 && Items2.bestTier("sword") < Math.min(2, Items2.bestTier("pickaxe"))) {
			String sword = switch (Math.min(2, Items2.bestTier("pickaxe"))) {
				case 2 -> "iron_sword";
				case 1 -> "stone_sword";
				default -> "wooden_sword";
			};
			Option sw = itemStep(sword, 1, 0);
			if (sw != null && sw.skill().equals("craft")) out.add(new Option(sw.skill(), sw.arg(), "a sword to fight with: " + sw.why()));
		}
		// Armor only protects when worn: put it on as soon as it's in the bag.
		if (betterArmorInInventory()) out.add(new Option("equip", "armor", "wear the armor we carry"));
		// The shield blocks arrows and creeper blasts only from the off hand: put it there at once.
		if (Mc.count("shield") > 0 && !Items2.id(Mc.player().getOffhandItem()).equals("shield"))
			out.add(new Option("equip", "shield", "shield into the off hand"));
		// Iron ore we can see, while the route still needs iron: take it now instead of a trip later.
		if (Tune.on("gather.iron_in_view") && Items2.bestTier("pickaxe") >= 1
				&& ironStillNeeded() > Mc.count("iron_ingot") + Mc.count("raw_iron")) {
			WorldMemory.Seen iron = memory.nearest("iron_ore");
			int view = Tune.i("gather.coal_view_dist");
			if (iron != null && iron.pos().distSqr(Mc.player().blockPosition()) < view * view)
				out.add(new Option("collect", "raw_iron:" + Math.min(8, ironStillNeeded() - Mc.count("iron_ingot") - Mc.count("raw_iron")),
						"iron ore in view (the route needs it)"));
		}
		if (Mc.count("coal") < Tune.i("gather.coal_upkeep_below") && Items2.bestTier("pickaxe") >= 0) {
			WorldMemory.Seen coal = memory.nearest("coal_ore");
			int view = Tune.i("gather.coal_view_dist");
			if (coal != null && coal.pos().distSqr(Mc.player().blockPosition()) < view * view)
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
	public static Option escape(Perception seen, String why) {
		if (Tune.on("survival.danger_v2")) {
			Option verdict = dangerOption(DangerSense.assess(seen), why);
			if (verdict != null) return verdict;
		}
		Option pillar = SurvivalPlan.pillar(seen, why);
		return pillar != null ? pillar : SurvivalPlan.retreat(seen, escape(seen, canHide(), why));
	}

	/**
	 * The rules' pick first, then the other ways to deal with the nearest monster that are possible
	 * here: fight it, run, or wall in and heal. The learned danger model (gene safety.hazard) picks
	 * among them; without it the first is taken, exactly as before.
	 */
	public static List<Option> escapeChoices(Perception seen, Option rulesPick) {
		List<Option> out = new ArrayList<>();
		out.add(rulesPick);
		Option pillar = SurvivalPlan.pillar(seen, rulesPick.why());
		if (pillar != null) out.add(pillar);
		if (Tune.on("skill.panic_box") && io.github.plrlr.autopilot.skills.PanicBox.suits(seen)) out.add(new Option("panic_box", null, rulesPick.why() + ": box in and heal"));
		Perception.Seen h = seen.nearestHostile();
		String why = rulesPick.why();
		if (h != null && !h.type().equals("creeper") && h.dist() <= 6) out.add(new Option("attack", h.type(), why + ": fight it"));
		out.add(SurvivalPlan.retreat(seen, new Option("retreat", null, why + ": run")));
		if (canHide()) out.add(new Option("shelter", "heal", why + ": wall in and heal"));
		List<Option> unique = new ArrayList<>();
		for (Option o : out)
			if (unique.stream().noneMatch(u -> u.label().equals(o.label()))) unique.add(o);
		return unique;
	}

	/** Whether walling in to heal can work here (the same test the rules' escape uses). */
	private static boolean canHide() {
		// Hiding only heals with 18+ hunger or food to eat; otherwise shelter heal fails at once and
		// this would pick it again.
		boolean canHeal = Mc.player().getFoodData().getFoodLevel() >= 18 || Mc.count(Items2.matcher("food")) > 0;
		// Not in the Nether (no sky, so it always looks "underground") or while burning: walled in
		// on fire against blazes, the bot burned to death in the first blaze test.
		boolean hideOk = !Mc.dimension().equals("the_nether") && !Mc.player().isOnFire();
		// Underground always; on the surface too with combat.wall_in_anywhere: 56 of 138 mob deaths in
		// generations 6-9 came while retreating (back turned, ~5 health), running is what got it killed.
		boolean wallOk = !onSurface() || Tune.on("combat.wall_in_anywhere");
		return wallOk && hideOk && canHeal && Mc.count("throwaway") >= 6;
	}


	/** Translate the one verdict to existing skill names, preserving the decision log schema. */
	public static Option dangerOption(Danger.Verdict verdict, String why) {
		return switch (verdict.kind()) {
			case FIGHT -> new Option("attack", verdict.target(), why + ": fight");
			case RETREAT -> new Option("retreat", null, why + ": safe retreat");
			case WALL_IN -> new Option("shelter", "heal", why + ": wall in");
			case AVOID_HAZARD, NONE -> null;
		};
	}

	/** Same perceived threats for the planner and reflex; no extra scan or hidden information. */
	public static Perception.Seen escapeCreeper(Perception seen) {
		for (Perception.Seen mob : seen.mobs)
			if (mob.hostile() && mob.type().equals("creeper") && mob.dist() < Tune.get("reflex.creeper_dist")) return mob;
		return null;
	}

	static Option escape(Perception seen, boolean canShelter, String why) {
		// Fled twice in 30 s (gene reflex.escalate): running isn't working, so stand our ground,
		// walled in if we can. Not from a creeper: its blast breaks walls and hurts fighters.
		if (Tune.on("reflex.escalate") && Escalation.ranTwice() && escapeCreeper(seen) == null) {
			Perception.Seen h = seen.nearestHostile();
			if (canShelter) return new Option("shelter", "heal", why + ": fled twice already, wall in");
			if (h != null) return new Option("attack", h.type(), why + ": fled twice already, stand and fight");
		}
		boolean noCloseRetreat = Tune.on("combat.no_close_retreat");
		// A second mob can be a creeper: don't wall in or fight with a blast about to happen.
		if (noCloseRetreat && escapeCreeper(seen) != null) return new Option("retreat", null, why + ": creeper close");
		if (canShelter) return new Option("shelter", "heal", why + ": wall in and heal");
		Perception.Seen hostile = seen.nearestHostile();
		// Keep the four-block danger zone even if the fight reflex's distance gene is lower.
		if (noCloseRetreat && hostile != null && hostile.dist() <= 4 && !hostile.type().equals("creeper"))
			return new Option("attack", hostile.type(), why + ": too close to turn our back");
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
				// Lake and river beds are often gravel, seen through clear water.
				case "flint" -> "gravel,water";
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
		// Trading in the Nether: the gold helmet beats any helmet (piglins attack without gold).
		if (id.equals("golden_helmet") && Tune.on("route.barter") && Mc.player() != null && Mc.dimension().equals("the_nether")) return 9;
		if (id.startsWith("leather_")) return 0;
		if (id.startsWith("golden_") || id.startsWith("chainmail_")) return 1;
		if (id.startsWith("iron_")) return 2;
		if (id.startsWith("diamond_")) return 3;
		if (id.startsWith("netherite_")) return 4;
		if (id.equals("turtle_helmet")) return 2;
		return -1;
	}
}
