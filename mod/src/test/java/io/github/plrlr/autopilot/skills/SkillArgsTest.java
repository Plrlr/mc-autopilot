package io.github.plrlr.autopilot.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillArgsTest {
	/** Just enough of a skill to test the argument helpers. */
	private static final class Probe extends Skill {
		Probe(String arg) {
			this.arg = arg;
		}

		@Override
		public String name() {
			return "probe";
		}

		@Override
		protected void start() {}

		@Override
		protected void tick() {}
	}

	@Test
	void splitsItemAndCount() {
		assertEquals("iron_ingot", new Probe("iron_ingot:3").argName());
		assertEquals(3, new Probe("iron_ingot:3").argCount(1));
		assertEquals("log", new Probe("log").argName());
		assertEquals(7, new Probe("log").argCount(7));
		// Bad or zero counts fall back to something usable.
		assertEquals(7, new Probe("log:x").argCount(7));
		assertEquals(1, new Probe("log:0").argCount(7));
		assertEquals("", new Probe(null).argName());
	}
}
