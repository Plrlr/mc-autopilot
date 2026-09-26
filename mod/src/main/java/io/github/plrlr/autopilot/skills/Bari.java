package io.github.plrlr.autopilot.skills;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import net.minecraft.world.level.block.Block;
import io.github.plrlr.autopilot.Mc;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

/** Thin wrapper over Baritone, which does the walking, digging and mining for our skills. */
public final class Bari {
	private Bari() {}

	private static Boolean savedChatControl;

	public static IBaritone get() {
		return BaritoneAPI.getProvider().getPrimaryBaritone();
	}

	/**
	 * Settings that keep Baritone playing fair and safe. legitMine stops it from targeting ores
	 * it hasn't seen (no x-ray); it branch-mines like a player instead.
	 */
	public static void applyFairPlay() {
		Settings s = BaritoneAPI.getSettings();
		s.legitMine.value = true;
		s.allowOnlyExposedOres.value = false;
		s.exploreForBlocks.value = false;
		s.allowBreak.value = true;
		s.allowPlace.value = true;
		s.allowInventory.value = true;
		s.autoTool.value = true;
		s.allowSprint.value = true;
		s.allowParkour.value = false;
		s.allowDownward.value = true;
		s.mineScanDroppedItems.value = true;
		s.antiCheatCompatibility.value = true;
		s.renderPath.value = true;
		s.renderGoal.value = true;
		s.disconnectOnArrival.value = false;
		s.maxFallHeightNoWater.value = 3;
		s.avoidance.value = true;
		// Lets a goal inside a portal actually walk into it.
		s.enterPortal.value = true;
		// Our own chat commands start with "!", Baritone's with "#"; keep them apart.
		if (savedChatControl == null) savedChatControl = s.chatControl.value;
		s.chatControl.value = false;
	}

	public static void restoreUserSettings() {
		if (savedChatControl != null) BaritoneAPI.getSettings().chatControl.value = savedChatControl;
		savedChatControl = null;
	}

	/**
	 * Fair mining for ores only: with legitMine Baritone targets an ore only once it can really
	 * see it, and branch-mines otherwise. Trees, sand, gravel and stone are visible surface
	 * features, so for those it may use its normal search of loaded chunks.
	 */
	public static void setLegitMine(boolean on) {
		BaritoneAPI.getSettings().legitMine.value = on;
	}

	public static void setMineY(Integer y) {
		if (y != null) BaritoneAPI.getSettings().legitMineYLevel.value = y;
	}

	public static void path(Goal goal) {
		get().getCustomGoalProcess().setGoalAndPath(goal);
	}

	public static boolean pathing() {
		return get().getCustomGoalProcess().isActive() || get().getPathingBehavior().isPathing();
	}

	public static boolean anyActive() {
		IBaritone b = get();
		return b.getCustomGoalProcess().isActive() || b.getMineProcess().isActive() || b.getExploreProcess().isActive()
				|| b.getGetToBlockProcess().isActive() || b.getBuilderProcess().isActive() || b.getPathingBehavior().isPathing();
	}

	public static void stop() {
		try {
			IBaritone b = get();
			b.getPathingBehavior().cancelEverything();
			b.getInputOverrideHandler().clearAllKeys();
		} catch (Exception e) {
			// Baritone not ready (no world yet): nothing to stop.
		}
	}

	/** Blocks for a pattern: "*_log" means every non-stripped log block, anything else an exact id. */
	public static Block[] blocks(List<String> patterns) {
		List<Block> out = new ArrayList<>();
		for (String p : patterns) {
			if (p.startsWith("*")) {
				String suffix = p.substring(1);
				for (Block b : BuiltInRegistries.BLOCK) {
					String id = Mc.id(b);
					if (id.endsWith(suffix) && !id.startsWith("stripped_")) out.add(b);
				}
			} else {
				Block b = Mc.block(p);
				if (b != null) out.add(b);
			}
		}
		return out.toArray(new Block[0]);
	}
}
