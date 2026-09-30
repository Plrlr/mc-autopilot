package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * Treading water while our own code breaks a block (gene mine.tread_water). A player in water sinks
 * unless jump is held, and our miners held only attack: the bot drifted down mid-break, the block
 * slid out of view or reach, and the break restarted (the user saw it "just float down" while
 * mining at a shore). Floating also mines 5x slower than standing, 25x with the head underwater.
 *
 * So: floating, with the block at or above our feet, hold jump to stay up; with the block below,
 * let go and sink toward it (standing on the bottom mines faster than floating). Jump is released
 * the tick after the last break call, and forgotten when a skill releases every key, so it never
 * fights the drowning reflex, which holds jump itself.
 */
public final class Swim {
	private Swim() {}

	private static boolean held;
	private static long lastPress = Long.MIN_VALUE;
	private static long now;

	/** Call on each tick our code spends breaking `target`. */
	static void whileBreaking(BlockPos target) {
		if (!Tune.on("mine.tread_water")) return;
		LocalPlayer pl = Mc.player();
		boolean up = pl.isInWater() && !pl.onGround() && target.getY() + 1 > pl.getY();
		if (up) {
			Mc.mc().options.keyJump.setDown(true);
			held = true;
			lastPress = now;
		} else if (held) {
			release();
		}
	}

	/** Start of each autopilot tick: let go of jump once no miner asked for it last tick. */
	public static void newTick(long tick) {
		now = tick;
		if (held && lastPress < tick - 1) release();
	}

	/** Every key was just released (a skill ended or was aborted): jump is no longer ours. */
	public static void forget() {
		held = false;
	}

	private static void release() {
		Mc.mc().options.keyJump.setDown(false);
		held = false;
	}
}
