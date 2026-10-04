package io.github.plrlr.autopilot.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gold calms ordinary piglins, never brutes; any piglin in attack pose is coming for us. */
class PiglinThreatTest {
	@Test
	void calmPiglinsStayNeutral() {
		assertFalse(Perception.piglinThreat("piglin", false));
		assertFalse(Perception.piglinThreat("zombified_piglin", false));
	}

	@Test
	void attackPoseMakesAPiglinAThreat() {
		assertTrue(Perception.piglinThreat("piglin", true));
		assertTrue(Perception.piglinThreat("zombified_piglin", true));
	}

	@Test
	void aBruteIsAlwaysAThreat() {
		assertTrue(Perception.piglinThreat("piglin_brute", false));
	}

	@Test
	void otherMobsAreNotDecidedHere() {
		assertFalse(Perception.piglinThreat("enderman", true));
		assertFalse(Perception.piglinThreat("zombie", true));
	}
}
