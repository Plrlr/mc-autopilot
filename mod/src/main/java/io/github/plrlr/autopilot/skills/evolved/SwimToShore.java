package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class SwimToShore extends EvolvedSkill {
	/** Land we tried and couldn't reach, so we don't keep aiming at it. */
	private static final Set<BlockPos> ASIDE = new HashSet<>();
	private BlockPos land;
	private float swimYaw;
	private int dryTicks, landTicks;
	private double lastProgressDist = Double.MAX_VALUE;
	private int noCloser;

	public static Option offer(Context c) {
		if (!"explore".equals(c.lastSkill()) || !"HAZARD".equals(c.lastCode())) return null;
		if (c.lastDetail() == null || !c.lastDetail().contains("water")) return null;
		if (!Player.inWater()) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 15) return null;
		if (!"overworld".equals(shortDim())) return null;
		return new Option("swim_to_shore", null, "explore hit open water: swim to shore first");
	}

	private static String shortDim() {
		String d = Player.dimension();
		if (d == null) return "";
		int i = d.indexOf(':');
		return i >= 0 ? d.substring(i + 1) : d;
	}

	@Override
	public String name() { return "swim_to_shore"; }

	@Override
	protected void start() {
		timeoutTicks = 20 * 80;
		Vec3 p = Player.pos();
		int h = memory.exploreHeading(p.x, p.z, 120);
		double a = h * Math.PI / 4;
		swimYaw = (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a)));
		pickLand();
	}

	private void pickLand() {
		BlockPos l = memory.nearestLand(ASIDE);
		if (l != null && l.distSqr(Player.feet()) > 96 * 96) l = null;
		land = l;
		landTicks = 0;
		noCloser = 0;
		lastProgressDist = Double.MAX_VALUE;
		if (land != null) {
			Player.releaseAll();
			Player.walkNear(land, 1);
		}
	}

	@Override
	protected void tick() {
		if (!Player.inWater() && Player.onGround()) {
			if (++dryTicks >= 20) {
				Player.stopWalking();
				done("reached dry ground at " + Player.feet());
			}
			return;
		}
		dryTicks = 0;
		if (land == null) {
			// No land known: hold one heading at the surface, look for shore every second.
			if (ticks % 20 == 0) {
				pickLand();
				if (land != null) return;
			}
			Player.turn(swimYaw, 0);
			Player.press(Player.Key.FORWARD, true);
			Player.press(Player.Key.SPRINT, true);
			Player.press(Player.Key.JUMP, true);
			return;
		}
		landTicks++;
		Player.press(Player.Key.JUMP, Player.eyesInWater());
		if (landTicks % 20 == 0) {
			double d = Math.sqrt(land.distSqr(Player.feet()));
			if (d < lastProgressDist - 0.5) {
				lastProgressDist = d;
				noCloser = 0;
			} else noCloser++;
			if (noCloser >= 6 || (landTicks > 60 && !Player.walking() && d > 3)) {
				ASIDE.add(land.immutable());
				if (ASIDE.size() > 64) ASIDE.clear();
				Player.stopWalking();
				pickLand();
				return;
			}
		}
		if (landTicks > 20 && !Player.walking()) Player.walkNear(land, 1);
	}

	@Override
	protected void cleanup() {
		Player.stopWalking();
		super.cleanup();
		if (result() != null && !result().ok() && result().code() == Fail.TIMEOUT && land != null) ASIDE.add(land.immutable());
	}
}
