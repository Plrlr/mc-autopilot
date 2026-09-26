package io.github.plrlr.autopilot.brains;

import io.github.plrlr.autopilot.plan.Goal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategistRulesTest {
	@Test
	void startsAtTheBottomOfTheLadder() {
		assertEquals(Goal.WOOD_TOOLS, Strategist.rules(g -> false, null).goal());
	}

	@Test
	void skipsArmorAndDiamondsOnTheWayToThePortal() {
		// Iron tools done: the speedrun route goes straight to the portal.
		assertEquals(Goal.NETHER_PORTAL, Strategist.rules(g -> g.milestone >= 1 && g.milestone <= 4, null).goal());
	}

	@Test
	void everythingDoneMeansTheDragon() {
		assertEquals(Goal.KILL_DRAGON, Strategist.rules(g -> true, null).goal());
	}
}
