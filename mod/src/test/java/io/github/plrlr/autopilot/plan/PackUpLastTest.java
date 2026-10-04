package io.github.plrlr.autopilot.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Gene stations.pickup_last: the table is packed up after the crafting that needs it, not between. */
class PackUpLastTest {
	@Test
	void pickupMovesBehindTheLastCraft() {
		Option pickup = new Option("pickup", "stations", "take them along");
		Option sword = new Option("craft", "wooden_sword:1", "a sword");
		Option stone = new Option("collect", "stone:13", "stone");
		List<Option> out = Planner.packUpAfter(List.of(pickup, sword, stone), 0, 1);
		assertEquals(List.of("craft wooden_sword:1", "pickup stations", "collect stone:13"), out.stream().map(Option::label).toList());
	}
}
