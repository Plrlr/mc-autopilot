package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;

/** Short key-driven steps during combat, only onto ground the player can see. */
final class CombatFootwork {
	private CombatFootwork() {}

	static boolean safe(LocalPlayer pl, double forward, double right) {
		return safeDistance(pl, forward, right, 1);
	}

	/**
	 * Forward is (-sin yaw, cos yaw) in Minecraft's axes and the player's right is (-cos yaw, -sin yaw)
	 * (yaw 0 faces south, +z; its right hand points west, -x). Until 2026-09-29 "right" here was
	 * (cos, sin), the player's LEFT, while every caller pressed the right-hand key for right > 0: the
	 * creeper escape, the shield-less weave, strafing and the zig-zag approach checked one side and
	 * stepped to the other (found in Codex's zombie-pack code, which had compensated).
	 */
	static boolean safeDistance(LocalPlayer pl, double forward, double right, int cells) {
		if (!pl.onGround()) return false;
		double yaw = Math.toRadians(pl.getYRot());
		BlockPos feet = pl.blockPosition();
		for (int step = 1; step <= cells; step++) {
			int dx = (int) Math.round((-Math.sin(yaw) * forward - Math.cos(yaw) * right) * step);
			int dz = (int) Math.round((Math.cos(yaw) * forward - Math.sin(yaw) * right) * step);
			if (dx == 0 && dz == 0) return false;
			if (dx != 0 && dz != 0 && (!safeCell(feet.offset(dx, 0, 0)) || !safeCell(feet.offset(0, 0, dz)))) return false;
			if (!safeCell(feet.offset(dx, 0, dz))) return false;
		}
		return true;
	}

	private static boolean safeCell(BlockPos at) {
		BlockPos floor = at.below();
		if (!Mc.canSee(at) || !Mc.canSee(at.above()) || !Mc.canSee(floor)) return false;
		if (!Mc.free(at) || !Mc.free(at.above()) || !Mc.solid(floor)) return false;
		return !hazard(at) && !hazard(floor);
	}

	private static boolean hazard(BlockPos at) {
		var state = Mc.state(at);
		String id = Mc.id(state.getBlock());
		return state.getFluidState().is(FluidTags.LAVA) || id.equals("fire") || id.equals("soul_fire");
	}

	static void releaseMovement() {
		var o = Mc.mc().options;
		o.keyUp.setDown(false);
		o.keyDown.setDown(false);
		o.keyLeft.setDown(false);
		o.keyRight.setDown(false);
		o.keySprint.setDown(false);
	}
}
