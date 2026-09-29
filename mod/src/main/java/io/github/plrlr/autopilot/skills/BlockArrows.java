package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.state.Perception;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Set;

/**
 * block_arrows: close in on a skeleton behind a raised shield, lower it only to strike.
 *
 * 72 of 281 deaths in generations 41-46 were arrows. The attack skill walks straight at a
 * skeleton with the shield down and takes every arrow on the way; a player holds the shield up
 * facing it (arrows stop dead) and walks in. Without a shield: weave (strafe every half second),
 * which spoils the skeleton's lead, and never turn our back while it can see us.
 */
public final class BlockArrows extends Skill {
	static final Set<String> ARCHERS = Set.of("skeleton", "stray", "bogged");

	private Entity target;
	private int unseen;
	private int weave = 1;

	public static boolean suits(Perception.Seen mob) {
		return mob != null && ARCHERS.contains(mob.type()) && mob.dist() <= 24;
	}

	@Override
	public String name() {
		return "block_arrows";
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 30;
		Perception seen = Perception.look(24);
		Perception.Seen mob = null;
		for (Perception.Seen s : seen.mobs) if (s.hostile() && ARCHERS.contains(s.type())) {
			mob = s;
			break;
		}
		if (mob == null) {
			fail(Fail.NOT_FOUND, "no skeleton in sight");
			return;
		}
		target = mob.entity();
		Bari.stop();
		CombatSkills.holdWeapon();
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		var o = Mc.mc().options;
		CombatFootwork.releaseMovement();
		if (!target.isAlive() || target.isRemoved()) {
			Act.shield(false);
			Facts.report("threat_cleared");
			done("skeleton down");
			return;
		}
		if (!Mc.canSee(target)) {
			// Out of its sight is safe too; don't hunt it through a cave.
			if (++unseen > 60) {
				Act.shield(false);
				done("out of its line of fire");
			}
			return;
		}
		unseen = 0;
		Mc.lookAt(target.getBoundingBox().getCenter());
		double d = pl.distanceTo(target);
		if (d <= 3.2 && Act.charged()) {
			Act.strike(target);
			return;
		}
		boolean shield = Act.hasShield();
		Act.shield(shield);
		// Forward only onto safe ground (no drop, no lava): a skeleton on a ledge isn't worth a fall.
		if (d > 2.5 && CombatFootwork.safe(pl, 1, 0)) o.keyUp.setDown(true);
		if (!shield && d > 4) {
			// Weave: a strafe that flips every 10 ticks, onto safe ground only.
			if (ticks % 10 == 0) weave = -weave;
			if (CombatFootwork.safe(pl, 0, weave)) (weave > 0 ? o.keyRight : o.keyLeft).setDown(true);
		}
	}

	@Override
	protected void cleanup() {
		Act.shield(false);
		super.cleanup();
	}
}
