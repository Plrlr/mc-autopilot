package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;

@FunctionalInterface
public interface Offer {
	Option offer(Context c);
}
