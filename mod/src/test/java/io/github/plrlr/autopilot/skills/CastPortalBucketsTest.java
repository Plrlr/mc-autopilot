package io.github.plrlr.autopilot.skills;

import org.junit.jupiter.api.Test;

import static io.github.plrlr.autopilot.skills.CastPortal.BucketPlan.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CastPortalBucketsTest {
	@Test
	void twoWaterBucketsOnlyBecomeUsableWithReserveGene() {
		assertEquals(MISSING, CastPortal.bucketPlan(2, 0, 0, false));
		assertEquals(EMPTY_SPARE, CastPortal.bucketPlan(2, 0, 0, true));
		assertEquals(MISSING, CastPortal.bucketPlan(1, 0, 0, true));
	}

	@Test
	void existingEmptyOrLavaBucketKeepsNormalCastPath() {
		assertEquals(READY, CastPortal.bucketPlan(1, 1, 0, false));
		assertEquals(READY, CastPortal.bucketPlan(1, 0, 1, true));
	}
}
