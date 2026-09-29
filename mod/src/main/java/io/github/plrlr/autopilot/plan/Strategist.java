package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.brains.SkillStats;
import io.github.plrlr.autopilot.skills.SkillSpec;
import io.github.plrlr.autopilot.skills.SkillSpecs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Brain v2's strategist (docs/brain-v2.md): picks the route to what the goal needs by expected
 * time, and hands the planner the route's next step.
 *
 * Every way to get a key item or fact is a route spec (skills/SkillSpecs: cast the portal, mine
 * obsidian with a diamond pickaxe, a ruined portal; barter, the boat trap, endermen; ...). Plain
 * crafting, smelting, mining and hunting come from TechTree. A route's cost is
 *
 *   what it needs (recursively)  +  E[seconds + P(death) x death cost] / P(success)
 *
 * with success, seconds and deaths from the learned skill stats (this game's tries count at
 * once), so a route that keeps failing stops being picked and one that starts working gets
 * picked, without a gene flip. It's AND-OR planning over a small graph (tens of nodes, memoized):
 * microseconds, on the game thread, at each decision.
 *
 * When the cheapest route's next step is plain item work (planks, iron, a bucket), the planner's
 * own itemStep makes it: that code already knows tables, furnaces and pickaxe tiers.
 */
public final class Strategist {
	/** What the strategist reads: the inventory, facts, genes and learned stats. Faked in tests. */
	public interface World {
		int have(String item);

		Set<String> facts();

		boolean geneOn(String gene);

		/** Stats contexts right now: "overworld", "night", "under", "nether", "end". */
		List<String> contexts();

		SkillStats.Estimate estimate(String key, double priorP, double priorSeconds);

		double deathSeconds();
	}

	/** Makes the step for plain item work (Planner.itemStep); null when it can't. */
	public interface ItemStepper {
		Option step(String item, int count);
	}

	public static final double INF = Double.POSITIVE_INFINITY;
	private static final int MAX_DEPTH = 8;

	/** Seconds per item for plain mining, before any stats (a player's pace, roughly). */
	private static final Map<String, Double> MINE_SECONDS = Map.ofEntries(
			Map.entry("log", 8.0), Map.entry("stone", 3.0), Map.entry("dirt", 2.0), Map.entry("sand", 3.0),
			Map.entry("coal", 25.0), Map.entry("raw_iron", 40.0), Map.entry("raw_gold", 60.0), Map.entry("gold_nugget", 12.0),
			Map.entry("diamond", 240.0), Map.entry("flint", 25.0), Map.entry("obsidian", 20.0));

	/**
	 * Plain sources that only exist where something was seen first: obsidian only where lava was
	 * hardened (or a ruined portal stands), blazes only in a fortress. Without these, "mine 10
	 * obsidian" looked cheap and beat every portal route.
	 */
	private static final Map<String, String> MINE_FACT = Map.of("obsidian", "obsidian_known");
	private static final Map<String, String> MOB_FACT = Map.of("blaze", "fortress_known", "blaze_rod", "fortress_known");

	/** Items the inventory counts under a group name, and the item that group is really made of. */
	private static final Map<String, String> ALIAS = Map.of("throwaway", "stone", "cobblestone", "stone", "food", "cooked_beef");

	private final World world;
	private final ItemStepper items;
	private final Map<String, Double> memo = new HashMap<>();
	private final Set<String> visiting = new HashSet<>();
	/** Times a cycle was cut short; a cost computed across a cut isn't cached (it may be too high). */
	private int cuts;
	private final List<String> chain = new ArrayList<>();
	/** The route specs the last step descended through, outermost first. */
	private final List<SkillSpec> route = new ArrayList<>();

	public Strategist(World world, ItemStepper items) {
		this.world = world;
		this.items = items;
	}

	/** The next step toward a fact ("in_nether", "frame_known", "dragon_dead"), or null if no route. */
	public Step towardFact(String fact) {
		reset();
		double c = factCost(fact, 0);
		if (c == INF || c == 0) return null;
		Option o = stepFact(fact, 0);
		return o == null ? null : new Step(o, c, String.join(" < ", chain), List.copyOf(route));
	}

