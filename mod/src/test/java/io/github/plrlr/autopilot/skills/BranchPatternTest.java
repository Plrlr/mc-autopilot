package io.github.plrlr.autopilot.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BranchPatternTest {
	private static final BlockPos START = new BlockPos(0, BranchPattern.Y, 0);

	@Test void connectedLanesStayLevelAndNeverRedigTheirOwnCells() {
		for (Direction along : Direction.Plane.HORIZONTAL) {
			BlockPos p = START;
			Set<BlockPos> tunnel = new HashSet<>(Set.of(p));
			for (int step = 0; step < 10 * (BranchPattern.LENGTH + BranchPattern.SPACING); step++) {
				p = p.relative(BranchPattern.direction(step, along));
				assertEquals(BranchPattern.Y, p.getY());
				assertTrue(tunnel.add(p), "revisited " + p);
			}
		}
	}

	@Test void twoRockColumnsBetweenLanesHaveExposedFaces() {
		int length = BranchPattern.LENGTH;
		int spacing = BranchPattern.SPACING;
		Set<BlockPos> tunnel = new HashSet<>(Set.of(START));
		BlockPos p = START;
		for (int step = 0; step < 2 * length + spacing; step++) {
			p = p.relative(BranchPattern.direction(step, Direction.EAST));
			tunnel.add(p);
		}
		assertEquals(START.south(spacing), p);
		assertEquals(3, spacing);
		for (int x = 1; x < length; x++) {
			for (int z = 1; z < spacing; z++) {
				BlockPos rock = START.offset(x, 0, z);
				assertFalse(tunnel.contains(rock));
				assertTrue(tunnel.contains(rock.north()) || tunnel.contains(rock.south()));
			}
		}
	}

	@Test void eachStepOpensOnlyHeadAndBodyAndKeepsItsFloor() {
		assertArrayEquals(new BlockPos[]{START.above(), START}, BranchPattern.cut(START));
	}

	@Test void visibleFluidAndBedrockStopDiggingBeforeTheNextSwing() {
		for (String hazard : new String[]{"water", "lava", "bedrock"}) {
			PortalSiteTest.Terrain terrain = new PortalSiteTest.Terrain();
			terrain.blocks.put(START.above(), hazard);
			assertFalse(BranchPattern.diggable(START, terrain), hazard);
		}
		for (BlockPos cell : BranchPattern.cut(START)) {
			for (Direction side : Direction.values()) {
				PortalSiteTest.Terrain terrain = new PortalSiteTest.Terrain();
				terrain.blocks.put(cell.relative(side), "lava");
				assertFalse(BranchPattern.diggable(START, terrain));
			}
		}
	}

	@Test void steppingRequiresTwoOpenCellsAndASolidDryFloor() {
		PortalSiteTest.Terrain terrain = new PortalSiteTest.Terrain();
		assertFalse(BranchPattern.walkable(START, terrain));
		terrain.blocks.put(START, "air");
		assertFalse(BranchPattern.walkable(START, terrain));
		terrain.blocks.put(START.above(), "air");
		assertTrue(BranchPattern.walkable(START, terrain));
		for (String hazard : new String[]{"air", "water", "lava"}) {
			terrain.blocks.put(START.below(), hazard);
			assertFalse(BranchPattern.walkable(START, terrain), hazard);
		}
	}

	@Test void hiddenLavaIsNeverReadAndNewlyExposedLavaStopsTheDig() {
		Set<BlockPos> visible = new HashSet<>(Set.of(START, START.above()));
		BlockPos lava = START.east();
		PortalSiteTest.Terrain terrain = new PortalSiteTest.Terrain() {
			@Override String at(BlockPos p) {
				assertTrue(visible.contains(p), "read hidden block " + p);
				return p.equals(lava) ? "lava" : "stone";
			}
		};
		FairProbe before = new FairProbe(terrain, visible::contains);
		assertTrue(BranchPattern.diggable(START, before));
		assertTrue(before.solid(lava), "unknown cells are assumed rock");
		assertFalse(before.free(lava));
		assertFalse(before.hard(lava));
		visible.add(lava);
		assertFalse(BranchPattern.diggable(START, new FairProbe(terrain, visible::contains)));
	}
}
