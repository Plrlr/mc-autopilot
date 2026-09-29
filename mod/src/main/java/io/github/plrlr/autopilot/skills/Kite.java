package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.state.Perception;
import net.minecraft.world.entity.Entity;

import java.util.Set;

/** Face a pursuing melee mob while gaining space for charged sword hits. */
public final class Kite extends Skill {
	private static final Set<String> MELEE = Set.of("zombie", "husk", "drowned", "spider", "cave_spider", "piglin", "enderman");
	private Entity target;
	private int farTicks;
	private int blockedTicks;

	public static boolean suits(Perception.Seen mob) {
		return mob != null && mob.dist() <= 6 && MELEE.contains(mob.type());
	}

	@Override public String name() { return "kite"; }

	@Override protected void start() {
		timeoutTicks = 20 * 25;
		Perception.Seen mob = Perception.look(12).nearestHostile();
		if (!suits(mob)) { fail(Fail.NOT_FOUND, "no close melee pursuer"); return; }
		target = mob.entity();
		CombatSkills.holdWeapon();
		Bari.stop();
	}

	@Override protected void tick() {
		if (!target.isAlive() || target.isRemoved()) { Facts.report("threat_cleared"); done("pursuer gone"); return; }
		var pl = Mc.player();
		var o = Mc.mc().options;
		double distance = pl.distanceTo(target);
		if (distance > 12 && ++farTicks >= 40) { Facts.report("threat_cleared"); done("out of pursuit range"); return; }
		if (distance <= 12) farTicks = 0;
		if (!Mc.canSee(target) && ++blockedTicks > 60) { fail(Fail.UNREACHABLE, "pursuer behind cover"); return; }
		if (Mc.canSee(target)) blockedTicks = 0;
		Mc.lookAt(target.getBoundingBox().getCenter());
		CombatFootwork.releaseMovement();
		boolean shield = Items2.id(pl.getOffhandItem()).equals("shield");
		boolean charged = pl.getAttackStrengthScale(0.5f) >= 0.9f;
		boolean strike = distance <= 3 && charged && Mc.canSee(target);
		o.keyUse.setDown(shield && !strike);
		if (strike) {
			if (pl.isUsingItem()) Mc.mc().gameMode.releaseUsingItem(pl);
			Mc.mc().gameMode.attack(pl, target);
			Mc.swing();
			return;
		}
		// A two-cell lookahead keeps the retreat from backing off a ledge or into lava.
		if (distance < 5 && CombatFootwork.safeDistance(pl, -1, 0, 2)) o.keyDown.setDown(true);
		else if (distance > 3 && distance < 6 && CombatFootwork.safeDistance(pl, 1, 0, 2)) o.keyUp.setDown(true);
	}
}
