package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.skills.Skill;

/**
 * Generated skills share the ordinary skill lifecycle and fail-safe cleanup. They may never opt out
 * of safety: the reflexes (mobs, hunger) and routine re-checks always apply to them, so those two
 * switches are final here (overriding them is a compile error, not a rule a model could miss).
 */
public abstract class EvolvedSkill extends Skill {
	public EvolvedSkill() {
		timeoutTicks = 20 * 90;
	}

	@Override
	public final boolean interruptible() {
		return true;
	}

	@Override
	public final boolean ownsSafety() {
		return false;
	}

	@Override
	protected void cleanup() {
		super.cleanup();
		Player.releaseAll();
	}
}
