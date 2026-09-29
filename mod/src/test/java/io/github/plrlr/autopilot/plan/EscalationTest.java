package io.github.plrlr.autopilot.plan;

import com.google.gson.JsonObject;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.state.Perception;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EscalationTest {
	@AfterEach
	void reset() {
		Tune.reset();
		Escalation.clear();
	}

	private static Perception zombieAt(double d) {
		Perception seen = new Perception();
		seen.mobs.add(new Perception.Seen(null, "zombie", d, true));
		return seen;
	}

	private static void on() {
		JsonObject g = new JsonObject();
		g.addProperty("reflex.escalate", 1);
		Tune.apply(g, "test");
	}

	@Test
	void twoRetreatsInThirtySecondsCount() {
		Escalation.fled(100);
		assertFalse(Escalation.ranTwice());
		Escalation.fled(300);
		assertTrue(Escalation.ranTwice());
		// 30 s after the first one it no longer counts.
		Escalation.tick(100 + Escalation.WINDOW_TICKS + 1);
		assertFalse(Escalation.ranTwice());
	}

	@Test
	void offByDefaultTheThirdEscapeStillRuns() {
		Escalation.fled(1);
		Escalation.fled(2);
		assertEquals("retreat", Planner.escape(zombieAt(6), false, "low health").skill());
	}

	@Test
	void withTheGeneTheThirdEscapeStandsItsGround() {
		on();
		assertEquals("retreat", Planner.escape(zombieAt(6), false, "low health").skill());
		Escalation.fled(1);
		Escalation.fled(2);
		assertEquals("attack zombie", Planner.escape(zombieAt(6), false, "low health").label());
		assertEquals("shelter heal", Planner.escape(zombieAt(6), true, "low health").label());
	}

	@Test
	void neverStandsItsGroundAgainstACreeper() {
		Perception seen = new Perception();
		seen.mobs.add(new Perception.Seen(null, "creeper", 3, true));
		String before = Planner.escape(seen, false, "creeper").label();
		on();
		Escalation.fled(1);
		Escalation.fled(2);
		// A creeper close by: escalation stands down and the old escape applies unchanged.
		assertEquals(before, Planner.escape(seen, false, "creeper").label());
		assertNotEquals("attack creeper", Planner.escape(seen, false, "creeper").label());
	}
}
