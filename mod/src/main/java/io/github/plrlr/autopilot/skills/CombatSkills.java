package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.Perception;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.phys.Vec3;

/** Melee and bow skills. */
public final class CombatSkills {
	private CombatSkills() {}

	/** Best sword (or axe) into the hand; returns false if we have neither. */
	static boolean holdWeapon() {
		String best = null;
		int bestScore = -1;
		for (int i = 0; i < 36; i++) {
			String id = Items2.id(Mc.player().getInventory().getItem(i));
			int t = Items2.tier(id);
			int score = id.endsWith("_sword") ? 10 + t : id.endsWith("_axe") ? 5 + t : -1;
			if (score > bestScore) {
				bestScore = score;
				best = id;
			}
		}
		if (best == null) return false;
		String id = best;
		return Mc.holdItem(s -> Items2.id(s).equals(id));
	}

	/** attack <mob type>: walk up to the nearest one in sight and hit it until it dies. */
	public static final class Attack extends Skill {
		private Entity target;
		private BlockPos lastSeenAt;
		private int unseen;
		private int deadTicks = -1;

		@Override
		public String name() {
			return "attack";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 45;
			Perception.Seen s = Perception.look(32).nearest(arg == null ? "" : arg);
			if (s == null) {
				fail("no " + arg + " in sight");
				return;
			}
			target = s.entity();
			LocalPlayer pl = Mc.player();
			if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
			holdWeapon();
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			if (deadTicks >= 0) {
				// Stand where it died for a moment so the drops get picked up.
				if (deadTicks++ == 0 && lastSeenAt != null) Bari.path(new GoalNear(lastSeenAt, 0));
				if (deadTicks > 50) done("killed the " + arg);
				return;
			}
			if (!target.isAlive() || target.isRemoved()) {
				deadTicks = 0;
				return;
			}
			lastSeenAt = target.blockPosition();
			Entity hitBox = target;
			double dist = pl.distanceTo(target);
			if (target instanceof EnderDragon dragon) {
				// Damage goes through the dragon's body parts; aim for the closest one.
				for (EnderDragonPart part : dragon.getSubEntities()) {
					double d = pl.distanceTo(part);
					if (d < dist || hitBox == target) {
						dist = d;
						hitBox = part;
					}
				}
			}
			if (!Mc.canSee(target) && dist > 4) {
				if (++unseen > 100) {
					fail("lost sight of the " + arg);
					return;
				}
			} else unseen = 0;

			if (dist > 3.0) {
				if (ticks % 10 == 1) Bari.path(new GoalNear(target.blockPosition(), 1));
				return;
			}
			if (Bari.pathing()) Bari.stop();
			Mc.lookAt(hitBox.getBoundingBox().getCenter());
			// Wait for a full swing: spamming clicks does almost no damage.
			if (pl.getAttackStrengthScale(0.5f) >= 0.95f) {
				Mc.mc().gameMode.attack(pl, hitBox);
				Mc.swing();
			}
		}
	}

	/** shoot <mob type>: bow at a target out of reach, e.g. end crystals or the dragon. */
	public static final class Shoot extends Skill {
		private Entity target;
		private int drawTicks;
		private int shots;

		@Override
		public String name() {
			return "shoot";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60;
			if (Mc.count("bow") == 0 || Mc.count("arrow") == 0) {
				fail("need a bow and arrows");
				return;
			}
			Perception.Seen s = Perception.look(64).nearest(arg == null ? "" : arg);
			if (s == null) {
				fail("no " + arg + " in sight");
				return;
			}
			target = s.entity();
			Bari.stop();
			LocalPlayer pl = Mc.player();
			if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
			Mc.holdItem(st -> Items2.id(st).equals("bow"));
		}

		@Override
		protected void tick() {
			if (!target.isAlive() || target.isRemoved()) {
				done("destroyed the " + arg + " after " + shots + " shots");
				return;
			}
			if (Mc.count("arrow") == 0 || shots >= 10) {
				fail(shots >= 10 ? "missed 10 shots" : "out of arrows");
				return;
			}
			if (ticks < 3) return;
			LocalPlayer pl = Mc.player();
			// Full-draw arrows fly ~3 blocks/tick and drop ~d^2/360 blocks over distance d; aim above.
			Vec3 c = target.getBoundingBox().getCenter();
			double d = pl.getEyePosition().distanceTo(c);
			Mc.lookAt(c.add(0, d * d / 360.0, 0));
			if (drawTicks < 22) {
				Mc.mc().options.keyUse.setDown(true);
				drawTicks++;
			} else {
				Mc.mc().options.keyUse.setDown(false);
				shots++;
				drawTicks = -8; // short pause before the next draw
			}
		}
	}
}
