package io.github.plrlr.autopilot.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure pieces of wave 1: stair geometry, bite detection. */
class SurvivalSkillsTest {
	@Test
	void aStairStepClearsHeadBodyAndTheStepAhead() {
		BlockPos feet = new BlockPos(0, 64, 0);
		BlockPos[] c = StairDown.cut(feet, Direction.NORTH);
		assertArrayEquals(new BlockPos[]{new BlockPos(0, 65, -1), new BlockPos(0, 64, -1), new BlockPos(0, 63, -1)}, c);
	}

	@Test
	void aBiteIsADipBelowTheRestingBobber() {
		assertFalse(Fish.bite(Double.NaN, 60));
		assertFalse(Fish.bite(62.0, 61.95));
		assertTrue(Fish.bite(62.0, 61.8));
	}
}
