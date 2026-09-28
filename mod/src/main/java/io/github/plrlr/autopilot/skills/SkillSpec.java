package io.github.plrlr.autopilot.skills;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What one way of getting something done needs and gives, so the strategist can reason about
 * routes (plan/Strategist): "10 obsidian" can come from casting at a lava pool, from mining a
 * hardened pool with a diamond pickaxe, or from a ruined portal, and the cheapest one depends on
 * what we carry, what we've seen, and how often each has worked (the learned skill stats).
 *
 * A spec describes an action key as the brain logs it ("build_portal", "collect:obsidian",
 * "fortress:blazes"), so the learned stats for that key are this spec's success and time.
 * Priors (seconds, success) are only used until the loop has data for the key.
 *
 * Plain data: no game calls, safe to build in unit tests.
 */
public record SkillSpec(
		/** The action key the brain logs: skill, or skill:first-word-of-arg. */
		String key,
		/** The option to start: skill name and argument. */
		String skill, String arg,
		/** early, portal, nether, stronghold, end, or any: where on the route it belongs. */
		String stage,
		/** Items that must be carried (counted like Goal.have). */
		Map<String, Integer> needs,
		/** Facts that must hold (plan/Facts names: "lava_pool", "in_nether", "fortress_known"). */
		Set<String> facts,
		/** Items it adds on success. */
		Map<String, Integer> gives,
		/** Facts it makes true on success. */
		Set<String> makes,
		/** Prior: typical seconds for one successful run. */
		double seconds,
		/** Prior: chance one run succeeds. */
		double success,
		/** The gene that must be on for this route, or null for always. */
		String gene,
		/** One line for people and logs. */
		String help) {

	public static Builder of(String skill, String arg) {
		return new Builder(skill, arg);
	}

	/** The action key for a skill and its argument, the same way Learned.key builds it. */
	public static String keyOf(String skill, String arg) {
		if (arg == null || arg.isEmpty()) return skill;
		int cut = arg.length();
		for (char c : new char[]{':', ',', ' '}) {
			int i = arg.indexOf(c);
			if (i >= 0) cut = Math.min(cut, i);
		}
		return skill + ":" + arg.substring(0, cut);
	}

	public static final class Builder {
		private final String skill, arg;
		private String stage = "any", gene, help = "";
		private final Map<String, Integer> needs = new LinkedHashMap<>(), gives = new LinkedHashMap<>();
		private final Set<String> facts = new LinkedHashSet<>(), makes = new LinkedHashSet<>();
		private double seconds = 60, success = 0.5;

		private Builder(String skill, String arg) {
			this.skill = skill;
			this.arg = arg;
		}

		public Builder stage(String s) {
			stage = s;
			return this;
		}

		/** needs("bucket", 2, "flint_and_steel", 1) */
		public Builder needs(Object... kv) {
			put(needs, kv);
			return this;
		}

		public Builder gives(Object... kv) {
			put(gives, kv);
			return this;
		}

		public Builder facts(String... f) {
			facts.addAll(Set.of(f));
			return this;
		}

		public Builder makes(String... f) {
			makes.addAll(Set.of(f));
			return this;
		}

		/** Prior seconds per successful run and chance of success per run. */
		public Builder prior(double seconds, double success) {
			this.seconds = seconds;
			this.success = success;
			return this;
		}

		public Builder gene(String g) {
			gene = g;
			return this;
		}

		public Builder help(String h) {
			help = h;
			return this;
		}

		public SkillSpec build() {
			if (seconds <= 0 || success <= 0 || success > 1) throw new IllegalArgumentException("bad prior for " + skill);
			return new SkillSpec(keyOf(skill, arg), skill, arg, stage, Collections.unmodifiableMap(needs), Collections.unmodifiableSet(facts),
					Collections.unmodifiableMap(gives), Collections.unmodifiableSet(makes), seconds, success, gene, help);
		}

		private static void put(Map<String, Integer> m, Object... kv) {
			if (kv.length % 2 != 0) throw new IllegalArgumentException("item, count pairs");
			for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
		}
	}
}
