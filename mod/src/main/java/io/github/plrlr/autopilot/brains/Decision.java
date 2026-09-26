package io.github.plrlr.autopilot.brains;

import io.github.plrlr.autopilot.plan.Option;

/**
 * A tactician's answer. brain is who actually decided (e.g. "mock" when an LLM fell back),
 * valid is false when the LLM's answer had to be thrown away.
 */
public record Decision(Option choice, String why, String brain, boolean valid, long latencyMs, int tokensIn,
					   int tokensOut, String note) {
	public static Decision mock(Option o, String note, boolean valid) {
		return new Decision(o, o.why(), "mock", valid, 0, 0, 0, note);
	}
}
