package io.github.plrlr.autopilot.plan;

import com.google.gson.JsonObject;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.state.Perception;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EscapePlanTest {
	@AfterEach
	void reset() {
		Tune.reset();
	}

	private static void enable() {
		JsonObject genes = new JsonObject();
		genes.addProperty("combat.no_close_retreat", 1);
		Tune.apply(genes, "test");
	}

	private static Perception threats(double distance) {
		Perception seen = new Perception();
		seen.mobs.add(new Perception.Seen(null, "zombie", distance, true));
		seen.mobs.add(new Perception.Seen(null, "skeleton", 5, true));
		return seen;
	}

	@Test
	void defaultKeepsOldEscapeEvenAtArmsLength() {
		assertFalse(Tune.on("combat.no_close_retreat"));
		assertEquals("retreat", Planner.escape(threats(3), false, "low health").skill());
		assertEquals("shelter heal", Planner.escape(threats(3), true, "low health").label());
	}

	@Test
	void outnumberedAtThreeToFourBlocksFightsUnlessAbleToWallIn() {
		enable();
		for (double distance : new double[]{3, 3.5, 4}) {
			assertEquals("attack zombie", Planner.escape(threats(distance), false, "low health").label());
			assertEquals("shelter heal", Planner.escape(threats(distance), true, "low health").label());
		}
	}

	@Test
	void beyondFourBlocksCanStillRun() {
		enable();
		assertEquals("retreat", Planner.escape(threats(4.01), false, "low health").skill());
		assertEquals("retreat", Planner.escape(new Perception(), false, "low health").skill());
	}

	@Test
	void nearbyCreeperOverridesCloserZombieAndShelter() {
		enable();
		Perception seen = threats(2);
		seen.mobs.add(new Perception.Seen(null, "creeper", 3, true));
		assertEquals("retreat", Planner.escape(seen, true, "low health").skill());
		assertEquals("retreat", Planner.escape(seen, false, "low health").skill());
	}

	@Test
	void hiddenOrDistantCreeperDoesNotPreventDefense() {
		enable();
		Perception seen = threats(2);
		seen.mobs.add(new Perception.Seen(null, "creeper", 3, false));
		seen.mobs.add(new Perception.Seen(null, "creeper", 8, true));
		assertEquals("attack zombie", Planner.escape(seen, false, "low health").label());
	}
}
