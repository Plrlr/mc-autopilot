package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.state.Perception;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.monster.Creeper;

/**
 * creeper_defuse: deal with a creeper the way players do. 30 of 281 deaths in generations 41-46
 * were creeper blasts.
 *
 *   - its fuse is lit (it swells) or it's within 3 blocks: sprint straight away from it; 1.5 s
 *     of fuse is enough to clear the blast if we go at once;
 *   - otherwise, with a charged sword and it in reach: a sprinting hit (knock-back sends it 3+
 *     blocks, which resets its fuse), then back off for a second and let it come again;
 *   - no weapon or not charged: keep 4-6 blocks, facing it.
 * Never stand still within 5 blocks of one.
 */
public final class CreeperDefuse extends Skill {
	private Creeper target;
	private int backOff;
	private int farTicks, sinceCloser;
	private double closest = Double.MAX_VALUE;

	public static boolean suits(Perception.Seen mob) {
		return mob != null && mob.type().equals("creeper") && mob.dist() <= 10;
	}

	@Override
	public String name() {
		return "creeper_defuse";
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 25;
		Perception.Seen c = Perception.look(12).nearest("creeper");
		if (c == null || !(c.entity() instanceof Creeper cr)) {
			fail(Fail.NOT_FOUND, "no creeper in sight");
			return;
		}
		target = cr;
		Bari.stop();
		CombatSkills.holdWeapon();
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		var o = Mc.mc().options;
		CombatFootwork.releaseMovement();
		o.keySprint.setDown(false);
		if (!target.isAlive() || target.isRemoved()) {
			Facts.report("threat_cleared");
			done("creeper gone");
			return;
		}
		double d = pl.distanceTo(target);
		if (d > 12) {
			if (++farTicks > 40) done("clear of the creeper");
			return;
		}
		farTicks = 0;
		boolean lit = target.isIgnited() || target.getSwellDir() > 0;
		if (lit || d < 3 || backOff > 0) {
			if (backOff > 0) backOff--;
			// Face away and sprint. Away = our position minus its position, flat.
			double ax = pl.getX() - target.getX(), az = pl.getZ() - target.getZ();
			pl.setYRot((float) Math.toDegrees(Math.atan2(-ax, az)));
			if (CombatFootwork.safe(pl, 1, 0)) {
				o.keyUp.setDown(true);
				o.keySprint.setDown(true);
			} else if (CombatFootwork.safe(pl, 0, 1)) o.keyRight.setDown(true);
			else if (CombatFootwork.safe(pl, 0, -1)) o.keyLeft.setDown(true);
			else if (lit) {
				// Cornered with the fuse lit: a block between us is the last cover.
				Mc.lookAt(target.getBoundingBox().getCenter());
				Act.place(pl.blockPosition().relative(pl.getDirection().getOpposite()));
			}
			return;
		}
		Mc.lookAt(target.getBoundingBox().getCenter());
		// A creeper that isn't coming at us is no job: done, and left alone for a minute so the
		// creeper reflex doesn't start us again. (First drill: 21 starts in a row, each waiting out the
		// 25 s limit 4-6 blocks from a creeper that stood still; 9 minutes lost, no progress.)
		if (d < closest - 0.5) {
			closest = d;
			sinceCloser = 0;
		} else if (++sinceCloser > 60 && d > 4) {
			CombatSkills.markUnreachable(target);
			Facts.report("threat_cleared");
			done("the creeper keeps its distance");
			return;
		}
		if (d <= 3.2 && Act.charged() && CombatSkills.canHitCreeper(new Perception.Seen(target, "creeper", d, true))) {
			// A sprinting hit knocks it back far enough to reset the fuse.
			pl.setSprinting(true);
			Act.strike(target);
			backOff = 20;
			return;
		}
		// Keep 4-6 blocks while the sword charges.
		if (d < 4 && CombatFootwork.safe(pl, -1, 0)) o.keyDown.setDown(true);
		else if (d > 6 && CombatFootwork.safe(pl, 1, 0)) o.keyUp.setDown(true);
	}
}
