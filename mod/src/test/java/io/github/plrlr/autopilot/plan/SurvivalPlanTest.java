package io.github.plrlr.autopilot.plan;

import com.google.gson.JsonObject;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.skills.BlockArrows;
import io.github.plrlr.autopilot.skills.CreeperDefuse;
import io.github.plrlr.autopilot.skills.Kite;
import io.github.plrlr.autopilot.skills.Pillar;
import io.github.plrlr.autopilot.state.Perception;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SurvivalPlanTest {
	@AfterEach
	void reset() {
		Tune.reset();
		SurvivalPlan.respawnHandled();
	}

	private static void on(String... genes) {
		JsonObject g = new JsonObject();
		for (String n : genes) g.addProperty(n, 1);
		Tune.apply(g, "test");
	}

	private static Perception.Seen mob(String type, double d) {
		return new Perception.Seen(null, type, d, true);
	}

	@Test
	void kiteOnlyTargetsCloseMeleeMobs() {
		assertTrue(Kite.suits(mob("zombie", 5.9)));
		assertFalse(Kite.suits(mob("zombie", 6.1)));
		assertFalse(Kite.suits(mob("creeper", 2)));
		assertFalse(Kite.suits(mob("skeleton", 3)));
	}

	@Test
	void pillarNeedsBlocksAndNoRangedThreat() {
		assertTrue(Pillar.canPillar(3, 2, false));
		assertFalse(Pillar.canPillar(2, 2, false));
		assertFalse(Pillar.canPillar(9, 1, false));
		assertFalse(Pillar.canPillar(9, 3, true));
	}

	@Test
	void everyFightHookKeepsTheOldChoiceWithItsGeneOff() {
		Option attack = new Option("attack", "skeleton", "old");
		assertSame(attack, SurvivalPlan.fight(mob("skeleton", 8), attack));
		Option retreat = new Option("retreat", null, "old");
		assertSame(retreat, SurvivalPlan.fight(mob("creeper", 4), retreat));
		Option death = new Option("goto", "death", "old");
		assertSame(death, SurvivalPlan.recover(death));
		Option food = new Option("explore", "cow", "old");
		assertSame(food, SurvivalPlan.food(food));
		Option smelt = new Option("smelt", "iron_ingot:3", "old");
		assertSame(smelt, SurvivalPlan.beforeWork(smelt));
		SurvivalPlan.respawned();
		assertNull(SurvivalPlan.respawn());
	}

	@Test
	void theRightSkillForEachMobWithGenesOn() {
		on("skill.block_arrows", "skill.creeper_defuse");
		assertEquals("block_arrows", SurvivalPlan.fight(mob("skeleton", 8), new Option("attack", "skeleton", "x")).skill());
		assertEquals("creeper_defuse", SurvivalPlan.fight(mob("creeper", 5), new Option("attack", "creeper", "x")).skill());
		assertEquals("creeper_defuse", SurvivalPlan.fight(mob("creeper", 5), new Option("retreat", null, "x")).skill());
		// A zombie fight stays an attack (kite handles the running part).
		assertEquals("attack", SurvivalPlan.fight(mob("zombie", 3), new Option("attack", "zombie", "x")).skill());
	}

	@Test
	void respawnStepComesOnceUntilHandled() {
		on("skill.respawn_reset");
		assertNull(SurvivalPlan.respawn());
		SurvivalPlan.respawned();
		assertEquals("respawn_reset", SurvivalPlan.respawn().skill());
		SurvivalPlan.respawnHandled();
		assertNull(SurvivalPlan.respawn());
	}

	@Test
	void targetsAreTheRightMobs() {
		assertTrue(BlockArrows.suits(mob("stray", 20)));
		assertFalse(BlockArrows.suits(mob("zombie", 5)));
		assertFalse(BlockArrows.suits(mob("skeleton", 30)));
		assertTrue(CreeperDefuse.suits(mob("creeper", 9)));
		assertFalse(CreeperDefuse.suits(mob("creeper", 11)));
	}
}
