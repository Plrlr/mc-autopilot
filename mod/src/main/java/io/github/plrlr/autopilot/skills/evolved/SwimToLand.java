package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class SwimToLand extends EvolvedSkill {
	private static final Set<BlockPos> TRIED = new HashSet<>();
	private BlockPos land;
	private double bestDist;
	private int lastGain;
	private int switches;
	private float blindYaw;

	public SwimToLand() {
		timeoutTicks = 20 * 45;
	}

	public static Option offer(Context c) {
		if (!"shore".equals(c.lastSkill())) return null;
		if (!"TIMEOUT".equals(c.lastCode()) && !"STUCK".equals(c.lastCode())) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 240) return null;
		boolean shoreMain = c.main() != null && "shore".equals(c.main().skill());
		if (!shoreMain && !Player.inWater()) return null;
		return new Option("swim_to_land", null,
				"shore just failed " + c.lastCode() + ": swim straight to seen land by keys instead of pathing");
	}

	@Override
	public String name() { return "swim_to_land"; }

	@Override
	protected void start() {
		if (TRIED.size() > 32) TRIED.clear();
		blindYaw = Player.yaw();
		pick();
	}

	private void pick() {
		land = memory.nearestLand(TRIED);
		bestDist = land == null ? Double.MAX_VALUE : hdist(land);
		lastGain = ticks;
	}

	private static double hdist(BlockPos p) {
		Vec3 v = Player.pos();
		double dx = p.getX() + 0.5 - v.x, dz = p.getZ() + 0.5 - v.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	@Override
	protected void tick() {
		if (!Player.inWater() && Player.onGround() && ticks > 2) {
			done("on dry ground at " + Player.feet());
			return;
		}
		if (Player.walking()) Player.stopWalking();
		if (land == null && ticks % 20 == 0) pick();

		boolean lowAir = Player.air() < 100 && Player.eyesInWater();
		float yaw = blindYaw;
		if (land != null) {
			Vec3 v = Player.pos();
			double dx = land.getX() + 0.5 - v.x, dz = land.getZ() + 0.5 - v.z;
			yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			double d = hdist(land);
			if (d < bestDist - 0.5) { bestDist = d; lastGain = ticks; }
			if (ticks - lastGain > 60) {
				TRIED.add(land.immutable());
				if (++switches > 4) {
					fail(Fail.STUCK, "no progress toward 4 seen land spots by swimming");
					return;
				}
				pick();
				if (land == null) blindYaw = Player.yaw() + 90f;
			}
		} else if (ticks - lastGain > 100) {
			blindYaw += 90f;
			lastGain = ticks;
		}

		if (lowAir) {
			Player.turn(yaw, -90f);
			Player.press(Player.Key.SPRINT, false);
			Player.press(Player.Key.FORWARD, true);
			Player.press(Player.Key.JUMP, true);
			return;
		}
		Player.turn(yaw, Player.eyesInWater() ? -25f : 0f);
		Player.press(Player.Key.FORWARD, true);
		Player.press(Player.Key.SPRINT, true);
		Player.press(Player.Key.JUMP, Player.inWater() || ticks % 10 < 5);
	}
}
