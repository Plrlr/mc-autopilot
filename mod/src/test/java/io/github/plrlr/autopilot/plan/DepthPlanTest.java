package io.github.plrlr.autopilot.plan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DepthPlanTest {
	@AfterEach
	void reset() {
		DepthPlan.reset();
	}

	@Test
	void unarmoredStaysAboveTheDeepCaves() {
		assertEquals(40, DepthPlan.ironY(16, 0));
		assertEquals(50, DepthPlan.ironY(50, 0), "a shallower gene depth stands");
		assertEquals(16, DepthPlan.ironY(16, 12), "armored: the gene's depth");
	}

	@Test
	void aDeepDeathMakesLaterTripsShallower() {
		DepthPlan.noteDeath(6, true);
		assertEquals(48, DepthPlan.ironY(16, 0));
		DepthPlan.noteDeath(64, false);
		assertEquals(16, DepthPlan.ironY(16, 12));
	}

	@Test
	void aSurfaceDeathDoesntCount() {
		DepthPlan.noteDeath(20, false);
		assertEquals(40, DepthPlan.ironY(16, 0));
	}
}
