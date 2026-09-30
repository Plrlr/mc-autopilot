package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.WorldMemory;

import java.util.List;

/** An offer sees the rules' choices so far without being able to change their list. */
public record Context(Option main, List<Option> options, WorldMemory memory,
		String lastSkill, String lastArg, String lastCode, String lastDetail, double lastEndSeconds) {
	public Context {
		options = List.copyOf(options);
	}
}
