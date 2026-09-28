package io.github.plrlr.autopilot.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoalLadderTest {
	@Test
	void startsAtTheBottomOfTheLadder() {
		assertEquals(Goal.WOOD_TOOLS, GoalLadder.next(g -> false));
	}

	@Test
	void skipsArmorAndDiamondsOnTheWayToThePortal() {
		// Iron tools done: the speedrun route goes straight to the portal.
		assertEquals(Goal.NETHER_PORTAL, GoalLadder.next(g -> g.milestone >= 1 && g.milestone <= 4));
	}

	@Test
	void goesForIronRightAfterStoneTools() {
		// Food is upkeep, not a rung to stock up on first.
		assertEquals(Goal.IRON_TOOLS, GoalLadder.next(g -> g.milestone >= 1 && g.milestone <= 2));
	}

	@Test
	void everythingDoneMeansTheDragon() {
		assertEquals(Goal.KILL_DRAGON, GoalLadder.next(g -> true));
	}
}
