package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Items2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tech tree is how the planner works out where an item comes from. A hole in it (an
 * ingredient nothing can produce) makes itemStep fall through to "explore any" and the bot asks
 * for the same impossible item forever, so every ingredient has to be reachable.
 */
class TechTreeTest {
	/** Everything the tree can produce, by any of the four ways (craft, smelt, mine, mob drop). */
	private static Set<String> obtainable() {
		Set<String> s = new LinkedHashSet<>();
		s.addAll(TechTree.CRAFT.keySet());
		s.addAll(TechTree.SMELT.keySet());
		s.addAll(TechTree.MINE.keySet());
		s.addAll(TechTree.MOB.keySet());
		return s;
	}

	@Test
	void everyRecipeIngredientIsObtainable() {
		Set<String> ok = obtainable();
		for (TechTree.Recipe r : TechTree.CRAFT.values()) {
			for (String ingredient : r.in().keySet()) {
				assertTrue(ok.contains(ingredient),
						"nothing can produce '" + ingredient + "', an ingredient of " + r.out());
			}
		}
	}

	@Test
	void everyMineSourceHasTheRightToolTier() {
		// Hands (-1) for what a player punches, otherwise the vanilla pickaxe tier: 0 wood, 1 stone,
		// 2 iron, 3 diamond.
		assertEquals(-1, TechTree.MINE.get("log").tier(), "logs are punched by hand");
		assertEquals(-1, TechTree.MINE.get("dirt").tier());
		assertEquals(-1, TechTree.MINE.get("sand").tier());
		assertEquals(-1, TechTree.MINE.get("flint").tier(), "gravel is dug up by hand");
		assertEquals(0, TechTree.MINE.get("stone").tier());
		assertEquals(0, TechTree.MINE.get("coal").tier());
		assertEquals(1, TechTree.MINE.get("raw_iron").tier(), "iron ore needs a stone pickaxe");
		assertEquals(2, TechTree.MINE.get("diamond").tier(), "diamonds need an iron pickaxe");
		assertEquals(2, TechTree.MINE.get("raw_gold").tier());
		assertEquals(3, TechTree.MINE.get("obsidian").tier(), "obsidian needs a diamond pickaxe");
	}

	@Test
	void everyMineSourceHasAPickaxeThatCanMineIt() {
		for (TechTree.Source src : TechTree.MINE.values()) {
			if (src.tier() < 0) continue; // hands
			String pick = TechTree.pickaxeForTier(src.tier());
			assertEquals(src.tier(), Items2.tier(pick),
					pick + " should be pickaxe tier " + src.tier() + " to mine " + src.blocks());
		}
		assertEquals("wooden_pickaxe", TechTree.pickaxeForTier(0));
		assertEquals("stone_pickaxe", TechTree.pickaxeForTier(1));
		assertEquals("iron_pickaxe", TechTree.pickaxeForTier(2));
		assertEquals("diamond_pickaxe", TechTree.pickaxeForTier(3));
	}

	/**
	 * Same reachability rule for the smelt inputs. Disabled because main is missing the mob
	 * entries (rabbit, cod, salmon); see the Freebuff section of docs/coordination.md. Flip to
	 * enabled once those three MOB entries land.
	 */
	@Test
	@Disabled("TechTree.MOB has no rabbit/cod/salmon; see docs/coordination.md, Freebuff")
	void smeltedItemsHaveTheirRawSource() {
		Set<String> ok = obtainable();
		for (var e : TechTree.SMELT.entrySet()) {
			assertTrue(ok.contains(e.getValue()),
					"nothing can produce '" + e.getValue() + "', the input for " + e.getKey());
		}
	}
}
