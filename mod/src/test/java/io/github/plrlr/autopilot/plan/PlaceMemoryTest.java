package io.github.plrlr.autopilot.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Gene plan.place_backoff: pauses that grow per action and place, and what clears them. */
class PlaceMemoryTest {
	private static final long MAX = 480 * 20;

	@Test
	void eachRepeatAtTheSamePlaceDoublesThePauseUpToTheCap() {
		PlaceMemory m = new PlaceMemory();
		long t = 0;
		assertEquals(60 * 20, m.failed("collect:log", "overworld", 3, -2, t, MAX));
		assertEquals(120 * 20, m.failed("collect:log", "overworld", 3, -2, t += 2000, MAX));
		assertEquals(240 * 20, m.failed("collect:log", "overworld", 3, -2, t += 4000, MAX));
		assertEquals(MAX, m.failed("collect:log", "overworld", 3, -2, t += 6000, MAX));
		assertEquals(MAX, m.failed("collect:log", "overworld", 3, -2, t + 10_000, MAX));
	}

	@Test
	void pausedOnlyAtThatPlaceForThatAction() {
		PlaceMemory m = new PlaceMemory();
		m.failed("shore", "overworld", 0, 0, 100, MAX);
		assertTrue(m.paused("shore", "overworld", 0, 0, 200));
		assertFalse(m.paused("shore", "overworld", 1, 0, 200), "another chunk gets a fresh chance");
		assertFalse(m.paused("shore", "the_nether", 0, 0, 200));
		assertFalse(m.paused("collect:log", "overworld", 0, 0, 200));
		assertFalse(m.paused("shore", "overworld", 0, 0, 100 + 60 * 20), "the pause ends");
	}

	@Test
	void aSuccessClearsTheCount() {
		PlaceMemory m = new PlaceMemory();
		m.failed("shore", "overworld", 0, 0, 0, MAX);
		m.failed("shore", "overworld", 0, 0, 2000, MAX);
		m.succeeded("shore", "overworld", 0, 0);
		assertFalse(m.paused("shore", "overworld", 0, 0, 2100));
		assertEquals(60 * 20, m.failed("shore", "overworld", 0, 0, 3000, MAX), "back to the first pause");
	}

	@Test
	void oldNewsIsForgottenAndTheTableIsCapped() {
		PlaceMemory m = new PlaceMemory();
		m.failed("shore", "overworld", 0, 0, 0, MAX);
		m.failed("shore", "overworld", 0, 0, 2000, MAX);
		long later = 2000 + PlaceMemory.EXPIRE_TICKS + 1;
		assertEquals(60 * 20, m.failed("shore", "overworld", 0, 0, later, MAX));
		for (int i = 0; i < PlaceMemory.CAP + 50; i++) m.failed("a" + i, "overworld", i, 0, later, MAX);
		assertEquals(PlaceMemory.CAP, m.size());
	}
}
