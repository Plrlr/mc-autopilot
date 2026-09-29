package io.github.plrlr.autopilot.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObsidianMoldTest {
	@Test
	void minesOnlyTheRequestedRemainder() {
		for (int have = 0; have < 10; have++)
			assertEquals(10 - have, ObsidianMold.requestedCount(String.valueOf(10 - have)));
		assertEquals(3, ObsidianMold.requestedCount("obsidian:3"));
	}

	@Test
	void retainsTheDefaultForMissingOrMalformedCounts() {
		assertEquals(10, ObsidianMold.requestedCount(null));
		assertEquals(10, ObsidianMold.requestedCount("obsidian"));
		assertEquals(10, ObsidianMold.requestedCount(""));
		assertEquals(1, ObsidianMold.requestedCount("0"));
	}
}
