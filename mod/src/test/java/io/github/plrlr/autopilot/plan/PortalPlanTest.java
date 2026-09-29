package io.github.plrlr.autopilot.plan;

import com.google.gson.JsonObject;
import io.github.plrlr.autopilot.Tune;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortalPlanTest {
	@AfterEach
	void reset() {
		Tune.reset();
	}

	private static void on(String... genes) {
		JsonObject g = new JsonObject();
		for (String n : genes) g.addProperty(n, 1);
		Tune.apply(g, "test");
	}

	@Test
	void genesOffKeepTheOldSteps() {
		Option cast = new Option("build_portal", null, "old");
		Option lava = new Option("explore", "lava", "old");
		Option dia = new Option("collect", "diamond:3", "old");
		assertSame(cast, PortalPlan.cast(cast));
		assertSame(lava, PortalPlan.lava(lava));
		assertSame(dia, PortalPlan.diamonds(dia));
	}

	@Test
	void genesOnSwapInTheNewSkills() {
		on("skill.cast_portal", "skill.lava_scout", "skill.diamond_hunt");
		assertEquals("cast_portal", PortalPlan.cast(new Option("build_portal", null, "x")).skill());
		// Placing 10 carried obsidian stays build_portal.
		assertEquals("build_portal", PortalPlan.cast(new Option("build_portal", "placed", "x")).skill());
		assertEquals("lava_scout", PortalPlan.lava(new Option("explore", "lava", "x")).skill());
		assertEquals("diamond_hunt 2", PortalPlan.diamonds(new Option("collect", "diamond:2", "x")).label());
		// The deep-for-lava trick keeps its own collect step.
		assertEquals("collect", PortalPlan.diamonds(new Option("collect", "diamond:1:lava", "x")).skill());
	}
}
