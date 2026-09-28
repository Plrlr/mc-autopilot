package io.github.plrlr.autopilot.brains;

import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.plan.Option;

import java.util.List;
import java.util.Set;

/**
 * The bot's one brain. The planner lists the options in the rules' order; the learned model
 * (trained by the loop on every game played) may re-rank them, as far as the loop's genes allow
 * (learned.weight, learned.explore; both 0 = exactly the rules). Emergencies stay the rules' call.
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
		boolean active = Tune.get("learned.weight") > 0 || Tune.get("learned.explore") > 0;
		if (!active || features == null) return new Choice(options.get(0), 0, 1, "rules", options.get(0).why());
		Learned.Pick p = learned.choose(options, features, urgent);
		Option o = options.get(p.index());
		boolean rules = p.index() == 0 && p.why().equals("rules");
		return new Choice(o, p.index(), p.propensity(), rules ? "rules" : "learned", rules ? o.why() : p.why());
	}

	/** "rules" or "learned m31" for the HUD. */
	public String label() {
		boolean active = Tune.get("learned.weight") > 0 || Tune.get("learned.explore") > 0;
		return active ? "learned " + learned.modelId() : "rules";
	}
}
