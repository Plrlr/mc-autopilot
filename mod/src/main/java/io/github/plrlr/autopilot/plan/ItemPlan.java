package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.skills.SmeltSkill;
import io.github.plrlr.autopilot.state.Perception;
import java.util.List;
final class ItemPlan {
	/**
	 * What to do next to own `count` of `item`. Walks the tech tree: craft if ingredients exist,
	 * otherwise get the first missing ingredient; smelt, mine (with the right pickaxe) or hunt.
	 */
	Option itemStep(Planner p, String item, int count, int depth) {
		if (depth > 10) return null;
		int have = Goal.have(item);
		if (have >= count) return null;
		int missing = count - have;

		if (item.equals("food")) return p.goalStep(Goal.FOOD, Perception.look(32), 0);
		if (item.equals("water_bucket")) {
			if (Goal.have("bucket") == 0) return itemStep(p,"bucket", 1, depth + 1);
			if (p.memory.nearest("water") != null) return new Option("fill_bucket", "water", "fill the bucket with water");
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
					Option o = itemStep(p,ing.getKey(), need, depth + 1);
					if (o != null) return o;
				}
			}
			if (r.table() && !p.tableAvailable()) {
				Option o = itemStep(p,"crafting_table", 1, depth + 1);
				if (o != null) return o;
			}
			return new Option("craft", item + ":" + crafts * r.yield(), "have the ingredients for " + item);
		}

		if (TechTree.SMELT.containsKey(item)) return smeltStep(p,item, missing, depth);

		TechTree.Source src = TechTree.MINE.get(item);
		if (src != null) {
			if (Items2.bestTier("pickaxe") < src.tier()) {
				return itemStep(p,TechTree.pickaxeForTier(src.tier()), 1, depth + 1);
			}
			if (item.equals("obsidian") && p.memory.nearest("obsidian") == null) {
				// Make it like a player: water bucket poured beside a lava pool hardens the lava.
				if (Mc.count("water_bucket") == 0) {
					Option o = itemStep(p,"water_bucket", 1, depth + 1);
					if (o != null) return o;
				}
				if (p.memory.nearest("lava") != null) return new Option("make_obsidian", "obsidian:" + missing, "harden lava with water into obsidian");
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
				case "raw_iron" -> Math.max(0, Planner.ironStillNeeded() - Mc.count("iron_ingot") - have - missing);
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

	Option smeltStep(Planner p, String item, int missing, int depth) {
		String input = TechTree.SMELT.get(item);
		if (input == null) return null;
		// A load of ours is cooking: do useful work nearby until it's done, then collect it.
		SmeltSkill.Job job = SmeltSkill.job(item);
		if (job != null && Mc.count(input) == 0) {
			if (!job.ready()) {
				Option side = p.sideWork();
				if (side != null) return new Option(side.skill(), side.arg(), side.why() + " while the furnace works");
			}
			return new Option("smelt", item + ":" + job.count(), "collect the " + item + " from the furnace");
		}
		// Smelt every raw iron we carry at once: one furnace load instead of several.
		if (input.equals("raw_iron") && !(Tune.on("gear.shield_early") && Mc.count("shield") == 0 && missing == 1))
			missing = Math.max(missing, Mc.count("raw_iron"));
		if (Mc.count(input) < missing) {
			Option o = itemStep(p,input, missing, depth + 1);
			if (o != null) return o;
		}
		// Fuel: coal smelts 8 items, planks 1.5. Use whatever we have, else make planks.
		int coalNeed = (missing + 7) / 8;
		int planksNeed = (missing * 2 + 2) / 3;
		boolean fuelOk = Mc.count("coal") >= coalNeed || Mc.count("planks") >= planksNeed
				|| (!input.equals("log") && Mc.count("log") >= coalNeed * 2);
		if (!fuelOk) {
			Option o = itemStep(p,"planks", planksNeed, depth + 1);
			if (o != null) return o;
		}
		if ((p.memory.nearestStation("furnace") == null && Mc.count("furnace") == 0) || SmeltSkill.needSecondFurnace(p.memory, item)) {
			Option o = itemStep(p,"furnace", 1, depth + 1);
			if (o != null) return o;
		}
		return new Option("smelt", item + ":" + missing, "smelt " + input + " into " + item);
	}

}
