package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.skills.Skill;

/** Generated skills share the ordinary skill lifecycle and fail-safe cleanup. */
public abstract class EvolvedSkill extends Skill {
	public EvolvedSkill() {
		timeoutTicks = 20 * 90;
	}

	@Override
	protected void cleanup() {
		super.cleanup();
		Player.releaseAll();
	}
}
