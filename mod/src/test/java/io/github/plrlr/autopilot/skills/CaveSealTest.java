package io.github.plrlr.autopilot.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaveSealTest {
	@Test
	void distinguishesNarrowBranchFromOpenCave() {
		assertFalse(CaveSeal.caveShape(false, true, true, true));
		assertFalse(CaveSeal.caveShape(true, false, false, false));
		assertTrue(CaveSeal.caveShape(true, true, false, false));
		assertTrue(CaveSeal.caveShape(true, false, true, false));
		assertTrue(CaveSeal.caveShape(true, false, false, true));
	}

	@Test
	void visibleOreIsOnlyWorthAnUnthreatenedOpening() {
		assertFalse(CaveSeal.shouldSeal(false, false, false));
		assertTrue(CaveSeal.shouldSeal(true, false, false));
		assertFalse(CaveSeal.shouldSeal(true, false, true));
		assertTrue(CaveSeal.shouldSeal(true, true, true));
		assertTrue(CaveSeal.shouldSeal(false, true, false));
	}
}