	/** The next step toward n of an item, or null if no route (or we have them). */
	public Step towardItem(String item, int n) {
		reset();
		double c = itemCost(item, n, 0);
		if (c == INF || c == 0) return null;
		Option o = stepItem(item, n, 0);
		return o == null ? null : new Step(o, c, String.join(" < ", chain), List.copyOf(route));
	}

	/**
	 * A step, the whole route's expected seconds, the route as one line for the log, and the route
	 * specs chosen on the way (outermost first), so the planner can hand a chosen route to its own
	 * tuned code for it (the cast portal's preparation, bartering, the blaze fight).
	 */
	public record Step(Option option, double seconds, String plan, List<SkillSpec> route) {
		public boolean uses(String skill, String arg) {
			for (SkillSpec s : route)
				if (s.skill().equals(skill) && java.util.Objects.equals(s.arg(), arg)) return true;
			return false;
		}
	}

	private void reset() {
		memo.clear();
		visiting.clear();
		cuts = 0;
		chain.clear();
		route.clear();
	}

	// ------------------------------------------------------------------ costs

	double factCost(String fact, int depth) {
		if (world.facts().contains(fact)) return 0;
		String k = "f:" + fact;
		Double m = memo.get(k);
		if (m != null) return m;
		if (depth > MAX_DEPTH || !visiting.add(k)) {
			cuts++;
			return INF;
		}
		int cuts0 = cuts;
		double best = INF;
		for (SkillSpec s : SkillSpecs.making(fact)) best = Math.min(best, routeCost(s, 1, depth));
		visiting.remove(k);
		if (cuts == cuts0) memo.put(k, best);
		return best;
	}

	double itemCost(String item, int n, int depth) {
		int missing = n - world.have(item);
		if (missing <= 0) return 0;
		String k = "i:" + item + ":" + n;
		Double m = memo.get(k);
		if (m != null) return m;
		if (depth > MAX_DEPTH || !visiting.add(k)) {
			cuts++;
			return INF;
		}
		int cuts0 = cuts;
		double best = INF;
		for (SkillSpec s : SkillSpecs.giving(item)) {
			int runs = (int) Math.ceil(missing / (double) Math.max(1, s.gives().get(item)));
			best = Math.min(best, routeCost(s, runs, depth));
		}
		best = Math.min(best, plainCost(item, missing, depth));
		visiting.remove(k);
		if (cuts == cuts0) memo.put(k, best);
		return best;
	}

	/** A route spec run `runs` times: its needs once, then its own expected cost per run. */
	double routeCost(SkillSpec s, int runs, int depth) {
		if (s.gene() != null && !world.geneOn(s.gene())) return INF;
		double c = 0;
		for (var e : s.needs().entrySet()) {
			c += itemCost(e.getKey(), e.getValue(), depth + 1);
			if (c == INF) return INF;
		}
		for (String f : s.facts()) {
			c += factCost(f, depth + 1);
			if (c == INF) return INF;
		}
		return c + runs * exec(s);
	}

	double exec(SkillSpec s) {
		SkillStats.Estimate e = world.estimate(s.key(), s.success(), s.seconds());
		// Gene brain.thompson: cost with a drawn success chance, so thinly tried routes get tried.
		if (world.geneOn("brain.thompson")) e = io.github.plrlr.autopilot.brains.Thompson.draw(s.key(), e, System.currentTimeMillis());
		return e.cost(world.deathSeconds());
	}

