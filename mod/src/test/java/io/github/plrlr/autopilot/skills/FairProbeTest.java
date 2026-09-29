package io.github.plrlr.autopilot.skills;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FairProbeTest {
	@Test
	void hiddenPoolSupportIsNeverInspectedAndExposedFluidIsRejected() {
		BlockPos block = PortalSiteTest.O;
		Set<BlockPos> visible = new HashSet<>(Set.of(block));
		PortalSiteTest.Terrain terrain = guardedTerrain(visible);
		terrain.blocks.put(block.below(), "lava");
		assertFalse(PortalRoutes.ObsidianPool.exposedSupport(block, new FairProbe(terrain, visible::contains)));
		visible.add(block.below());
		assertFalse(PortalRoutes.ObsidianPool.exposedSupport(block, new FairProbe(terrain, visible::contains)));
		terrain.blocks.put(block.below(), "stone");
		assertTrue(PortalRoutes.ObsidianPool.exposedSupport(block, new FairProbe(terrain, visible::contains)));
	}

	@Test
	void hypotheticalBucketStandsDoNotReadHiddenGroundOrNeighbors() {
		BlockPos stand = PortalSiteTest.O;
		Set<BlockPos> visible = new HashSet<>(Set.of(stand, stand.above(), stand.below()));
		PortalSiteTest.Terrain terrain = guardedTerrain(visible);
		assertTrue(BucketSkills.standable(stand, new FairProbe(terrain, visible::contains)));
		visible.add(stand.east());
		terrain.blocks.put(stand.east(), "lava");
		assertFalse(BucketSkills.standable(stand, new FairProbe(terrain, visible::contains)));
		terrain.blocks.put(stand.east(), "water");
		assertTrue(BucketSkills.standable(stand, new FairProbe(terrain, visible::contains)), "a water bank is safe");
		assertFalse(BucketSkills.standable(stand.west(10), new FairProbe(terrain, visible::contains)));
	}

	@Test
	void raysAreCachedAndCappedEvenWhenTheCallerKeepsSearching() {
		AtomicInteger rays = new AtomicInteger();
		FairProbe seen = new FairProbe(new PortalSiteTest.Terrain(), p -> { rays.incrementAndGet(); return true; });
		for (int i = 0; i < FairProbe.RAY_BUDGET * 2; i++) {
			BlockPos p = PortalSiteTest.O.east(i);
			seen.free(p);
			seen.fluid(p);
			seen.hard(p);
		}
		assertEquals(FairProbe.RAY_BUDGET, rays.get());
	}

	private static PortalSiteTest.Terrain guardedTerrain(Set<BlockPos> visible) {
		return new PortalSiteTest.Terrain() {
			@Override public boolean lava(BlockPos p) { return at(p).equals("lava"); }
			@Override String at(BlockPos p) {
				assertTrue(visible.contains(p), "read hidden block " + p);
				return super.at(p);
			}
		};
	}
}
