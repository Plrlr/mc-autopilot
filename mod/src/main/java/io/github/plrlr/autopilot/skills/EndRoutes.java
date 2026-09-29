package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.Perception;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.phys.Vec3;

/**
 * Wave 5 of docs/skills-40.md: the End (0 of 18 End starts killed the dragon).
 *   end_landing    off the obsidian platform onto the main island, eyes on the ground
 *   crystal_hunt   every crystal down: shoot the open ones, climb to the caged ones and break the bars
 *   bed_bomb       beds explode in the End: blow them up at the perched dragon's head
 *   dragon_strike  arrows while it circles, the head fight (DragonFight) while it perches
 *   end_guard      the End's reflex: never meet an enderman's eyes, never fall into the void
 */
public final class EndRoutes {
	private EndRoutes() {}

	static EnderDragon dragon() {
		for (Entity e : Mc.mc().level.entitiesForRendering()) if (e instanceof EnderDragon d && d.isAlive()) return d;
		return null;
	}

	static boolean perched(EnderDragon d) {
		if (d == null) return false;
		var phase = d.getPhaseManager().getCurrentPhase();
		return phase.isSitting() || phase.getPhase() == EnderDragonPhase.LANDING;
	}

	/** For the planner: standing on end stone (not the obsidian spawn platform). */
	public static boolean onIslandNow() {
		return onIsland();
	}

	static boolean onIsland() {
		String under = Mc.id(Mc.state(Mc.player().blockPosition().below()).getBlock());
		return under.equals("end_stone");
	}

	// ------------------------------------------------------------------ 36 end_landing

	/** end_landing: from the spawn platform, walk and bridge toward the fountain until we stand on end stone. */
	public static final class EndLanding extends Composite {
		private int tries;

		@Override
		public String name() {
			return "end_landing";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 120;
			maxChildFails = 3;
			if (!Mc.dimension().equals("the_end")) fail(Fail.WRONG_PLACE, "not in the End");
		}

		@Override
		protected Option next() {
			Mc.player().setXRot(55f); // eyes on the ground: no enderman gets looked at on the way
			if (onIsland() || tries++ >= 3) return null;
			return new Option("nether_bridge", "0 0", "bridge toward the fountain");
		}

		@Override
		protected void finish() {
			if (onIsland()) {
				Facts.report("on_island");
				done("on the main island");
			} else fail(Fail.UNREACHABLE, "couldn't get onto the island");
		}
	}

	// ------------------------------------------------------------------ 37 crystal_hunt

	/**
	 * crystal_hunt: shoot every crystal in sight; a crystal we can't hit (iron bars around it) gets a
	 * climb: walk to its pillar, tower up beside it to 3 below its height, break the bars in reach,
	 * come back down 4+ blocks away (a crystal's blast is 6) and shoot it.
	 */
	public static final class CrystalHunt extends Composite {
		private final Act.Breaker breaker = new Act.Breaker();
		private EndCrystal caged;
		private int climbTicks, shots;
		private boolean climbing;

		@Override
		public String name() {
			return "crystal_hunt";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 8;
			maxChildFails = 8;
			if (!Mc.dimension().equals("the_end")) fail(Fail.WRONG_PLACE, "crystals are in the End");
			else if (Mc.count("bow") == 0 || Mc.count("arrow") == 0) fail(Fail.NEED_ITEM, "crystals need a bow and arrows");
		}

		private static EndCrystal nearestCrystal() {
			EndCrystal best = null;
			double bd = Double.MAX_VALUE;
			for (Entity e : Mc.mc().level.entitiesForRendering())
				if (e instanceof EndCrystal c && c.isAlive() && c.distanceTo(Mc.player()) < bd) {
					bd = c.distanceTo(Mc.player());
					best = c;
				}
			return best;
		}

		static boolean hasBars(EndCrystal c) {
			BlockPos p = c.blockPosition();
			for (Direction d : Direction.Plane.HORIZONTAL)
				if (Mc.id(Mc.state(p.relative(d)).getBlock()).equals("iron_bars") || Mc.id(Mc.state(p.above().relative(d)).getBlock()).equals("iron_bars"))
					return true;
			return false;
		}

