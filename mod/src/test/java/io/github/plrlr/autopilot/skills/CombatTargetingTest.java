package io.github.plrlr.autopilot.skills;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatTargetingTest {
	private static final List<CombatTargeting.Candidate> MOBS = List.of(
			new CombatTargeting.Candidate(0, "zombie", 2, 0, false),
			new CombatTargeting.Candidate(1, "skeleton", 4, 2, false),
			new CombatTargeting.Candidate(2, "creeper", 4.5, 0, true));

	@Test void nearestStaysDefault() {
		assertEquals(0, CombatTargeting.choose(MOBS, 0, false, 5));
	}

	@Test void observedHitsStandInForHiddenHealth() {
		assertEquals(1, CombatTargeting.choose(MOBS, 1, false, 5));
	}

	@Test void creeperPriorityRequiresTheHitAndBackOffTactic() {
		assertEquals(0, CombatTargeting.choose(MOBS, 2, false, 5));
		assertEquals(2, CombatTargeting.choose(MOBS, 2, true, 5));
		assertEquals(0, CombatTargeting.choose(MOBS, 2, true, 4));
	}
}
