package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.state.Perception;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Nether stage (rung 8). blazeStep reads WorldMemory, which needs a live player, so the
 * decision itself is exercised through the pure two-boolean hook requested in the Freebuff
 * section of docs/coordination.md; the routing test and the rod count need nothing.
 */
class NetherPlanTest {
	/** The plan only reads the type string, so the entity can be null in a unit test. */
	private static Perception.Seen seen(String type) {
		return new Perception.Seen(null, type, 4.0, true);
	}

	@Test
	void blazeIsHandledByTheFortressFightOnlyInTheNether() {
		assertTrue(NetherPlan.fightInFortress("the_nether", seen("blaze")),
				"a blaze in the Nether must go to the fortress fight, not a chase");
		assertFalse(NetherPlan.fightInFortress("overworld", seen("blaze")));
		assertFalse(NetherPlan.fightInFortress("the_end", seen("blaze")));
		assertFalse(NetherPlan.fightInFortress("the_nether", seen("creeper")), "creeper reflex stays separate");
		assertFalse(NetherPlan.fightInFortress("the_nether", seen("enderman")), "neutral, not the fortress fight");
		assertFalse(NetherPlan.fightInFortress("the_nether", null));
	}

	@Test
	void rodsIncludeSparesForEyesThatBreak() {
		// 12 eyes need 6 rods (one rod makes two powder); outside review A3 added 2 spare rods,
		// because thrown eyes break about 20% of the time.
		assertEquals(8, NetherPlan.RODS);
	}

	/** The pure decision hook: known fortress / blaze in sight, without touching WorldMemory. */
	private static Method stepHook() {
		try {
			return NetherPlan.class.getMethod("blazeStep", boolean.class, boolean.class);
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	private static final String PENDING = "NetherPlan.blazeStep(boolean, boolean) hook not merged yet (docs/coordination.md, Freebuff)";

	@Test
	void knownFortressFightsBlazesForTheRodCount() throws Exception {
		Method m = stepHook();
		Assumptions.assumeTrue(m != null, PENDING);
		Option o = (Option) m.invoke(null, true, false);
		assertEquals("fortress", o.skill());
		assertEquals("blazes:" + NetherPlan.RODS, o.arg());
	}

	@Test
	void aBlazeInSightFightsEvenWithNoFortressMapped() throws Exception {
		Method m = stepHook();
		Assumptions.assumeTrue(m != null, PENDING);
		Option o = (Option) m.invoke(null, false, true);
		assertEquals("fortress", o.skill());
		assertEquals("blazes:" + NetherPlan.RODS, o.arg());
	}

	@Test
	void nothingKnownSearchesForAFortress() throws Exception {
		Method m = stepHook();
		Assumptions.assumeTrue(m != null, PENDING);
		Option o = (Option) m.invoke(null, false, false);
		assertEquals("fortress", o.skill());
		assertEquals("find", o.arg());
	}
}
