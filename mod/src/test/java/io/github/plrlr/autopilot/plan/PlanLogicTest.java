package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Items2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanLogicTest {
	@Test
	void exploreLooksForWhatTheStepNeeds() {
		assertEquals("cow,pig", Planner.exploreTarget(new Option("explore", "cow,pig", "")));
		assertEquals("log", Planner.exploreTarget(new Option("collect", "log:9", "")));
		assertEquals("gravel", Planner.exploreTarget(new Option("collect", "flint:1", "")));
		// Ores are found by mining, not by walking around.
		assertEquals("any", Planner.exploreTarget(new Option("collect", "raw_iron:13", "")));
		assertEquals("lava", Planner.exploreTarget(new Option("make_obsidian", "obsidian:10", "")));
		assertEquals("water", Planner.exploreTarget(new Option("fill_bucket", "water", "")));
		assertEquals("any", Planner.exploreTarget(null));
	}

	@Test
	void armorAndToolTiers() {
		assertEquals(0, Planner.armorTier("leather_boots"));
		assertEquals(2, Planner.armorTier("iron_chestplate"));
		assertEquals(2, Planner.armorTier("turtle_helmet"));
		assertEquals(-1, Planner.armorTier("carved_pumpkin"));
		assertEquals(0, Items2.tier("wooden_pickaxe"));
		assertEquals(1, Items2.tier("stone_sword"));
		assertEquals(3, Items2.tier("diamond_pickaxe"));
		assertEquals(-1, Items2.tier("stick"));
	}

	@Test
	void goalNeedsKeepTheirOrder() {
		// Map.of's order changed between game starts; the planner works through needs in order.
		assertEquals(List.of("iron_pickaxe", "shield", "bucket", "iron_sword"), List.copyOf(Goal.IRON_TOOLS.needs.keySet()));
	}

	@Test
	void speedrunSkipsFoodArmorAndDiamonds() {
		assertTrue(Goal.FOOD.optional());
		assertTrue(Goal.IRON_ARMOR.optional());
		assertTrue(Goal.DIAMONDS.optional());
		assertFalse(Goal.IRON_TOOLS.optional());
		assertFalse(Goal.NETHER_PORTAL.optional());
	}
}