		@Override
		protected boolean ownTick() {
			if (!climbing) return false;
			LocalPlayer pl = Mc.player();
			climbTicks++;
			if (!caged.isAlive() || !hasBars(caged) || climbTicks > 20 * 90) {
				climbing = false;
				breaker.stop();
				Mc.mc().options.keyJump.setDown(false);
				return false;
			}
			BlockPos base = caged.blockPosition();
			if (Act.flatDist(base) > 3) {
				if (!Bari.pathing()) Bari.path(new GoalNear(new BlockPos(base.getX(), pl.getBlockY(), base.getZ()), 2));
				return true;
			}
			Bari.stop();
			if (pl.getY() < caged.getY() - 3) {
				// Tower: jump, place under at the top of the jump.
				Mc.mc().options.keyJump.setDown(true);
				BlockPos feet = pl.blockPosition();
				if (!pl.onGround() && Mc.free(feet.below()) && climbTicks % 3 == 0) {
					pl.setXRot(90f);
					Act.place(feet.below());
				}
				return true;
			}
			Mc.mc().options.keyJump.setDown(false);
			BlockPos p = caged.blockPosition();
			for (BlockPos b : new BlockPos[]{p.north(), p.south(), p.east(), p.west(), p.above().north(), p.above().south(), p.above().east(), p.above().west()})
				if (Mc.id(Mc.state(b).getBlock()).equals("iron_bars") && pl.getEyePosition().distanceTo(Vec3.atCenterOf(b)) < Mc.reach() - 0.3) {
					breaker.tick(b);
					return true;
				}
			return true;
		}

		@Override
		protected Option next() {
			EndCrystal c = nearestCrystal();
			if (c == null || Mc.count("arrow") == 0 || shots++ > 30) return null;
			if (hasBars(c) && !climbing) {
				caged = c;
				climbing = true;
				climbTicks = 0;
				return WAIT;
			}
			return new Option("shoot", "end_crystal", "shoot the crystal off its pillar");
		}

		@Override
		protected void finish() {
			breaker.stop();
			if (nearestCrystal() == null) {
				Facts.report("crystals_down");
				done("no crystals left in sight");
			} else fail(Mc.count("arrow") == 0 ? Fail.NEED_ITEM : Fail.NO_PROGRESS, "crystals still standing");
		}

		@Override
		protected void cleanup() {
			breaker.stop();
			Mc.mc().options.keyJump.setDown(false);
			super.cleanup();
		}
	}

	// ------------------------------------------------------------------ 38 bed_bomb

	/**
	 * bed_bomb: beds can't be slept in outside the overworld; in the End using one blows it up
	 * (vanilla). When the dragon perches, stand 3-4 blocks from its head, place a bed on the ground
	 * under the head, use it. Each blast hits the head hard. Health 14 or more between bombs.
	 */
	public static final class BedBomb extends Skill {
		private int bombs, wait;
		private BlockPos bedAt;

		@Override
		public String name() {
			return "bed_bomb";
		}

		@Override
		public boolean ownsSafety() {
			return true;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 10;
			if (!Mc.dimension().equals("the_end")) fail(Fail.WRONG_PLACE, "beds only explode outside the overworld");
			else if (Mc.count(Items2.matcher("bed")) == 0) fail(Fail.NEED_ITEM, "no beds");
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			EnderDragon d = dragon();
			if (d == null) {
				Facts.report("dragon_dead");
				done("the dragon is gone after " + bombs + " bed blasts");
				return;
			}
			if (Mc.count(Items2.matcher("bed")) == 0 && bedAt == null) {
				fail(Fail.NEED_ITEM, "out of beds after " + bombs + " blasts (the dragon lives)");
				return;
			}
			if (pl.getHealth() < 14 && Act.eatTick()) return;
			Mc.mc().options.keyUse.setDown(false);
			if (bedAt != null) {
				// Placed last tick: use it now.
				if (Mc.id(Mc.state(bedAt).getBlock()).endsWith("_bed")) {
					Mc.useOn(bedAt, Direction.UP);
					bombs++;
				}
				bedAt = null;
				wait = 20;
				return;
			}
			if (wait > 0) {
				wait--;
				return;
			}
			if (!perched(d)) {
				// Wait where the fountain is in reach, away from the edge.
				if (Act.flatDist(BlockPos.ZERO) > 12 && !Bari.pathing()) Bari.path(new GoalNear(new BlockPos(0, pl.getBlockY(), 6), 3));
				return;
			}
			Entity head = d.head;
			double dist = pl.distanceTo(head);
			if (dist > 5) {
				if (!Bari.pathing() || ticks % 20 == 0) Bari.path(new GoalNear(BlockPos.containing(head.position()), 3));
				return;
			}
			Bari.stop();
			// The bed goes on the ground under the head (the first solid block below it).
			BlockPos p = BlockPos.containing(head.position());
			for (int i = 0; i < 6 && Mc.free(p.below()); i++) p = p.below();
			if (!Mc.free(p) || pl.getEyePosition().distanceTo(Vec3.atCenterOf(p)) > Mc.reach() - 0.3) return;
			if (!Mc.holdItem(Items2.matcher("bed"))) return;
			if (Mc.useOn(p.below(), Direction.UP)) bedAt = p;
		}
	}

