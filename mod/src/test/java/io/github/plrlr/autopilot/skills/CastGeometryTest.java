package io.github.plrlr.autopilot.skills;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CastGeometryTest {
	@Test
	void offsetsStayInTheFacesPlane() {
		// A face pointing along x varies in y and z, one along z in x and y, a top face in x and z.
		assertEquals(new Vec3(0, 0.2, 0.3), CastGeometry.offset(Direction.EAST, 0.3, 0.2));
		assertEquals(new Vec3(0.3, 0.2, 0), CastGeometry.offset(Direction.NORTH, 0.3, 0.2));
		assertEquals(new Vec3(0.3, 0, 0.2), CastGeometry.offset(Direction.UP, 0.3, 0.2));
	}
}
