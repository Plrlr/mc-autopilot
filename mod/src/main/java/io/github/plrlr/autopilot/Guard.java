package io.github.plrlr.autopilot;

import io.github.plrlr.autopilot.skills.Bari;
import net.minecraft.client.player.LocalPlayer;
final class Guard {
	/** True while guard() holds the shield up (so it lets go of the key itself afterwards). */
	private boolean guarding;

	/**
	 * Shield guard (gene combat.shield_guard): skeleton arrows and creeper blasts caused 67 of 181
	 * deaths in generations 6-9, and a raised shield facing them stops both. A creeper about to
	 * blow within 5 blocks: everything pauses, face it, block. A skeleton drawing its bow at us in
	 * sight within 20: face it and block, but only while we're not walking a path (looking at the
	 * skeleton would steer Baritone off it) or fighting.
	 */
	void guard(Autopilot a, LocalPlayer pl) {
		var o = Mc.mc().options;
		// A ghast's fireball coming at us: a hit sends it back (gene combat.deflect).
		if (Tune.on("combat.deflect") && a.tick % 4 == 0) {
			for (var e : Mc.mc().level.entitiesForRendering()) {
				if (e instanceof net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball fb && fb.distanceTo(pl) < 4.5
						&& fb.getDeltaMovement().dot(pl.position().subtract(fb.position())) > 0) {
					Mc.lookAt(fb.getBoundingBox().getCenter());
					Mc.mc().gameMode.attack(pl, fb);
					Mc.swing();
					a.log.event("guard", "fireball hit back");
					return;
				}
			}
		}
		net.minecraft.world.entity.Entity threat = null;
		boolean creeper = false;
		if (Tune.on("combat.shield_guard") && Items2.id(pl.getOffhandItem()).equals("shield")
				&& !Items2.isAnyFood(pl.getMainHandItem()) && !Items2.id(pl.getMainHandItem()).contains("bucket")
				&& (a.skill == null || !java.util.Set.of("eat", "clutch", "build_portal", "fill_bucket", "place", "barter",
				"enderman_boat", "unstuck", "shelter", "sleep", "craft", "smelt").contains(a.skill.name()))) {
			boolean still = a.skill == null || !Bari.pathing() || a.skill.name().equals("attack");
			for (var e : Mc.mc().level.entitiesForRendering()) {
				double d = e.distanceTo(pl);
				if (e instanceof net.minecraft.world.entity.monster.Creeper c && d < 5 && (c.isIgnited() || c.getSwellDir() > 0)) {
					threat = c;
					creeper = true;
					break;
				}
				if (still && threat == null && e instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton sk && d < 20
						&& sk.isUsingItem() && Mc.canSee(sk)) threat = sk;
			}
		}
		if (threat == null) {
			if (guarding) {
				o.keyUse.setDown(false);
				guarding = false;
			}
			return;
		}
		if (creeper && Bari.pathing()) Bari.stop();
		if (!guarding) a.log.event("guard", (creeper ? "creeper" : "skeleton") + " at " + Math.round(threat.distanceTo(pl)));
		guarding = true;
		Mc.lookAt(threat.getBoundingBox().getCenter());
		o.keyUse.setDown(true);
	}

}
