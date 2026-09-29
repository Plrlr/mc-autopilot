package io.github.plrlr.autopilot.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The one-block obsidian mold's site on made-up terrain (stone below y 64, air above). */
class MoldSiteTest {
	static final BlockPos FEET = new BlockPos(0, 64, 0);

	@Test
	void flatGroundMakesAMoldRightInFront() {
		MoldSite.Site s = MoldSite.at(FEET, Direction.EAST, new PortalSiteTest.Terrain());
		assertNotNull(s);
		assertEquals(FEET.east().below(), s.pit());
		assertEquals(FEET.east(2), s.water());
	}

	@Test
	void thePitMustBeWalledAndFloored() {
		PortalSiteTest.Terrain t = new PortalSiteTest.Terrain();
		// A cave under the pit: the lava would pour away (and the obsidian drop with it).
		t.blocks.put(FEET.east().below(2), "air");
		assertNull(MoldSite.at(FEET, Direction.EAST, t));
		t = new PortalSiteTest.Terrain();
		// An open side: lava would run out of the pit.
		t.blocks.put(FEET.east().below().north(), "air");
		assertNull(MoldSite.at(FEET, Direction.EAST, t));
	}

	@Test
	void noMoldNextToFluidsOrOnBedrock() {
		PortalSiteTest.Terrain t = new PortalSiteTest.Terrain();
		t.blocks.put(FEET.east(3), "lava");
		assertNull(MoldSite.at(FEET, Direction.EAST, t), "water spot touches lava");
		t = new PortalSiteTest.Terrain();
		t.blocks.put(FEET.east().below(), "bedrock");
		assertNull(MoldSite.at(FEET, Direction.EAST, t), "can't dig the pit");
		t = new PortalSiteTest.Terrain();
		t.blocks.put(FEET.east().below().south(), "lava");
		assertNull(MoldSite.at(FEET, Direction.EAST, t), "the pit's wall is lava");
	}

	@Test
	void theWaterNeedsAFloorAndTheStandHeadroom() {
		PortalSiteTest.Terrain t = new PortalSiteTest.Terrain();
		t.blocks.put(FEET.east(2).below(), "air");
		assertNull(MoldSite.at(FEET, Direction.EAST, t));
		t = new PortalSiteTest.Terrain();
		t.blocks.put(FEET.above(), "stone");
		assertNull(MoldSite.at(FEET, Direction.EAST, t));
	}

	@Test
	void findTakesTheNearestGoodStandAndSkipsBadOnes() {
		PortalSiteTest.Terrain t = new PortalSiteTest.Terrain();
		MoldSite.Site s = MoldSite.find(FEET, t, new HashSet<>());
		assertEquals(FEET, s.stand());
		MoldSite.Site next = MoldSite.find(FEET, t, Set.of(FEET));
		assertNotNull(next);
		assertNotEquals(FEET, next.stand());
		assertTrue(next.stand().distManhattan(FEET) <= 2);
	}

	@Test
	void aTunnelOneWideHasNoRoomForTheWaterSpot() {
		// Solid everywhere except a 1x2 pocket at the feet: nowhere to stand facing an open cell.
		PortalSiteTest.Terrain t = new PortalSiteTest.Terrain() {
			@Override
			String at(BlockPos p) {
				if (p.equals(FEET) || p.equals(FEET.above())) return "air";
				return "stone";
			}
		};
		assertNull(MoldSite.find(FEET, t, new HashSet<>()));
	}
}
