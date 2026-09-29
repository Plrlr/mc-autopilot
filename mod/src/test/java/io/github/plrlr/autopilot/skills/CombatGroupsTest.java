package io.github.plrlr.autopilot.skills;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombatGroupsTest {
	@Test void keepsFocusUntilAnotherZombieGetsInsideReach() {
		assertFalse(CombatGroups.switchTarget(2.9, 2.0));
		assertFalse(CombatGroups.switchTarget(4.0, 3.0));
		assertTrue(CombatGroups.switchTarget(4.0, 2.5));
	}

	@Test void clearsThePackBeforeCollectingDrops() {
		assertFalse(CombatGroups.collectDrops(1));
		assertTrue(CombatGroups.collectDrops(0));
	}

	@Test void stepsAwayFromTheNearestZombieWithoutWalkingIntoAnother() {
		var zombies = List.of(new CombatGroups.Point(0, 1), new CombatGroups.Point(0, -1.5));
		var moves = new CombatGroups.Point[]{new CombatGroups.Point(0, -0.8),
				new CombatGroups.Point(-0.8, 0), new CombatGroups.Point(0.8, 0)};
		assertEquals(1, CombatGroups.step(zombies, moves, new boolean[]{true, true, true}));
		assertEquals(2, CombatGroups.step(zombies, moves, new boolean[]{true, false, true}));
		assertEquals(-1, CombatGroups.step(zombies, moves, new boolean[]{false, false, false}));
	}
}
