package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * clutch: the water-bucket landing players do when they fall too far. Look straight down, pour
 * the water onto the ground just before landing (landing in water takes no fall damage), then
 * scoop it back up. Started by the fall reflex (Autopilot); needs a water bucket, and not in the
 * Nether (water boils there).
 */
public final class Clutch extends Skill {
	private enum Phase {FALL, LANDED}

	private Phase phase = Phase.FALL;
	private boolean poured;

	@Override
	public String name() {
		return "clutch";
	}

	@Override
	public boolean interruptible() {
		return false;
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 8;
		Bari.stop();
		if (Mc.count("water_bucket") == 0) fail(Fail.NEED_ITEM, "no water bucket");
	}

	/** Blocks from the feet down to the first solid block (up to 30), or -1. */
	public static int groundBelow(LocalPlayer pl) {
		BlockPos feet = pl.blockPosition();
		for (int d = 1; d <= 30; d++) {
			BlockPos p = feet.below(d);
			if (!Mc.state(p).getFluidState().isEmpty()) return -1; // falling into water or lava already
			if (Mc.solid(p)) return d - 1;
		}
		return -1;
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		switch (phase) {
			case FALL -> {
				if (pl.onGround() || pl.isInWater()) {
					phase = Phase.LANDED;
					return;
				}
				if (!poured && !Items2.id(pl.getMainHandItem()).equals("water_bucket")) {
					Mc.holdItem(s -> Items2.id(s).equals("water_bucket"));
					return;
				}
				pl.setXRot(90f);
				int ground = groundBelow(pl);
				// Pour in the last couple of blocks: within reach, before the landing tick.
				if (!poured && ground >= 0 && ground <= 2) {
					Mc.useItem();
					poured = true;
				}
			}
			case LANDED -> {
				// Take the water back: the route needs the bucket (casting the portal, obsidian).
				if (Mc.count("water_bucket") > 0 || !poured) {
					done(poured ? "landed in water" : "landed");
					return;
				}
				if (!Items2.id(pl.getMainHandItem()).equals("bucket")) {
					Mc.holdItem(s -> Items2.id(s).equals("bucket"));
					return;
				}
				pl.setXRot(90f);
				if (ticks % 4 == 0) Mc.useItem();
				if (ticks > 20 * 6) done("landed in water (couldn't scoop it back)");
			}
		}
	}
}
