package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.phys.Vec3;

/**
 * gaze_avoid (gene safety.enderman_gaze): don't meet an enderman's eyes, in any dimension.
 *
 * An enderman turns on a player who looks at its head with a clear line of sight from up to 64
 * blocks (Enderman.isBeingStaredBy: within a few degrees). The bot's camera points wherever
 * Baritone mines or walks, so it provoked one while mining iron in the local trial night2
 * (2026-09-29, "slain by Enderman"). Only the End had this guard (EndRoutes.EndGuard).
 * When the view comes within CONE degrees of a visible enderman's eyes, stop Baritone (it aims the
 * camera) and look down for a moment; the planner then resumes the work.
 */
public final class EndermanGaze extends Skill {
	static final double CONE = 15;
	private static long lastMs;

	/** A visible enderman within 64 blocks whose eyes are close to where we look (and no reflex just ran). */
	public static boolean threat() {
		LocalPlayer pl = Mc.player();
		if (pl == null || System.currentTimeMillis() - lastMs < 3000) return false;
		Vec3 eye = pl.getEyePosition(), look = pl.getViewVector(1f);
		for (Entity e : Mc.mc().level.entitiesForRendering()) {
			if (!(e instanceof Enderman em) || em.isCreepy() || em.distanceTo(pl) > 64 || !Mc.canSee(em)) continue;
			Vec3 to = em.getEyePosition().subtract(eye).normalize();
			if (look.dot(to) > Math.cos(Math.toRadians(CONE))) return true;
		}
		return false;
	}

	@Override
	public String name() {
		return "gaze_avoid";
	}

	@Override
	public boolean ownsSafety() {
		return false;
	}

	@Override
	protected void start() {
		lastMs = System.currentTimeMillis();
		timeoutTicks = 20 * 3;
		Bari.stop();
	}

	@Override
	protected void tick() {
		Mc.player().setXRot(Math.max(60f, Mc.player().getXRot()));
		if (ticks >= 30) done("looked away from an enderman");
	}
}
