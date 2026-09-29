package io.github.plrlr.autopilot.plan;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryValueTest {
	private static int value(Map<String, Integer> inv) {
		return SurvivalPlan.kitValue(id -> inv.getOrDefault(id, 0));
	}

	@Test
	void stoneToolsAndBlocksAreNotWorthTheWalkBack() {
		// The local trial night2's second death: stone tools, cobblestone, dirt.
		assertEquals(0, value(Map.of("stone_pickaxe", 1, "cobblestone", 58, "dirt", 4, "oak_planks", 3)));
		SurvivalPlan.noteDeath(0);
		assertFalse(SurvivalPlan.recoveryWorthIt(20, true));
	}

	@Test
	void ironKitIsWorthItWhenWeCanTakeAFight() {
		SurvivalPlan.noteDeath(value(Map.of("iron_pickaxe", 1, "raw_iron", 5)));
		assertTrue(SurvivalPlan.recoveryWorthIt(14, false));
		assertTrue(SurvivalPlan.recoveryWorthIt(7, true));
		assertFalse(SurvivalPlan.recoveryWorthIt(7, false), "7 hp, no food: not back into that cave");
		SurvivalPlan.noteDeath(0);
	}
}
