package io.github.plrlr.autopilot.brains;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Thompson sampling for the strategist (gene brain.thompson): cost a route with a success chance
 * drawn from what we know about it, not its average. A route tried 300 times draws close to its
 * mean every time; one tried 3 times draws widely, so it gets picked now and then when it might
 * be the better way, and every such try sharpens the loop's skill stats for the next game. This
 * replaces a fixed exploration rate (learned.explore) with exploration where it's worth it.
 *
 * Draws are held for HOLD_MS per action key: the planner re-decides every second or so, and a
 * fresh draw each time would flip between routes mid-way.
 */
public final class Thompson {
	private Thompson() {}

	static final long HOLD_MS = 60_000;
	private static final Random RNG = new Random();
	private static final Map<String, double[]> HELD = new HashMap<>(); // key -> {p, until ms}

	/** The estimate with its success chance replaced by a (held) draw from Beta(ok+1, fail+1). */
	public static synchronized SkillStats.Estimate draw(String key, SkillStats.Estimate e, long nowMs) {
		double[] h = HELD.get(key);
		if (h == null || nowMs >= h[1]) {
			double ok = Math.max(0, e.p() * e.n()), fail = Math.max(0, (1 - e.p()) * e.n());
			h = new double[]{beta(ok + 1, fail + 1, RNG), nowMs + HOLD_MS};
			HELD.put(key, h);
		}
		return new SkillStats.Estimate(h[0], e.pLow(), e.pHigh(), e.seconds(), e.spent(), e.death(), e.n());
	}

	public static synchronized void clear() {
		HELD.clear();
	}

	/** Beta(a, b) from two gamma draws. */
	static double beta(double a, double b, Random rng) {
		double x = gamma(a, rng), y = gamma(b, rng);
		return x / (x + y);
	}

	/** Marsaglia and Tsang's gamma sampler (shape >= 1; the +1 above guarantees that). */
	static double gamma(double shape, Random rng) {
		double d = shape - 1.0 / 3, c = 1 / Math.sqrt(9 * d);
		while (true) {
			double x = rng.nextGaussian(), v = 1 + c * x;
			if (v <= 0) continue;
			v = v * v * v;
			double u = rng.nextDouble();
			if (Math.log(u) < 0.5 * x * x + d - d * v + d * Math.log(v)) return d * v;
		}
	}
}
