package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.WorldMemory;

import java.util.List;

/**
 * An offer sees the rules' choices so far without being able to change their list. main is the step
 * the rules would run next: the goal's step, or shore while the bot is in water with ground work to
 * do (then options holds only the safety options: a fight, a retreat, eating).
 */
public record Context(Option main, List<Option> options, WorldMemory memory,
		String lastSkill, String lastArg, String lastCode, String lastDetail, double lastEndSeconds) {
	public Context {
		options = List.copyOf(options);
	}
}
