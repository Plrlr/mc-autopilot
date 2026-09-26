package io.github.plrlr.autopilot.log;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointsTest {
	@Test
	void recordsEachCheckpointOnceWithItsTime() {
		Checkpoints.start(100);
		Checkpoints.tick(100 + 20 * 300);
		assertTrue(Checkpoints.mark("two_buckets"));
		Checkpoints.tick(100 + 20 * 400);
		assertFalse(Checkpoints.mark("two_buckets"));
		assertTrue(Checkpoints.mark("lava_seen"));
		assertEquals(List.of("two_buckets@300s", "lava_seen@400s"), Checkpoints.summary());
		Checkpoints.start(0);
		assertTrue(Checkpoints.summary().isEmpty());
	}
}
