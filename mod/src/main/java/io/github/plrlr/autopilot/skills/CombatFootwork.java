package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;

/** Short key-driven steps during combat, only onto ground the player can see. */
final class CombatFootwork {
	private CombatFootwork() {}

	static boolean safe(LocalPlayer pl, double forward, double right) {
		if (!pl.onGround()) return false;
		double yaw = Math.toRadians(pl.getYRot());
		int dx = (int) Math.round(-Math.sin(yaw) * forward + Math.cos(yaw) * right);
		int dz = (int) Math.round(Math.cos(yaw) * forward + Math.sin(yaw) * right);
		if (dx == 0 && dz == 0) return false;
		BlockPos feet = pl.blockPosition();
		if (dx != 0 && dz != 0 && (!safeCell(feet.offset(dx, 0, 0)) || !safeCell(feet.offset(0, 0, dz)))) return false;
		return safeCell(feet.offset(dx, 0, dz));
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
