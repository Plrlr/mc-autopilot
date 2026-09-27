package io.github.plrlr.autopilot.skills;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The bow aim: an arrow flown with the chosen yaw and pitch must land on the target. */
class BallisticAimTest {
	/** Height error when the arrow reaches the target's horizontal distance (vanilla arrow physics). */
	private static double miss(Vec3 eye, Vec3 target, float[] rot) {
		double yaw = Math.toRadians(rot[0]), pitch = Math.toRadians(-rot[1]);
		double vh = Math.cos(pitch) * 3.0, vy = Math.sin(pitch) * 3.0;
		double vx = -Math.sin(yaw) * vh, vz = Math.cos(yaw) * vh;
		double x = eye.x, y = eye.y - 0.1, z = eye.z;
		double h = Math.hypot(target.x - eye.x, target.z - eye.z);
		for (int t = 0; t < 200; t++) {
			x += vx;
			y += vy;
			z += vz;
			vx *= 0.99;
			vz *= 0.99;
			vy = vy * 0.99 - 0.05;
			if (Math.hypot(x - eye.x, z - eye.z) >= h) return Math.abs(y - target.y) + Math.hypot(x - target.x, z - target.z) * 0.1;
		}
		return Double.MAX_VALUE;
	}

	@Test
	void hitsACrystalOnATallPillar() {
		Vec3 eye = new Vec3(0, 64.62, 0);
		Vec3 crystal = new Vec3(30, 100, 25);
		float[] rot = CombatSkills.ballisticAim(eye, crystal, Vec3.ZERO);
		assertNotNull(rot);
		assertTrue(miss(eye, crystal, rot) < 0.75, "missed by " + miss(eye, crystal, rot));
	}

	@Test
	void hitsAFarTargetOnTheLevel() {
		Vec3 eye = new Vec3(0, 64.62, 0);
		Vec3 far = new Vec3(-45, 64, 10);
		float[] rot = CombatSkills.ballisticAim(eye, far, Vec3.ZERO);
		assertNotNull(rot);
		assertTrue(miss(eye, far, rot) < 0.75);
		assertTrue(rot[1] < 0, "a far target needs the bow raised (negative pitch)");
	}

	@Test
	void outOfRangeGivesNull() {
		assertNull(CombatSkills.ballisticAim(new Vec3(0, 64, 0), new Vec3(400, 64, 0), Vec3.ZERO));
	}
}
