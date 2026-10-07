package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class SurfaceSwim extends EvolvedSkill {
	private final Set<BlockPos> aside = new HashSet<>();
	private BlockPos land;
	private float heading;
	private Vec3 checkpoint;
	private int stalls;

	public static Option offer(Context c) {
		if (!Player.inWater()) return null;
		String ls = c.lastSkill();
		String code = c.lastCode();
		boolean shoreFailed = "shore".equals(ls) && ("TIMEOUT".equals(code) || "STUCK".equals(code));
		boolean selfFailed = "surface_swim".equals(ls) && code != null && !"INTERRUPTED".equals(code);
		boolean mainShore = c.main() != null && "shore".equals(c.main().skill());
		if (!shoreFailed && !(selfFailed && mainShore)) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 180) return null;
		return new Option("surface_swim", null, "shore stalled in water: swim by hand to land");
	}

	@Override
	public String name() { return "surface_swim"; }

	@Override
	protected void start() {
		timeoutTicks = 20 * 70;
		Player.stopWalking();
		heading = Player.yaw();
		land = memory.nearestLand(aside);
		checkpoint = Player.pos();
	}

	@Override
	protected void tick() {
		if (!Player.inWater() && Player.onGround()) {
			done("out of the water at " + Player.feet());
			return;
		}
		Vec3 p = Player.pos();
		if (ticks > 0 && ticks % 100 == 0) {
			double dx = p.x - checkpoint.x, dz = p.z - checkpoint.z;
			if (dx * dx + dz * dz < 2.25) {
				stalls++;
				if (land != null) {
					aside.add(land);
					land = memory.nearestLand(aside);
				} else {
					heading += 90;
				}
				if (stalls >= 8) {
					fail(Fail.STUCK, "no headway after " + stalls + " stalls");
					return;
				}
			}
			checkpoint = p;
		}
		float yaw = heading;
		if (land != null) {
			double dx = land.getX() + 0.5 - p.x, dz = land.getZ() + 0.5 - p.z;
			if (dx * dx + dz * dz > 0.25) yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		}
		boolean lowAir = Player.air() < 120;
		if (lowAir && Player.eyesInWater()) {
			// Get a breath first: look up and rise.
			Player.turn(yaw, -70);
			Player.press(Player.Key.SPRINT, false);
			Player.press(Player.Key.FORWARD, true);
			Player.press(Player.Key.JUMP, true);
			return;
		}
		Player.turn(yaw, -8);
		Player.press(Player.Key.FORWARD, true);
		Player.press(Player.Key.SPRINT, !Player.eyesInWater());
		Player.press(Player.Key.JUMP, true);
	}
}
