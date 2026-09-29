package io.github.plrlr.autopilot.skills;

import org.junit.jupiter.api.Test;

import static io.github.plrlr.autopilot.skills.CastPortal.BucketPlan.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CastPortalBucketsTest {
	@Test
	void twoWaterBucketsEmptyOneForTheLava() {
		assertEquals(EMPTY_SPARE, CastPortal.bucketPlan(2, 0, 0));
		assertEquals(MISSING, CastPortal.bucketPlan(1, 0, 0));
		assertEquals(MISSING, CastPortal.bucketPlan(0, 2, 0));
	}

	@Test
	void existingEmptyOrLavaBucketKeepsNormalCastPath() {
		assertEquals(READY, CastPortal.bucketPlan(1, 1, 0));
		assertEquals(READY, CastPortal.bucketPlan(1, 0, 1));
	}
}