	// ------------------------------------------------------------------ 39 dragon_strike

	/** dragon_strike: arrows at the dragon while it circles in range, the head fight (DragonFight) while it perches. */
	public static final class DragonStrike extends Composite {
		private int shots;

		@Override
		public String name() {
			return "dragon_strike";
		}

		@Override
		public boolean ownsSafety() {
			return true;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 20;
			maxChildFails = 20;
			if (!Mc.dimension().equals("the_end")) fail(Fail.WRONG_PLACE, "the dragon is in the End");
		}

		@Override
		protected Option next() {
			EnderDragon d = dragon();
			if (d == null) return null;
			boolean bow = Mc.count("bow") > 0 && Mc.count("arrow") > 0;
			if (!perched(d) && bow && d.distanceTo(Mc.player()) < 48 && shots++ < 200)
				return new Option("shoot", "ender_dragon", "arrows while it circles");
			return new Option("dragon", null, "the head fight while it perches");
		}

		@Override
		protected void finish() {
			if (dragon() == null) {
				Facts.report("dragon_dead");
				done("the dragon is dead");
			} else fail(Fail.NO_PROGRESS, "the dragon lives");
		}
	}

	// ------------------------------------------------------------------ 40 end_guard

	/**
	 * end_guard: the End's reflex (Autopilot calls tick() every tick there with the gene on).
	 * Looking an enderman in the eyes angers it, and the End is full of them: when our view comes
	 * within 12 degrees of one's eyes within 32 blocks, look down. Falling with no ground below
	 * within 12 blocks: a block under our feet if we can, else a pearl thrown at the fountain.
	 */
	public static final class EndGuard extends Skill {
		private static long lastPearlMs;

		@Override
		public String name() {
			return "end_guard";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 30;
		}

		@Override
		protected void tick() {
			if (!guard()) done("nothing to guard against");
		}

		/** One tick of the guard. True if it acted. */
		public static boolean guard() {
			LocalPlayer pl = Mc.player();
			if (pl == null || !Mc.dimension().equals("the_end")) return false;
			// (b) The void: falling with nothing under us.
			if (!pl.onGround() && pl.getDeltaMovement().y < -0.3 && Clutch.groundBelow(pl) < 0) {
				BlockPos under = pl.blockPosition().below();
				if (Act.place(under)) return true;
				if (Mc.count("ender_pearl") > 0 && System.currentTimeMillis() - lastPearlMs > 2000) {
					Mc.holdItem(s -> Items2.id(s).equals("ender_pearl"));
					Mc.lookAt(new Vec3(0, 70, 0));
					Mc.useItem();
					lastPearlMs = System.currentTimeMillis();
					return true;
				}
				return false;
			}
			// (a) Enderman eyes: look away (down) when the view nears them.
			Vec3 eye = pl.getEyePosition(), look = pl.getViewVector(1f);
			for (Entity e : Mc.mc().level.entitiesForRendering()) {
				if (!(e instanceof Enderman em) || em.distanceTo(pl) > 32) continue;
				Vec3 to = em.getEyePosition().subtract(eye).normalize();
				if (look.dot(to) > Math.cos(Math.toRadians(12))) {
					pl.setXRot(Math.min(90f, pl.getXRot() + 35f));
					return true;
				}
			}
			return false;
		}
	}

	/** For the planner: crystals standing in sight (the plain route shoots only the open ones). */
	public static boolean crystalsInSight() {
		return Perception.look(128).nearest("end_crystal") != null;
	}
}
