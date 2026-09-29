package io.github.plrlr.autopilot;

import io.github.plrlr.autopilot.plan.Option;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Deque;
final class StuckWatch {
	private Vec3 lastPos;
	private long lastMoveTick;

	void resetPosition() { lastPos = null; }
	/** Where the bot was, once a second, while a moving skill ran (the stuck box check). */
	private final Deque<Vec3> recentPos = new ArrayDeque<>();
	/** The inventory at the last stuck sample: a gain means the skill is working, not stuck. */
	private int stuckInvHash;
	/** Skills that are supposed to move the bot. Crafting, smelting, eating, hiding or fighting in place are not stuck. */
	private static final java.util.Set<String> MOVING = java.util.Set.of("collect", "explore", "shore", "goto", "retreat", "pickup",
			"fill_bucket", "locate_stronghold");

	/**
	 * Stuck = a moving skill has kept the bot inside a small square (gene stuck.box, 2 blocks) for
	 * stuck.window_s seconds, whatever Baritone says it's doing. The old check only counted while
	 * Baritone reported walking, so an idle Baritone (a mine search in open water) left the bot
	 * standing for minutes (laptop run 2026-09-27: 150 s per try, the same collect again after).
	 * Mining a block is standing still on purpose. Then the unstuck reflex swims, walks, tunnels or
	 * climbs out, and the action that got stuck counts as a failure (paused after repeats).
	 */
	void trackStuck(Autopilot a, LocalPlayer pl) {
		Vec3 p = pl.position();
		if (lastPos == null || p.distanceToSqr(lastPos) > 0.25) {
			lastPos = p;
			lastMoveTick = a.tick;
		}
		if (a.skill == null || a.skillIsReflex || !MOVING.contains(a.skill.name()) || Mc.mc().gameMode.isDestroying() || a.skill.workingInPlace()) {
			recentPos.clear();
			return;
		}
		if (a.tick % 20 != 0) return;
		// Gene stuck.item_progress: an item gained is progress. Gens 57-58's logs: 41 of 78 "stuck"
		// calls were collect trips mining in place (coal, stone, iron); unstuck walked them off
		// and the same collect then succeeded, so the call cost a trip and found nothing wrong.
		int inv = a.inventoryHash();
		if (inv != stuckInvHash && Tune.on("stuck.item_progress")) {
			stuckInvHash = inv;
			recentPos.clear();
			return;
		}
		stuckInvHash = inv;
		recentPos.addLast(p);
		int window = Tune.i("stuck.window_s");
		while (recentPos.size() > window) recentPos.removeFirst();
		if (recentPos.size() < window) return;
		double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9, minZ = 1e9, maxZ = -1e9;
		for (Vec3 v : recentPos) {
			minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
			minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
			minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z);
		}
		double box = Tune.get("stuck.box");
		// Swimming in place bobs vertically without making progress toward a block on shore.
		if (!StuckBox.stalled(maxX - minX, maxY - minY, maxZ - minZ, box,
				pl.isInWater(), Tune.on("stuck.ignore_water_bob"))) return;
		recentPos.clear();
		String what = a.skillOption.label();
		a.log.event("stuck", what + " at " + pl.blockPosition().toShortString() + (pl.isInWater() ? " in water" : ""));
		a.abortSkill("stuck: stayed inside " + box + " blocks for " + window + " s", false);
		if (Tune.on("skill.drain_tunnel") && io.github.plrlr.autopilot.skills.Fluids.floodedTunnel())
			a.startSkill(new Option("drain_tunnel", null, "stuck in a flooded tunnel while " + what), "reflex_stuck", true);
		else a.startSkill(new Option("unstuck", null, "not moving while " + what), "reflex_stuck", true);
		a.reflexCooldownUntil = a.tick + 40;
	}

}
