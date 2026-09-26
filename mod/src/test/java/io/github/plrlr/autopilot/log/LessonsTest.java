package io.github.plrlr.autopilot.log;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonsTest {
	@Test
	void numbersDontSplitReasons() {
		assertEquals("no progress for # s (got #/#)", Lessons.normalize("no progress for 60 s (got 2/9)"));
	}

	@Test
	void interruptionsAreNotFailures() {
		Lessons l = new Lessons(null);
		for (int i = 0; i < 6; i++) l.record("collect log", false, "interrupted: brain chose craft planks:4", 20);
		assertEquals(0, l.failRate("collect log"));
		assertTrue(l.worst(3).isEmpty());
	}

	@Test
	void reportsWhatKeepsFailingAndWhy() {
		Lessons l = new Lessons(null);
		for (int i = 0; i < 5; i++) l.record("craft furnace", false, "couldn't place the crafting_table", 1);
		l.record("craft furnace", false, "missing ingredients for furnace", 1);
		for (int i = 0; i < 3; i++) l.record("craft furnace", true, "crafted 1 furnace", 1);
		for (int i = 0; i < 9; i++) l.record("craft planks", true, "crafted 4 planks", 0.5);
		List<String> worst = l.worst(3);
		assertEquals(List.of("craft furnace failed 6 of 9 (mostly: couldn't place the crafting_table)"), worst);
		assertEquals(6 / 9.0, l.failRate("craft furnace"), 1e-9);
	}

	@Test
	void tooFewTriesToJudge() {
		Lessons l = new Lessons(null);
		l.record("attack cow", false, "no cow in sight", 0);
		assertEquals(0, l.failRate("attack cow"));
	}
}
