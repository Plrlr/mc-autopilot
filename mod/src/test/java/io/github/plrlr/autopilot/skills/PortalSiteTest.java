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
	static class Terrain implements PortalSite.Probe {
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
	void aFlowerWhereTheWallGoesGetsDugOut() {
		Terrain t = new Terrain() {
			@Override
			public boolean solid(BlockPos p) {
				return !at(p).equals("poppy") && super.solid(p);
			}
		};
		// Along EAST the front is SOUTH, so the wall is one block NORTH of the frame.
		BlockPos wall = O.north().above(1);
		t.blocks.put(wall, "poppy");
		PortalSite.Plan p = PortalSite.plan(O, Direction.EAST, t);
		assertTrue(p.dig().contains(wall), "dig " + p.dig());
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

	@Test
	void makerCanDigAWholeRockRoomFromAnOpenFrontRow() {
		Terrain t = new Terrain();
		for (int x = 0; x < 4; x++) for (int f = 0; f <= 2; f++)
			for (int y = 0; y < 6; y++) t.blocks.put(O.east(x).south(f).above(y), "stone");
		BlockPos stand = O.east().south(2);
		t.blocks.put(stand, "air");
		t.blocks.put(stand.above(), "air");
		PortalSite.Plan p = PortalSite.makerPlan(O, Direction.EAST, t);
		assertNotNull(p, "a room dug into solid rock, entered from one open standing cell");
		// The frame's 4x5 opening and the two standing rows (the back wall stays rock).
		assertTrue(p.dig().size() >= 30, "dig " + p.dig().size());
	}

	@Test
	void makerFillsAThreeDeepGapFromTheBottomUp() {
		Terrain t = new Terrain();
		for (int y = 1; y <= 3; y++) t.blocks.put(O.below(y), "air");
		PortalSite.Plan p = PortalSite.makerPlan(O, Direction.EAST, t);
		assertNotNull(p);
		assertEquals(java.util.List.of(O.below(3), O.below(2), O.below()), p.floor());
	}

	@Test
	void makerRejectsFluidThatCouldEnterAndHardFrameBlocks() {
		Terrain wet = new Terrain();
		wet.blocks.put(O.north().above(2), "lava");
		assertNull(PortalSite.makerPlan(O, Direction.EAST, wet));
		Terrain hard = new Terrain();
		hard.blocks.put(O.above(3), "bedrock");
		assertNull(PortalSite.makerPlan(O, Direction.EAST, hard));
	}

	@Test
	void placedFrameFitsBelowAHardRoofWithoutASecondWall() {
		Terrain t = new Terrain();
		for (int x = 0; x < 4; x++) {
			t.blocks.put(O.east(x).above(5), "bedrock");
			t.blocks.put(O.east(x).north().below(), "air");
			t.blocks.put(O.east(x).north().below(2), "air");
		}
		PortalSite.Plan p = PortalSite.placedPlan(O, Direction.EAST, t);
		assertNotNull(p, "a five-high placed frame needs no cast roof row or backing support");
		assertTrue(p.dig().isEmpty());
		assertTrue(p.floor().isEmpty());
		assertNull(PortalSite.plan(O, Direction.EAST, t), "casting still needs its original geometry");
	}

	@Test
	void placedRoomDigsOnlyItsFrameAndStandingRowsAndStillRejectsHazards() {
		Terrain t = new Terrain() {
			@Override String at(BlockPos p) { return blocks.getOrDefault(p, "stone"); }
		};
		PortalSite.Plan p = PortalSite.placedPlan(O, Direction.EAST, t);
		assertNotNull(p);
		assertEquals(4 * 5 + 4 * 2 * 2, p.dig().size());
		assertFalse(p.dig().contains(O.above(5)));
		t.blocks.put(O.above(4), "bedrock");
		assertNull(PortalSite.placedPlan(O, Direction.EAST, t));
		t.blocks.put(O.above(4), "lava");
		assertNull(PortalSite.placedPlan(O, Direction.EAST, t));
	}

	@Test
	void roomDiggingStartsWithAnExposedFrontInsteadOfAHiddenFrameCell() {
		BlockPos hidden = O, front = O.south(2), feet = O.south(3);
		Set<BlockPos> visible = new java.util.HashSet<>(Set.of(front));
		Terrain t = new Terrain() {
			@Override String at(BlockPos p) {
				assertTrue(visible.contains(p), "inspected hidden frame cell " + p);
				return blocks.getOrDefault(p, "stone");
			}
		};
		PortalSite.Plan plan = new PortalSite.Plan(O, Direction.EAST, java.util.List.of(hidden, front), java.util.List.of(), 4);
		assertEquals(front, PortalSite.exposedDig(plan, feet, new FairProbe(t, visible::contains)));
		t.blocks.put(front, "air");
		visible.add(hidden);
		assertEquals(hidden, PortalSite.exposedDig(plan, feet, new FairProbe(t, visible::contains)));
	}
}
