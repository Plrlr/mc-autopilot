package io.github.plrlr.autopilot.skills;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PortalSiteSearchTest {
	@Test
	void spreadSearchAcrossTicksWithoutRankingBudgetTruncatedPlans() {
		BlockPos feet = PortalSiteTest.O;
		BlockPos pool = feet.east(7).below();
		PortalSiteTest.Terrain terrain = new PortalSiteTest.Terrain();
		PortalSite.Plan expected = PortalSite.makerBest(feet, pool, terrain, Set.of());
		PortalSiteSearch search = new PortalSiteSearch(feet, pool, Set.of());
		int ticks = 0;
		boolean done;
		do {
			AtomicInteger rays = new AtomicInteger();
			FairProbe seen = new FairProbe(terrain, p -> { rays.incrementAndGet(); return true; });
			done = search.step(seen);
			assertTrue(rays.get() <= FairProbe.RAY_BUDGET);
			assertTrue(++ticks < 100, "search must make progress after exhausting a tick's budget");
		} while (!done);
		assertTrue(ticks > 1, "candidate work is also bounded per tick");
		assertNotNull(search.best());
		assertEquals(expected.origin(), search.best().origin());
		assertEquals(expected.along(), search.best().along());
		assertEquals(expected.cost(), search.best().cost());
	}

	@Test
	void blockedStandingRowsCannotBecomeATentativeSite() {
		PortalSiteSearch search = new PortalSiteSearch(PortalSiteTest.O, null, Set.of());
		for (int tick = 0; tick < 20; tick++) {
			if (search.step(new FairProbe(new PortalSiteTest.Terrain(), p -> false))) {
				assertNull(search.best());
				return;
			}
		}
		fail("blocked search did not complete");
	}
}
