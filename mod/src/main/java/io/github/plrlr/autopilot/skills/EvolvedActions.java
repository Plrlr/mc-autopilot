package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Narrow bridge to the existing package-private tool and breaking helpers. */
public final class EvolvedActions {
	private EvolvedActions() {}

	private static final Act.Breaker BREAKER = new Act.Breaker();

	public static void holdBestToolFor(BlockPos p) {
		if (Mc.player() != null && Mc.canSee(p)) NightSkills.Shelter.holdBestTool(Mc.state(p));
	}

	/** True while breaking; unseen and unreachable cells are never inspected. */
	public static boolean mine(BlockPos p) {
		if (Mc.player() == null || Mc.mc().gameMode == null || !Mc.canSee(p)
				|| Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(p)) > Mc.reach() || Mc.free(p)) {
			stopMining();
			return false;
		}
		return !BREAKER.tick(p);
	}

	public static void stopMining() {
		BREAKER.stop();
	}
}
