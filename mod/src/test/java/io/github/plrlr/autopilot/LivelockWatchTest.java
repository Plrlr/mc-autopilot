package io.github.plrlr.autopilot;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LivelockWatchTest {
	@Test
	void tonightsSkeletonLoopIsCaught() {
		// Local trial night2: block_arrows ok / recover_items interrupted, standing at (6, 120, -1).
		LivelockWatch w = new LivelockWatch();
		Set<String> loop = null;
		for (int s = 0; s <= 60 && loop == null; s += 5)
			loop = w.record(990 + s, s % 10 == 0 ? "block_arrows" : "recover_items", 6.3, 120, -0.7 + (s % 2) * 0.4, 42, false);
		assertEquals(Set.of("block_arrows", "recover_items"), loop);
	}

	@Test
	void movingGainingOrKillingIsProgress() {
		LivelockWatch walk = new LivelockWatch(), mine = new LivelockWatch(), fight = new LivelockWatch();
		for (int s = 0; s <= 60; s += 5) {
			assertNull(walk.record(s, "explore", s * 0.5, 64, 0, 1, false));
			assertNull(mine.record(s, "collect", 0, 64, 0, s, false));
			assertNull(fight.record(s, "attack", 0, 64, 0, 1, s == 30));
		}
	}

	@Test
	void aFewQuickActionsAreNotALoop() {
		LivelockWatch w = new LivelockWatch();
		for (int s = 0; s < 10; s++) assertNull(w.record(s, "craft", 0, 64, 0, 1, false));
	}
}
