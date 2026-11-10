package io.github.plrlr.autopilot.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The drops' despawn clock (gene death.recovery_v2): it ages only while their chunks are loaded. */
class DeathClockTest {
	@Test
	void idleUntilStarted() {
		DeathClock c = new DeathClock();
		assertFalse(c.active());
		assertFalse(c.tick("overworld", 0, 64, 0));
		assertEquals(0, c.remaining());
	}

	@Test
	void doesNotAgeFarAwayOrInAnotherDimension() {
		DeathClock c = new DeathClock();
		c.start("the_nether", 0, 64, 0, 0);
		for (int i = 0; i < 10_000; i++) {
			assertFalse(c.tick("overworld", 0, 64, 0));
			assertFalse(c.tick("the_nether", 500, 64, 0));
		}
		assertTrue(c.active());
		assertFalse(c.running(), "nobody was near: the items haven't started aging");
		assertEquals(DeathClock.BUDGET, c.remaining());
	}

	@Test
	void expiresOnceAfterTheBudgetOfLoadedTicks() {
		DeathClock c = new DeathClock();
		c.start("overworld", 10, 64, 10, 0);
		int expired = 0;
		for (long i = 0; i < DeathClock.BUDGET + 50; i++) if (c.tick("overworld", 20, 64, 20)) expired++;
		assertEquals(1, expired, "the caller forgets the spot exactly once");
		assertFalse(c.active());
	}

	@Test
	void resumesFromTheAgeInTheNotes() {
		DeathClock c = new DeathClock();
		c.start("overworld", 0, 64, 0, DeathClock.BUDGET - 5);
		assertTrue(c.running());
		assertEquals(5, c.remaining());
		c.stop();
		assertFalse(c.active());
	}
}
