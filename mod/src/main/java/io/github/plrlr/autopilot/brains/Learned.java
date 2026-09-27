package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.log.Checkpoints;
import io.github.plrlr.autopilot.plan.Goal;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The learned brain: our own model, trained only on this bot's trial runs (scripts/loop/train.py).
 * For each kind of action ("collect:log", "explore:lava", "attack:zombie", ...) a linear model
 * predicts the progress the bot makes in the next two minutes after choosing it in a given state
 * (inventory value, milestones and checkpoints gained, minus deaths). It re-ranks the rules'
 * options by that prediction:
 *
 *   score(i) = -i + learned.weight * (Q(option i) - Q(option 0)) / scale
 *
 * so learned.weight 0 is exactly the rules' order, and the loop only raises it if runs get better.
 * Life-or-death options (the planner's urgent list) are never overruled. With learned.explore > 0
 * it sometimes tries another option on purpose, and logs the probability it had (the propensity),
 * so training can tell deliberate tries from the rules' habits.
 *
 * Pure arithmetic on a few dozen numbers: microseconds on the game thread, no network.
 */
public final class Learned {
	/** Feature names, in order. The trainer reads them from the log; a model with other names is ignored. */
	public static final List<String> FEATURES = List.of(
			"hp", "food", "armor", "night", "underground", "y", "nether", "end",
			"pick", "sword", "shield", "log", "planks", "blocks", "iron", "raw_iron", "coal", "cooked",
			"raw_meat", "buckets", "water_bucket", "lava_bucket", "flint_steel", "obsidian",
			"hostiles8", "hostile_dist", "lava_known", "water_known", "deaths", "milestone", "checkpoints", "goal");

	private final Random rng = new Random();
	private Map<String, double[]> weights = Map.of();
	private double scale = 1;
	private String modelId = "none";

	public String modelId() {
		return modelId;
	}

	/** Loads mc-autopilot/learned.json (or the test harness's file). Returns a line for the log. */
	public String load(Path file) {
		weights = Map.of();
		modelId = "none";
		if (file == null || !Files.exists(file)) return "learned model: none";
		try {
			JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray names = o.getAsJsonArray("features");
			if (names == null || names.size() != FEATURES.size()) return "learned model: ignored (feature list changed)";
			for (int i = 0; i < names.size(); i++)
				if (!names.get(i).getAsString().equals(FEATURES.get(i))) return "learned model: ignored (feature " + i + " differs)";
			Map<String, double[]> w = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("keys").entrySet()) {
				JsonArray a = e.getValue().getAsJsonArray();
				if (a.size() != FEATURES.size() + 1) continue; // bias + one weight per feature
				double[] v = new double[a.size()];
				for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsDouble();
				w.put(e.getKey(), v);
			}
			weights = w;
			scale = o.has("scale") ? Math.max(1e-6, o.get("scale").getAsDouble()) : 1;
			modelId = o.has("id") ? o.get("id").getAsString() : "unnamed";
			return "learned model: " + modelId + " (" + w.size() + " action kinds)";
		} catch (Exception e) {
			return "learned model: couldn't read (" + e + ")";
		}
	}

	/** The state as numbers, roughly 0..1 each. Game thread only. */
	public static double[] features(WorldMemory memory, Perception seen, int deaths, int milestone, Goal goal) {
		LocalPlayer pl = Mc.player();
		String dim = Mc.dimension();
		Perception.Seen h = seen.nearestHostile();
		double[] x = {
				pl.getHealth() / 20.0, pl.getFoodData().getFoodLevel() / 20.0, pl.getArmorValue() / 20.0,
				Mc.isNight() ? 1 : 0, pl.level().canSeeSky(pl.blockPosition().above()) ? 0 : 1, pl.getBlockY() / 64.0,
				dim.equals("the_nether") ? 1 : 0, dim.equals("the_end") ? 1 : 0,
				(Items2.bestTier("pickaxe") + 1) / 5.0, (Items2.bestTier("sword") + 1) / 5.0, Mc.count("shield") > 0 ? 1 : 0,
				cap(Mc.count("log"), 16), cap(Mc.count("planks"), 16), cap(Mc.count("throwaway"), 32),
				cap(Mc.count("iron_ingot"), 16), cap(Mc.count("raw_iron"), 16), cap(Mc.count("coal"), 16), cap(Mc.count("food"), 16),
				cap(Mc.count("meat"), 16), cap(Goal.have("bucket"), 2), Mc.count("water_bucket") > 0 ? 1 : 0,
				Mc.count("lava_bucket") > 0 ? 1 : 0, Mc.count("flint_and_steel") > 0 ? 1 : 0, cap(Mc.count("obsidian"), 10),
				cap(seen.hostilesWithin(8), 4), h == null ? 1 : Math.min(1, h.dist() / 16.0),
				memory.nearest("lava") != null ? 1 : 0, memory.nearest("water") != null ? 1 : 0,
				cap(deaths, 5), milestone / 13.0, Checkpoints.summary().size() / 6.0, goal == null ? 0 : goal.milestone / 13.0};
		return x;
	}

	private static double cap(int v, int max) {
		return Math.min(v, max) / (double) max;
	}

	/** "collect log:3" -> "collect:log"; "explore cow,pig" -> "explore:cow"; "eat" -> "eat". */
	public static String key(Option o) {
		if (o.arg() == null || o.arg().isEmpty()) return o.skill();
		String a = o.arg();
		int cut = a.length();
		for (char c : new char[]{':', ',', ' '}) {
			int i = a.indexOf(c);
			if (i >= 0) cut = Math.min(cut, i);
		}
		return o.skill() + ":" + a.substring(0, cut);
	}

	private double q(Option o, double[] x) {
		double[] w = weights.get(key(o));
		if (w == null) w = weights.get(o.skill());
		if (w == null) return Double.NaN;
		double v = w[0];
		for (int i = 0; i < x.length; i++) v += w[i + 1] * x[i];
		return v;
	}

	/** The pick, the probability it had, and why (for the log). */
	public record Pick(int index, double propensity, String why) {}

	/**
	 * Chooses among the options. Returns index 0 with propensity 1 when the model has no say
	 * (weight and exploration both 0, an urgent option on top, or nothing to compare).
	 */
	public Pick choose(List<Option> options, double[] x, Set<String> urgent) {
		double weight = Tune.get("learned.weight");
		double explore = Tune.get("learned.explore");
		if (options.size() < 2 || urgent.contains(options.get(0).label()) || (weight <= 0 && explore <= 0))
			return new Pick(0, 1, "rules");
		// Candidates: the rules' first choice and the next few non-urgent options.
		int n = 0;
		int[] cand = new int[Math.min(options.size(), 5)];
		for (int i = 0; i < options.size() && n < cand.length; i++) {
			if (i > 0 && urgent.contains(options.get(i).label())) continue;
			cand[n++] = i;
		}
		int best = 0;
		String why = "rules";
		if (weight > 0 && !weights.isEmpty()) {
			double q0 = q(options.get(0), x);
			double bestScore = 0;
			for (int k = 1; k < n; k++) {
				int i = cand[k];
				double qi = q(options.get(i), x);
				if (Double.isNaN(q0) || Double.isNaN(qi)) continue;
				double score = -i + weight * (qi - q0) / scale;
				if (score > bestScore) {
					bestScore = score;
					best = i;
					why = String.format("model: +%.2f over the rules' pick", (qi - q0));
				}
			}
		}
		// Deliberate tries: with probability `explore`, one of the other candidates at random.
		if (n > 1 && explore > 0) {
			if (rng.nextDouble() < explore) {
				int i = cand[1 + rng.nextInt(n - 1)];
				double p = explore / (n - 1) + (i == best ? 1 - explore : 0);
				return new Pick(i, p, "trying another option (exploration)");
			}
			// Exploration draws from candidates 1..n-1, which include `best` when it isn't 0.
			return new Pick(best, 1 - explore + (best == 0 ? 0 : explore / (n - 1)), why);
		}
		return new Pick(best, 1, why);
	}
}
