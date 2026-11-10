package io.github.plrlr.autopilot.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Gene respawn.no_fights_s: which chosen fights a freshly respawned, poor bot turns down. */
class FreshRespawnTest {
	@Test
	void offByDefault() {
		assertFalse(SurvivalPlan.skipFreshFight(5_000, 0, 0, 3, 6));
	}

	@Test
	void skipsADistantFightSoonAfterRespawnWithNothing() {
		assertTrue(SurvivalPlan.skipFreshFight(5_000, 180, 0, 3, 6));
	}

	@Test
	void fightsWhenTheMobIsAtArmsLength() {
		// Turning our back on one that close gets us hit from behind.
		assertFalse(SurvivalPlan.skipFreshFight(5_000, 180, 0, 3, 2.0));
	}

	@Test
	void fightsOnceTheWindowIsOverOrTheKitIsBack() {
		assertFalse(SurvivalPlan.skipFreshFight(181_000, 180, 0, 3, 6));
		assertFalse(SurvivalPlan.skipFreshFight(5_000, 180, 3, 3, 6));
	}

	@Test
	void neverBeforeTheFirstDeath() {
		assertFalse(SurvivalPlan.skipFreshFight(-1, 180, 0, 3, 6));
	}
}
