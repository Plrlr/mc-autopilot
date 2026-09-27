package io.github.plrlr.autopilot.skills;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Nether stage: find a fortress, then get blaze rods there.
 *
 * fortress find: walk long straight legs through the Nether and look around while walking.
 * Fortresses are big dark brick structures a player spots from far away, but WorldMemory's close
 * scan only reaches 16 blocks; here a sweep of view rays (what the camera could see, no x-ray)
 * remembers nether bricks and spawners out to 96 blocks.
 *
 * fortress blazes:n: blazes hover and shoot, so chasing them fails (attack gives up after 8 s
 * without getting closer). Instead stand at the spawner (or in the fortress), shield up while
 * they're at range, and hit the ones that drift into reach. Ends when the bag holds n rods.
 */
public final class NetherSkills {
	private NetherSkills() {}

	/** Blocks only fortresses are made of (bastions use blackstone, so they don't count). */
	static boolean fortressBlock(String id) {
		return id.equals("nether_bricks") || id.startsWith("nether_brick_");
	}

	/**
	 * View ray n of a sweep: yaw goes round in 7.5° steps (48 per turn), and each turn uses the
	 * next pitch band, from slightly up to well down, so ~8 ticks cover every direction once.
	 * Returns {yaw, pitch} in degrees (Minecraft: pitch > 0 looks down).
	 */
	static float[] sweepRay(int n) {
		float yaw = (n % 48) * 7.5f;
		float[] pitches = {-12f, 0f, 10f, 22f, 35f};
		float pitch = pitches[(n / 48) % pitches.length];
		return new float[]{yaw, pitch};
	}

