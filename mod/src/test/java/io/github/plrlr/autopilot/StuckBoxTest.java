package io.github.plrlr.autopilot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StuckBoxTest {
	@Test void waterBobbingDoesNotHideAHorizontalStall() {
		assertFalse(StuckBox.stalled(0.2, 2.0, 0.3, 2, true, false));
		assertTrue(StuckBox.stalled(0.2, 2.0, 0.3, 2, true, true));
		assertFalse(StuckBox.stalled(2.1, 2.0, 0.3, 2, true, true));
		assertFalse(StuckBox.stalled(0.2, 2.0, 0.3, 2, false, true));
	}
}
