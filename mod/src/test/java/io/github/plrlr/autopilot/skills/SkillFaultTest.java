package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.plan.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillFaultTest {
	private static class Broken extends Skill {
		boolean cleaned;
		@Override public String name() { return "broken"; }
		@Override protected void start() { throw new IllegalStateException("unexpected"); }
		@Override protected void tick() {}
		@Override protected void cleanup() { cleaned = true; }
	}

	private static class Parent extends Composite {
		int children;
		final Skill child;
		Parent(Skill child) { this.child = child; }
		@Override public String name() { return "parent"; }
		@Override protected void start() { tick(); }
		@Override protected Option next() { return new Option("broken", null, "test child"); }
		@Override protected void finish() { fail(Fail.NO_PROGRESS, "unexpected finish"); }
		@Override protected Skill createChild(String name) { children++; return child; }
		@Override protected void cleanup() {}
	}

	@Test
	void unexpectedStartErrorsCleanUpAndUnwind() {
		Broken child = new Broken();
		assertThrows(Skill.Fault.class, () -> child.begin(null, null));
		assertEquals(Fail.ERROR, child.result().code());
		assertTrue(child.cleaned);
		assertThrows(Skill.Fault.class, child::update, "a completed error cannot be quietly consumed");
	}

	@Test
	void nestedErrorsBypassEveryParentsRetryBudget() {
		Broken child = new Broken();
		Parent inner = new Parent(child), outer = new Parent(inner);
		assertThrows(Skill.Fault.class, () -> outer.begin(null, null));
		assertEquals(Fail.ERROR, inner.result().code());
		assertEquals(Fail.ERROR, outer.result().code());
		assertEquals(1, inner.children);
		assertEquals(1, outer.children);
		assertTrue(child.cleaned);
	}

	@Test
	void ordinaryFailuresRemainResultsForThePlanner() {
		Broken child = new Broken() {
			@Override protected void start() { fail(Fail.NEED_ITEM, "no tool"); }
		};
		assertDoesNotThrow(() -> child.begin(null, null));
		assertEquals(Fail.NEED_ITEM, child.result().code());
		assertTrue(child.cleaned);
	}
}