	/** Casts `rays` view rays of the sweep from the eye; remembers fortress blocks and spawners hit. */
	static int look(WorldMemory memory, int from, int rays, double range) {
		LocalPlayer pl = Mc.player();
		Vec3 eye = pl.getEyePosition();
		int n = from;
		for (int i = 0; i < rays; i++, n++) {
			float[] yp = sweepRay(n);
			Vec3 dir = Vec3.directionFromRotation(yp[1], yp[0]);
			// Lava is opaque: a lava sea hides what's behind it, so fluids stop the ray too.
			BlockHitResult r = pl.level().clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.VISUAL,
					ClipContext.Fluid.ANY, pl));
			if (r.getType() != HitResult.Type.BLOCK) continue;
			BlockPos p = r.getBlockPos();
			String id = Mc.id(Mc.state(p).getBlock());
			if (fortressBlock(id)) memory.remember("nether_bricks", p.immutable(), id);
			else if (id.equals("spawner")) memory.remember("spawner", p.immutable(), id);
		}
		return n;
	}

	public static final class Fortress extends Skill {
		private static final int LEG = 200;
		private static final double VIEW = 96;

		private boolean findMode;
		private int want;
		private int ray;
		private double startX, startZ;

		// blazes mode. Tried anchors outlive one run of the skill: a reflex or an eat restarts it,
		// and a fresh list sent it back to the same empty corner (loop 0355).
		private BlockPos anchor;
		private static final List<BlockPos> visitedAnchors = new ArrayList<>();
		private int sinceBlaze;
		private int rodsBefore;
		private boolean walking;

		// Baritone settings changed while this skill runs, restored in cleanup.
		private Boolean savedItemSaver;
		private Double savedBreakPenalty, savedSpawnerAvoid;

		/**
		 * The first test walked a 200-block leg through solid netherrack near the roof and wore the
		 * iron pickaxe out; without it the bot dug by hand. Here: stop using a tool before it
		 * breaks, and make digging costly so open caves and corridors win over tunnels (in blazes
		 * mode Baritone dug 26 bricks out of the fortress and sank to y 41 under it, where no blaze
		 * can be seen; loop 0355). In blazes mode, Baritone's spawner avoidance (16 blocks) would
		 * keep us from the one place blazes come to, so it's switched off.
		 */
		private void tuneBaritone() {
			Settings st = BaritoneAPI.getSettings();
			savedItemSaver = st.itemSaver.value;
			st.itemSaver.value = true;
			savedBreakPenalty = st.blockBreakAdditionalPenalty.value;
			st.blockBreakAdditionalPenalty.value = 12.0;
			if (!findMode) {
				savedSpawnerAvoid = st.mobSpawnerAvoidanceCoefficient.value;
				st.mobSpawnerAvoidanceCoefficient.value = 1.0;
			}
		}

		private void restoreBaritone() {
			Settings st = BaritoneAPI.getSettings();
			if (savedItemSaver != null) st.itemSaver.value = savedItemSaver;
			if (savedBreakPenalty != null) st.blockBreakAdditionalPenalty.value = savedBreakPenalty;
			if (savedSpawnerAvoid != null) st.mobSpawnerAvoidanceCoefficient.value = savedSpawnerAvoid;
		}

		@Override
		public String name() {
			return "fortress";
		}

		/** A fight in a fortress shouldn't be dropped for a routine re-check; danger still interrupts. */
		@Override
		public boolean interruptible() {
			return findMode;
		}

		@Override
		protected void start() {
			if (!Mc.dimension().equals("the_nether")) {
				fail(Fail.WRONG_PLACE, "fortresses are in the Nether");
				return;
			}
			findMode = !"blazes".equals(argName());
			tuneBaritone();
			LocalPlayer pl = Mc.player();
			if (findMode) {
				timeoutTicks = 20 * 240;
				if (memory.nearest("nether_bricks") != null) {
					fail(Fail.ALREADY_DONE, "a fortress is already known");
					return;
				}
				visitedAnchors.clear();
				startX = pl.getX();
				startZ = pl.getZ();
				// Fortresses and bastions share 432-block regions: long legs reach new regions,
				// short ones keep circling the same one.
				int h = memory.exploreHeading(startX, startZ, LEG);
				double a = h * Math.PI / 4;
				Bari.path(new GoalXZ((int) (startX + Math.cos(a) * LEG), (int) (startZ + Math.sin(a) * LEG)));
				return;
			}
			timeoutTicks = 20 * 300;
			want = argCount(6);
			rodsBefore = Mc.count("blaze_rod");
			if (rodsBefore >= want) {
				fail(Fail.ALREADY_DONE, "already have " + rodsBefore + " blaze rods");
				return;
			}
			anchor = pickAnchor();
			if (anchor == null && Perception.look(32).nearest("blaze") == null) {
				fail(Fail.NOT_FOUND, "no fortress or blaze known");
				return;
			}
			if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
			CombatSkills.holdWeapon();
			if (anchor != null) walkTo(anchor);
		}

		/** The spawner if we know one (blazes appear right there), else the nearest fortress block not yet tried. */
		private BlockPos pickAnchor() {
			WorldMemory.Seen s = memory.nearest("spawner");
			if (s != null && !visitedAnchors.contains(s.pos())) return s.pos();
			LocalPlayer pl = Mc.player();
			BlockPos best = null;
			double bd = Double.MAX_VALUE;
			for (WorldMemory.Seen b : memory.all("nether_bricks")) {
				// Floors and bridge tops only: a brick in a wall or under the floor made Baritone
				// tunnel into the fortress, out of sight of every blaze.
				if (!Mc.free(b.pos().above()) || !Mc.free(b.pos().above(2))) continue;
				boolean tried = false;
				for (BlockPos v : visitedAnchors) if (v.distSqr(b.pos()) < 20 * 20) tried = true;
				if (tried) continue;
				double d = b.pos().distSqr(pl.blockPosition());
				if (d < bd) {
					bd = d;
					best = b.pos();
				}
			}
			return best;
		}

		private void walkTo(BlockPos p) {
			walking = true;
			Bari.path(new GoalNear(p, 3));
		}

		@Override
		protected void tick() {
			ray = look(memory, ray, 16, VIEW);
			if (findMode) tickFind();
			else tickBlazes();
		}

		private void tickFind() {
			if (ticks % 10 != 0) return;
			WorldMemory.Seen b = memory.nearest("nether_bricks");
			LocalPlayer pl = Mc.player();
			if (b != null) {
				done("saw a fortress " + Math.round(Math.sqrt(b.pos().distSqr(pl.blockPosition()))) + " blocks away");
				return;
			}
			if (ticks > 20 && !Bari.pathing()) {
				double moved = Math.hypot(pl.getX() - startX, pl.getZ() - startZ);
				if (moved < 16) {
					memory.markBadAhead(startX, startZ, LEG);
					fail(Fail.UNREACHABLE, "couldn't make headway that way (lava or cliffs); will turn");
				} else {
					done("walked " + Math.round(moved) + " blocks, no fortress in view yet");
				}
			}
		}

		private void tickBlazes() {
			LocalPlayer pl = Mc.player();
			int rods = Mc.count("blaze_rod");
			if (rods >= want) {
				done("have " + rods + " blaze rods (+" + (rods - rodsBefore) + ")");
				return;
			}
			// A better anchor came into view (the spawner itself): go there instead.
			WorldMemory.Seen spawner = memory.nearest("spawner");
			if (spawner != null && !spawner.pos().equals(anchor) && !visitedAnchors.contains(spawner.pos())) {
				anchor = spawner.pos();
				walkTo(anchor);
			}
			Perception seen = Perception.look(32);
			Perception.Seen blaze = seen.nearest("blaze");
			var keyUse = Mc.mc().options.keyUse;
			if (recover(pl, blaze, keyUse)) return;
			// Between blazes, keep hunger at 18+ so health comes back between hits: below 18 it
			// doesn't regenerate at all (loop 0341 fought at hunger 15 with 16 steaks in the bag).
			if (pl.getFoodData().getFoodLevel() < 18 && (blaze == null || blaze.dist() > 6) && eat(pl, keyUse)) return;
			stopEating(keyUse);
			// Wither skeletons and other fortress mobs walk up and hit us: fight them here. Leaving
			// them to the generic reflex restarted this skill every time (loop 0355).
			Perception.Seen close = seen.nearestHostile();
			if (close != null && close.dist() <= 3.2 && !close.type().equals("blaze")) {
				keyUse.setDown(false);
				if (Bari.pathing()) Bari.stop();
				walking = false;
				Mc.lookAt(close.entity().getBoundingBox().getCenter());
				if (pl.getAttackStrengthScale(0.5f) >= 0.95f) {
					Mc.mc().gameMode.attack(pl, close.entity());
					Mc.swing();
				}
				return;
			}
			if (blaze != null) {
				// Chase and hit. Waiting at the spawner with the shield up (the first design) only
				// blocked for 105 s: blazes keep their distance. The plain chase killed one every ~6 s.
				sinceBlaze = 0;
				keyUse.setDown(false);
				Entity e = blaze.entity();
				if (blaze.dist() <= 3.2) {
					if (Bari.pathing()) Bari.stop();
					walking = false;
					Mc.lookAt(e.getBoundingBox().getCenter());
					if (pl.getAttackStrengthScale(0.5f) >= 0.95f) {
						Mc.mc().gameMode.attack(pl, e);
						Mc.swing();
					}
					return;
				}
				// Rods first if one is lying close by; otherwise close in on the blaze.
				if (blaze.dist() > 6 || !rodNearby(pl, seen)) {
					if (ticks % 10 == 0) Bari.path(new GoalNear(e.blockPosition(), 2));
					walking = true;
					return;
				}
			} else {
				keyUse.setDown(false);
				sinceBlaze++;
			}
			// Rods on the ground nearby: pick them up before anything else.
			if (ticks % 10 == 0) {
				for (ItemEntity it : seen.items) {
					if (Items2.id(it.getItem()).equals("blaze_rod") && it.distanceTo(pl) < 12) {
						Bari.path(new GoalNear(it.blockPosition(), 0));
						walking = true;
						return;
					}
				}
			}
			if (walking && !Bari.pathing()) walking = false;
			// No blaze for 30 s here: try another part of the fortress (blazes spawn all over it).
			if (sinceBlaze > 20 * 30) {
				sinceBlaze = 0;
				if (anchor != null) visitedAnchors.add(anchor);
				visitedAnchors.add(pl.blockPosition().immutable());
				anchor = pickAnchor();
				if (anchor == null) {
					if (rods > rodsBefore) done("got " + (rods - rodsBefore) + " blaze rods; no more blazes found here");
					else fail(Fail.NOT_FOUND, "no blazes in the parts of the fortress we know");
					return;
				}
				walkTo(anchor);
			}
		}

		private static boolean rodNearby(LocalPlayer pl, Perception seen) {
			for (ItemEntity it : seen.items) if (Items2.id(it.getItem()).equals("blaze_rod") && it.distanceTo(pl) < 12) return true;
			return false;
		}

		private boolean recovering;
		private boolean eating;

		/**
		 * Low health (12, since burning keeps hurting after we stop): eat first, then get out of the
		 * blazes' sight, then fight again at 16. Eating comes first even in sight and on fire: at full
		 * hunger with saturation health comes back about 2 a second, faster than burning takes it
		 * (1 a second, and armor doesn't stop it). Running for cover without eating burned the bot
		 * to death in a room with no cover (loop 0341, hp 5, hunger 15, 16 steaks carried).
		 * Returns true while recovering.
		 */
		private boolean recover(LocalPlayer pl, Perception.Seen blaze, net.minecraft.client.KeyMapping keyUse) {
			float hp = pl.getHealth();
			if (!recovering && hp > 12) return false;
			if (recovering && hp >= 16) {
				recovering = false;
				stopEating(keyUse);
				return false;
			}
			recovering = true;
			if (pl.getFoodData().getFoodLevel() < 20 && eat(pl, keyUse)) return true;
			stopEating(keyUse);
			boolean inSight = blaze != null && Mc.canSee(blaze.entity());
			if (inSight || pl.isOnFire()) {
				// Run from the nearest blaze (or just move, if burning) until it can't see us.
				BlockPos from = blaze != null ? blaze.entity().blockPosition() : pl.blockPosition();
				if (ticks % 10 == 0) Bari.path(new baritone.api.pathing.goals.GoalRunAway(14, from));
				return true;
			}
			// Out of sight and full: wait for health to come back.
			if (Bari.pathing()) Bari.stop();
			return true;
		}

		/** Holds food and keeps eating; false with no food in the bag. Standing still: eating while walking crawls anyway. */
		private boolean eat(LocalPlayer pl, net.minecraft.client.KeyMapping keyUse) {
			if (Mc.count(Items2.matcher("food")) == 0) return false;
			if (Bari.pathing()) Bari.stop();
			walking = false;
			if (!Items2.matcher("food").test(pl.getMainHandItem())) Mc.holdItem(Items2.matcher("food"));
			keyUse.setDown(true);
			eating = true;
			return true;
		}

		private void stopEating(net.minecraft.client.KeyMapping keyUse) {
			if (!eating) return;
			eating = false;
			keyUse.setDown(false);
			CombatSkills.holdWeapon();
		}

		@Override
		protected void cleanup() {
			Mc.mc().options.keyUse.setDown(false);
			restoreBaritone();
			super.cleanup();
		}
	}
}
