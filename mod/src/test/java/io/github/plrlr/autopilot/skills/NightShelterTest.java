package io.github.plrlr.autopilot.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NightShelterTest {
	@Test
	void wallFallbackRequiresBlocksAndDryStableFooting() {
		assertTrue(NightSkills.Shelter.canWallWithoutShaft(9, true, false, false, true));
		assertFalse(NightSkills.Shelter.canWallWithoutShaft(8, true, false, false, true));
		assertFalse(NightSkills.Shelter.canWallWithoutShaft(9, false, false, false, true));
		assertFalse(NightSkills.Shelter.canWallWithoutShaft(9, true, true, false, true));
		assertFalse(NightSkills.Shelter.canWallWithoutShaft(9, true, false, true, true));
		assertFalse(NightSkills.Shelter.canWallWithoutShaft(9, true, false, false, false));
	}
}
