package io.github.plrlr.autopilot.brains;

import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.plan.Option;

import java.util.List;
import java.util.Set;

/**
 * The bot's one brain. The planner lists the options in the rules' order; the learned model
 * (trained by the loop on every game played) may re-rank them, as far as the loop's genes allow
 * (learned.weight, learned.explore; both 0 = exactly the rules). Emergencies stay the rules' call,
 * except that the learned danger model (gene safety.hazard) may swap any pick for one clearly less
 * likely to get the bot killed.
 *
 * Runs on the game thread in microseconds: no network, no waiting.
 */
public final class Brain {
	/** What was picked, where it stood in the rules' order, how likely it was, and who decided. */
	public record Choice(Option option, int index, double propensity, String by, String why) {}

	private final Learned learned;

	public Brain(Learned learned) {
		this.learned = learned;
	}

	public Choice decide(List<Option> options, double[] features, Set<String> urgent) {
		Choice c = rank(options, features, urgent);
		Learned.Safer s = learned.safer(options, c.index(), features);
		if (s == null) return c;
		return new Choice(options.get(s.index()), s.index(), c.propensity(), "safety",
				String.format("safer: death risk %.0f%% vs %.0f%% for %s", 100 * s.riskSafer(), 100 * s.riskPlanned(), c.option().label()));
	}

	private Choice rank(List<Option> options, double[] features, Set<String> urgent) {
		boolean active = Tune.get("learned.weight") > 0 || Tune.get("learned.explore") > 0;
		if (!active || features == null) return new Choice(options.get(0), 0, 1, "rules", options.get(0).why());
		Learned.Pick p = learned.choose(options, features, urgent);
		Option o = options.get(p.index());
		boolean rules = p.index() == 0 && p.why().equals("rules");
		return new Choice(o, p.index(), p.propensity(), rules ? "rules" : "learned", rules ? o.why() : p.why());
	}

	/** For reflexes: the option to take among the rules' pick (first) and its alternatives. */
	public Choice decideReflex(List<Option> choices, double[] features) {
		Learned.Safer s = learned.safer(choices, 0, features);
		if (s == null) return new Choice(choices.get(0), 0, 1, "rules", choices.get(0).why());
		return new Choice(choices.get(s.index()), s.index(), 1, "safety",
				String.format("safer: death risk %.0f%% vs %.0f%% for %s", 100 * s.riskSafer(), 100 * s.riskPlanned(), choices.get(0).label()));
	}

	/**
	 * Brain v2 (gene brain.skill_stats): options that clearly fail in this context (8+ tries and
	 * even the optimistic bound on success under 15%, loop data and this game's tries together) go
	 * behind the ones that work, in order. Emergencies keep their place: the rules own them. Replaces
	 * "fail three times, then hide it for a minute" with what every earlier game already showed.
	 */
	public static List<Option> demoteFailing(List<Option> options, Set<String> urgent, List<String> contexts) {
		var specs = io.github.plrlr.autopilot.skills.SkillSpecs.byKey();
		List<Option> good = new java.util.ArrayList<>(options.size()), bad = new java.util.ArrayList<>();
		for (Option o : options) {
			String key = Learned.key(o);
			var spec = specs.get(key);
			SkillStats.Estimate e = SkillStats.shared().estimate(key, contexts, spec == null ? 0.5 : spec.success(), spec == null ? 60 : spec.seconds());
			(!urgent.contains(o.label()) && e.clearlyBad() ? bad : good).add(o);
		}
		// Never demote everything: with nothing that works, the rules' order stands.
		if (good.isEmpty()) return options;
		good.addAll(bad);
		return good;
	}

	/** "rules" or "learned m31" for the HUD. */
	public String label() {
		boolean active = Tune.get("learned.weight") > 0 || Tune.get("learned.explore") > 0;
		String safety = Tune.on("safety.hazard") ? " + safety" : "";
		return (active ? "learned " + learned.modelId() : "rules") + safety;
	}
}
