package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.plan.Option;

/**
 * A skill made of other skills, the way a player strings habits together: "make a bed" is hunt
 * sheep, pick up the wool, craft the bed. Each tick asks next() for the step to run now (or null
 * when there's nothing left, or a result to finish with); a running child is updated until it
 * ends, and a child that fails counts against a budget so a composite can't loop forever.
 *
 * The children are the battle-tested skills (attack, collect, craft, smelt, explore, goto...),
 * so a composite only has to get the order and the checks right (Voyager's skill library idea:
 * new skills built from verified ones).
 */
abstract class Composite extends Skill {
	private Skill child;
	private Option childOption;
	private Skill.Result last;
	private int childFails;

	/** Returned by next(): no child now, the composite's ownTick() is doing the work; ask again later. */
	protected static final Option WAIT = new Option("_wait", null, "");

	/** Child failures allowed before the composite gives up (each retry re-asks next()). */
	protected int maxChildFails = 4;

	/**
	 * The step to run now. Return null when done: then finish() says whether it worked.
	 * Called whenever no child is running. May call done()/fail() itself.
	 */
	protected abstract Option next();

	/** Called when next() returns null: done(...) or fail(...). */
	protected abstract void finish();

	/** The result of the child that ended last (null before any). */
	protected final Skill.Result lastResult() {
		return last;
	}

	protected final Option lastOption() {
		return childOption;
	}

	/** The child running now, or null (reflexes look through a composite at what it's doing). */
	public final Skill current() {
		return child;
	}

	/** A tick for the composite's own work while no child runs (default: none). True = keep ticking it. */
	protected boolean ownTick() {
		return false;
	}

	@Override
	protected void tick() {
		if (child != null) {
			child.update();
			Skill.Result r = child.result();
			if (r == null) return;
			last = r;
			child = null;
			if (!r.ok() && r.code() != Fail.ALREADY_DONE && ++childFails > maxChildFails) {
				fail(r.code() == null ? Fail.NO_PROGRESS : r.code(), childOption.label() + " failed: " + r.detail());
				return;
			}
		}
		if (ownTick()) return;
		Option o = next();
		if (result() != null || o == WAIT) return;
		if (o == null) {
			finish();
			if (result() == null) done("finished");
			return;
		}
		Skill s = createChild(o.skill());
		if (s == null) {
			fail(Fail.ERROR, "no skill named " + o.skill());
			return;
		}
		childOption = o;
		child = s;
		s.begin(memory, o.arg());
	}

	protected Skill createChild(String name) { return Skills.create(name); }

	@Override
	protected void cleanup() {
		if (child != null && child.result() == null) child.abort(Fail.INTERRUPTED, "parent " + name() + " ended");
		child = null;
		super.cleanup();
	}
}
