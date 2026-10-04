package io.github.plrlr.autopilot.skills;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fight anchors (gene fortress.floor_anchor): a bridge or corridor floor is bricks in both directions;
 * the top of a wall is one line of them, and walking to it sent Baritone up or into the wall.
 */
class FloorPatchTest {
	@Test
	void aBridgeFloorIsAPatch() {
		Set<BlockPos> known = new HashSet<>();
		for (int x = 0; x < 5; x++)
			for (int z = 0; z < 5; z++) known.add(new BlockPos(x, 64, z));
		assertTrue(NetherSkills.floorPatch(new BlockPos(2, 64, 2), known));
		assertTrue(NetherSkills.floorPatch(new BlockPos(0, 64, 0), known), "a corner still has a neighbor each way");
	}

	@Test
	void aWallTopIsALineNotAPatch() {
		Set<BlockPos> known = new HashSet<>();
		for (int x = 0; x < 10; x++) known.add(new BlockPos(x, 70, 0));
		assertFalse(NetherSkills.floorPatch(new BlockPos(5, 70, 0), known));
	}

	@Test
	void aLoneBrickIsNotAFloor() {
		assertFalse(NetherSkills.floorPatch(new BlockPos(0, 64, 0), Set.of(new BlockPos(0, 64, 0))));
	}
}
