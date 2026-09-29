package io.github.plrlr.autopilot.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The site planner on made-up terrain: flat, bumpy, a hole, water, bedrock, lava pool distance. */
class PortalSiteTest {
	/** Terrain: stone below y 64, air from y 64 up, plus overrides. */
	static final class Terrain implements PortalSite.Probe {
		final Map<BlockPos, String> blocks = new HashMap<>();

		String at(BlockPos p) {
			return blocks.getOrDefault(p, p.getY() < 64 ? "stone" : "air");
		}

		@Override
		public boolean free(BlockPos p) {
			return at(p).equals("air");
		}

		@Override
		public boolean solid(BlockPos p) {
			return !free(p) && !fluid(p);
		}

		@Override
		public boolean fluid(BlockPos p) {
			return at(p).equals("water") || at(p).equals("lava");
		}

		@Override
		public boolean fluidNear(BlockPos p) {
			for (Direction d : Direction.values()) if (fluid(p.relative(d))) return true;
			return false;
		}

		@Override
		public boolean hard(BlockPos p) {
			return at(p).equals("bedrock");
		}
	}

	static final BlockPos O = new BlockPos(0, 64, 0);

	@Test
	void flatOpenGroundNeedsNoWork() {
		PortalSite.Plan p = PortalSite.plan(O, Direction.EAST, new Terrain());
		assertNotNull(p);
		assertEquals(0, p.dig().size());
		assertEquals(0, p.floor().size());
	}

	@Test
	void aBoulderInTheFrameGetsDug() {
		Terrain t = new Terrain();
		t.blocks.put(O.above(2), "stone");
		t.blocks.put(O.east().above(3), "dirt");
		PortalSite.Plan p = PortalSite.plan(O, Direction.EAST, t);
		assertEquals(Set.of(O.above(2), O.east().above(3)), Set.copyOf(p.dig()));
	}

	@Test
	void aOneDeepHoleUnderTheFrameGetsFilled() {
		Terrain t = new Terrain();
		t.blocks.put(O.below(), "air");
		PortalSite.Plan p = PortalSite.plan(O, Direction.EAST, t);
		assertEquals(java.util.List.of(O.below()), p.floor());
	}

	@Test
	void aDeepDropOrWaterOrBedrockRulesTheSiteOut() {
		Terrain drop = new Terrain();
		drop.blocks.put(O.below(), "air");
		drop.blocks.put(O.below(2), "air");
		assertNull(PortalSite.plan(O, Direction.EAST, drop));
		Terrain wet = new Terrain();
		wet.blocks.put(O.east(2).above(6), "water"); // right above the frame's top row
		assertNull(PortalSite.plan(O, Direction.EAST, wet));
		Terrain rock = new Terrain();
		rock.blocks.put(O.above(1), "bedrock");
		assertNull(PortalSite.plan(O, Direction.EAST, rock));
	}

	@Test
	void theBestSiteStaysAwayFromTheLavaButNear() {
		Terrain t = new Terrain();
		BlockPos pool = new BlockPos(0, 63, 0);
		t.blocks.put(pool, "lava");
		PortalSite.Plan p = PortalSite.best(new BlockPos(0, 64, 5), pool, t, Set.of());
		assertNotNull(p);
		double d = Math.sqrt(p.origin().distSqr(pool));
		assertTrue(d >= 3 && d <= 14, "distance " + d);
		assertEquals(0, p.cost());
		// A site set aside is not picked again.
		PortalSite.Plan q = PortalSite.best(new BlockPos(0, 64, 5), pool, t, Set.of(p.origin()));
		assertNotEquals(p.origin(), q.origin());
	}
}
