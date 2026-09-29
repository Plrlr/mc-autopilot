package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How well each action works, learned two ways at once:
 *
 *   - from every game the loop has played (learned.json "skills", scripts/loop/skillstats.py):
 *     tries, successes, deaths and seconds per action key, overall and per context
 *     ("build_portal@nether", "collect:raw_iron@under");
 *   - from this game, as each skill ends (record): a cast that failed three times at this pool
 *     counts right away, twice as much as a try in another world.
 *
 * Both are smoothed toward the route spec's prior (a Beta prior worth PRIOR_N tries), so a new
 * skill starts where its author guessed and a key with hundreds of tries speaks for itself.
 * The strategist costs routes with it; the tactician demotes options that clearly don't work here.
 *
 * Plain arithmetic over small maps: microseconds, any thread (loads swap the table whole).
 */
public final class SkillStats {
	/** The prior counts as this many tries. */
	static final double PRIOR_N = 4;
	/** A try in this game counts this much more than one from the loop's other worlds. */
	static final double LOCAL_WEIGHT = 2;
	/** A context needs this many tries before it overrules the overall numbers. */
	static final int CONTEXT_MIN = 8;

	/** Counts for one key (or key@context). */
	record Counts(double n, double ok, double died, double okSecs, double spent) {
		Counts plus(Counts o) {
			return new Counts(n + o.n, ok + o.ok, died + o.died, okSecs + o.okSecs, spent + o.spent);
		}
	}

	/**
	 * What the brain uses: success chance with its 90% bounds, seconds of a success, seconds a try
	 * takes on average (failures included), death chance per try.
	 */
	public record Estimate(double p, double pLow, double pHigh, double seconds, double spent, double death, double n) {
		/**
		 * Expected seconds to get one success: tries needed (1/p) times what a try costs, its mean
		 * time plus its death risk (a death costs deathSeconds). Using the mean try, not the time of a
		 * success, matters: a cast that fails "no flat ground" fails in seconds, and charging each
		 * failure a full cast's time made casting look ~5x dearer than it is (smoke run, 2026-09-29).
		 */
		public double cost(double deathSeconds) {
			double pp = Math.max(0.02, p);
			return (spent + death * deathSeconds) / pp;
		}

		/** Clearly not working: enough tries and even the optimistic bound is low. */
		public boolean clearlyBad() {
			return n >= CONTEXT_MIN && pHigh < 0.15;
		}
	}

	private static final SkillStats SHARED = new SkillStats();

	/** The one table the game uses (the learned model loads into it; skills end into it). */
	public static SkillStats shared() {
		return SHARED;
	}

	private volatile Map<String, Counts> loop = Map.of();
	private final Map<String, Counts> local = new ConcurrentHashMap<>();

	/** Loads the "skills" object of learned.json (null or missing: no loop data). */
	public void load(JsonObject skills) {
		Map<String, Counts> m = new HashMap<>();
		if (skills != null) {
			for (Map.Entry<String, JsonElement> e : skills.entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				double n = num(o, "n"), ok = num(o, "ok");
				double sec = o.has("sec") && !o.get("sec").isJsonNull() ? o.get("sec").getAsDouble() : 0;
				m.put(e.getKey(), new Counts(n, ok, num(o, "died"), sec * ok, num(o, "spent") * n));
			}
		}
		loop = Map.copyOf(m);
	}

	/** Number of keys the loop's table holds (for the log line). */
	public int loopKeys() {
		return loop.size();
	}

	/** A new world: this game's counts start over. */
	public void clearLocal() {
		local.clear();
	}

	/** One skill ended in this game. contexts: "overworld", "night", "under"... (see contexts()). */
	public void record(String key, List<String> contexts, boolean ok, boolean died, double seconds) {
		Counts c = new Counts(LOCAL_WEIGHT, ok ? LOCAL_WEIGHT : 0, died ? LOCAL_WEIGHT : 0, ok ? seconds * LOCAL_WEIGHT : 0, seconds * LOCAL_WEIGHT);
		local.merge(key, c, Counts::plus);
		for (String ctx : contexts) local.merge(key + "@" + ctx, c, Counts::plus);
	}

	/**
	 * The estimate for a key in the current contexts. priorP and priorSeconds come from the
	 * route spec (or 0.5 and 60 when there's none). The most specific context with enough tries
	 * wins; underground and night say more about a try than the dimension does.
	 */
	public Estimate estimate(String key, List<String> contexts, double priorP, double priorSeconds) {
		Counts best = counts(key);
		for (String ctx : contexts) {
			Counts c = counts(key + "@" + ctx);
			if (c != null && c.n() >= CONTEXT_MIN && (best == null || !ctx.equals("overworld"))) best = c;
		}
		if (best == null) best = new Counts(0, 0, 0, 0, 0);
		double a = priorP * PRIOR_N + best.ok(), b = (1 - priorP) * PRIOR_N + best.n() - best.ok();
		double p = a / (a + b);
		double sd = Math.sqrt(p * (1 - p) / (a + b + 1));
		double secs = best.ok() > 0 ? (best.okSecs() + priorSeconds * 2) / (best.ok() + 2) : priorSeconds;
		double death = (best.died() + 0.02 * PRIOR_N) / (best.n() + PRIOR_N);
		// Mean seconds per try, smoothed toward the prior (a prior try takes as long as a success).
		double spent = (best.spent() + priorSeconds * PRIOR_N) / (best.n() + PRIOR_N);
		return new Estimate(p, Math.max(0, p - 1.645 * sd), Math.min(1, p + 1.645 * sd), secs, spent, death, best.n());
	}

	private Counts counts(String key) {
		Counts l = loop.get(key), h = local.get(key);
		if (l == null) return h;
		return h == null ? l : l.plus(h);
	}

	private static double num(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0;
	}

	/** The contexts skillstats.py uses, from the state as the game sees it. */
	public static List<String> contexts(String dimension, boolean night, boolean underground) {
		List<String> out = new java.util.ArrayList<>(3);
		switch (dimension) {
			case "the_nether" -> out.add("nether");
			case "the_end" -> out.add("end");
			default -> {
				out.add("overworld");
				if (night) out.add("night");
			}
		}
		if (underground) out.add("under");
		return out;
	}
}