	/** Crafting, smelting, mining or hunting, from TechTree; the cheapest that applies. */
	private double plainCost(String item, int missing, int depth) {
		String real = ALIAS.getOrDefault(item, item);
		double best = INF;
		TechTree.Recipe r = TechTree.CRAFT.get(real);
		if (r != null) {
			int crafts = (int) Math.ceil(missing / (double) r.yield());
			double c = 3.0 * crafts;
			for (var e : r.in().entrySet()) {
				c += itemCost(e.getKey(), e.getValue() * crafts, depth + 1);
				if (c == INF) break;
			}
			best = Math.min(best, c);
		}
		String raw = TechTree.SMELT.get(real);
		if (raw != null) best = Math.min(best, 10.0 * missing + itemCost(raw, missing, depth + 1));
		TechTree.Source src = TechTree.MINE.get(real);
		if (src != null) {
			double tool = src.tier() < 0 ? 0 : itemCost(TechTree.pickaxeForTier(src.tier()), 1, depth + 1);
			if (MINE_FACT.containsKey(real)) tool += factCost(MINE_FACT.get(real), depth + 1);
			SkillStats.Estimate e = world.estimate("collect:" + real, 0.7, MINE_SECONDS.getOrDefault(real, 20.0) * missing);
			best = Math.min(best, tool + MINE_SECONDS.getOrDefault(real, 20.0) * missing / Math.max(0.05, e.p()));
		}
		List<String> mobs = TechTree.MOB.get(real);
		if (mobs != null && !mobs.isEmpty()) {
			SkillStats.Estimate e = world.estimate("attack:" + mobs.get(0), 0.6, 30);
			double where = MOB_FACT.containsKey(real) ? factCost(MOB_FACT.get(real), depth + 1) : 0;
			best = Math.min(best, where + e.cost(world.deathSeconds()) * missing);
		}
		return best;
	}

	// ------------------------------------------------------------------ steps (descend the cheapest route)

	private Option stepFact(String fact, int depth) {
		if (world.facts().contains(fact) || depth > MAX_DEPTH) return null;
		SkillSpec best = cheapest(SkillSpecs.making(fact), 1, depth);
		if (best == null) return null;
		chain.add(fact + " via " + label(best));
		route.add(best);
		return stepThrough(best, depth);
	}

	private Option stepItem(String item, int n, int depth) {
		int missing = n - world.have(item);
		if (missing <= 0 || depth > MAX_DEPTH) return null;
		SkillSpec best = null;
		double bc = plainCost(item, missing, depth);
		for (SkillSpec s : SkillSpecs.giving(item)) {
			int runs = (int) Math.ceil(missing / (double) Math.max(1, s.gives().get(item)));
			double c = routeCost(s, runs, depth);
			if (c < bc) {
				bc = c;
				best = s;
			}
		}
		if (best == null) {
			// Plain work that needs a place seen first: find the place (that's the step), then the planner mines or hunts.
			String real = ALIAS.getOrDefault(item, item);
			String where = TechTree.MINE.containsKey(real) ? MINE_FACT.get(real) : MOB_FACT.get(real);
			if (where != null && !world.facts().contains(where)) {
				chain.add(n + " " + item + " (plain, needs " + where + ")");
				return stepFact(where, depth + 1);
			}
			chain.add(n + " " + item + " (plain)");
			return items.step(real, n);
		}
		chain.add(n + " " + item + " via " + label(best));
		route.add(best);
		return stepThrough(best, depth);
	}

	/** The route's first unmet need, else the route's own skill. */
	private Option stepThrough(SkillSpec s, int depth) {
		for (var e : s.needs().entrySet()) {
			if (world.have(e.getKey()) < e.getValue()) return stepItem(e.getKey(), e.getValue(), depth + 1);
		}
		for (String f : s.facts()) {
			if (!world.facts().contains(f)) return stepFact(f, depth + 1);
		}
		return new Option(s.skill(), s.arg(), "route: " + label(s));
	}

	private SkillSpec cheapest(List<SkillSpec> specs, int runs, int depth) {
		SkillSpec best = null;
		double bc = INF;
		for (SkillSpec s : specs) {
			double c = routeCost(s, runs, depth);
			if (c < bc) {
				bc = c;
				best = s;
			}
		}
		return best;
	}

	private String label(SkillSpec s) {
		SkillStats.Estimate e = world.estimate(s.key(), s.success(), s.seconds());
		return String.format("%s (p %.2f, %.0f s)", s.key(), e.p(), e.seconds());
	}
}
