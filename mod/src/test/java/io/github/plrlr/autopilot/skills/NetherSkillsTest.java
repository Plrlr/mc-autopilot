package io.github.plrlr.autopilot.skills;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetherSkillsTest {
	@Test
	void fortressBlocksAreNetherBrickOnly() {
		assertTrue(NetherSkills.fortressBlock("nether_bricks"));
		assertTrue(NetherSkills.fortressBlock("nether_brick_fence"));
		assertTrue(NetherSkills.fortressBlock("nether_brick_stairs"));
		// Bastions and the nether floor must not count as a fortress.
		assertFalse(NetherSkills.fortressBlock("red_nether_bricks"));
		assertFalse(NetherSkills.fortressBlock("polished_blackstone_bricks"));
		assertFalse(NetherSkills.fortressBlock("netherrack"));
	}

	@Test
	void oneSweepCoversEveryYawAtEveryPitch() {
		// 48 yaws x 5 pitch bands = 240 rays = 15 ticks at 16 rays a tick.
		Set<String> seen = new HashSet<>();
		for (int n = 0; n < 240; n++) {
			float[] yp = NetherSkills.sweepRay(n);
			assertTrue(yp[0] >= 0 && yp[0] < 360);
			assertTrue(yp[1] >= -12 && yp[1] <= 35);
			seen.add(yp[0] + "/" + yp[1]);
		}
		assertEquals(240, seen.size());
		// Then it starts over.
		assertEquals(NetherSkills.sweepRay(0)[0], NetherSkills.sweepRay(240)[0]);
		assertEquals(NetherSkills.sweepRay(0)[1], NetherSkills.sweepRay(240)[1]);
	}
}
