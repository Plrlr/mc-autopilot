package io.github.plrlr.autopilot;

import java.util.HashMap;
import java.util.Map;

/**
 * Exposure evidence for the loop's races. A change behind a gene logs each moment it could act,
 * on both sides of a race: a "gene" event "<gene> on: <what>" where the new behavior runs, "<gene>
 * off: <what>" where the old one does at the same moment. A race can then tell a change that never
 * got its chance (g164's swim_out was never offered in 14 games, yet its drill "passed") from one
 * that acted and lost (scripts/loop/metrics.py, exposure).
 *
 * At most one line per gene every 10 s: a moment that lasts is one opportunity, not two hundred.
 */
public final class Exposure {
	private static final long QUIET_MS = 10_000;
	private static final Map<String, Long> LAST = new HashMap<>();

	private Exposure() {}

	/** Logs the opportunity and returns whether the gene is on (callers branch on it). */
	public static boolean mark(String gene, String what) {
		boolean on = Tune.on(gene);
		long now = System.currentTimeMillis();
		Long last = LAST.get(gene);
		if (last != null && now - last < QUIET_MS) return on;
		LAST.put(gene, now);
		try {
			Autopilot a = AutopilotMod.instance();
			if (a != null && a.log != null) a.log.event("gene", gene + (on ? " on: " : " off: ") + what);
		} catch (RuntimeException e) {
			// Evidence only: never let logging change play.
		}
		return on;
	}
}
