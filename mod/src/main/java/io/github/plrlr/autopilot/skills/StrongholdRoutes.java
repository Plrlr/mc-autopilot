package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

/**
 * Wave 4 of docs/skills-40.md: from eyes of ender to the portal room (0 of 30 eye starts found it).
 *   bow_kit             string, feathers, flint: a bow and arrows for the End's crystals
 *   eye_triangulate     locate_stronghold (with bearings 5+ degrees apart) until the point is known
 *   dig_to_stronghold   safe stairs down at the point until stronghold bricks show
 *   stronghold_navigate the corridor search, round after round, until the portal room shows
 *   silverfish_control  break the portal room's spawner
 *   boat_cross          cross water by boat (drowned, tridents and slow swims killed runs)
 */
public final class StrongholdRoutes {
	private StrongholdRoutes() {}

	// ------------------------------------------------------------------ 30 bow_kit

	/** bow_kit[:n]: a bow and n arrows, from spiders (string), chickens (feathers) and gravel (flint). */
	public static final class BowKit extends Composite {
		private int want, explores;
		private boolean pickupNext;

		@Override
		public String name() {
			return "bow_kit";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 8;
			maxChildFails = 6;
			want = argCount(24);
			if (!Mc.dimension().equals("overworld")) fail(Fail.WRONG_PLACE, "string and feathers are overworld things");
		}

		private Option hunt(String mob, String why) {
			if (Perception.look(32).nearest(mob) != null) {
				pickupNext = true;
				return new Option("attack", mob, why);
			}
			if (explores++ >= 3) {
				fail(Fail.NOT_FOUND, "no " + mob + " found for " + why);
				return null;
			}
			return new Option("explore", mob, "look for a " + mob + " (" + why + ")");
		}

		@Override
		protected Option next() {
			if (pickupNext) {
				pickupNext = false;
				return new Option("pickup", null, "pick up the drops");
			}
			if (Mc.count("stick") < 4 && Mc.count(Items2.matcher("planks")) >= 2) return new Option("craft", "stick:4", "sticks");
			if (Mc.count("bow") == 0) {
				if (Mc.count("string") >= 3 && Mc.count("stick") >= 3) return new Option("craft", "bow:1", "a bow for the crystals");
				return hunt("spider", "string for a bow");
			}
			int arrows = Mc.count("arrow");
			if (arrows >= want) return null;
			if (Mc.count("feather") > 0 && Mc.count("flint") > 0 && Mc.count("stick") > 0)
				return new Option("craft", "arrow:" + Math.min(want, arrows + 4 * Math.min(Mc.count("feather"), Mc.count("flint"))), "arrows");
			if (Mc.count("flint") == 0) return new Option("collect", "flint:" + Math.max(1, (want - arrows) / 4), "flint for arrows");
			return hunt("chicken", "feathers for arrows");
		}

		@Override
		protected void finish() {
			if (Mc.count("bow") > 0 && Mc.count("arrow") >= want) done("bow and " + Mc.count("arrow") + " arrows");
			else fail(Fail.NO_PROGRESS, "bow " + Mc.count("bow") + ", arrows " + Mc.count("arrow"));
		}
	}

	// ------------------------------------------------------------------ 31 eye_triangulate

	/** eye_triangulate: locate_stronghold runs (its bearings must cross at 5+ degrees with the gene) until the point is known. */
	public static final class EyeTriangulate extends Composite {
		private int runs;

		@Override
		public String name() {
			return "eye_triangulate";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 15;
			maxChildFails = 2;
			if (Mc.count("ender_eye") < 2) fail(Fail.NEED_ITEM, "triangulating takes 2+ eyes");
		}

		private boolean located() {
			return memory.nearest("end_portal_frame") != null || SearchStronghold.inStronghold()
					|| PortalSkills.LocateStronghold.knownPoint() != null && Act.flatDist(BlockPos.containing(PortalSkills.LocateStronghold.knownPoint())) < 24;
		}

		@Override
		protected Option next() {
			if (located() || runs++ >= 3 || Mc.count("ender_eye") == 0) return null;
			return new Option("locate_stronghold", null, "throw, sidestep, throw: cross the bearings");
		}

		@Override
		protected void finish() {
			if (located()) {
				Facts.report("stronghold_known");
				done("the stronghold is here");
			} else fail(Fail.NOT_FOUND, "bearings never crossed close by");
		}
	}

	// ------------------------------------------------------------------ 32 dig_to_stronghold

	/** dig_to_stronghold: at the point, stairs down 8 levels at a time, looking for stronghold bricks between. */
	public static final class DigToStronghold extends Composite {
		private Vec3 point;
		private boolean walked;

		@Override
		public String name() {
			return "dig_to_stronghold";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 6;
			maxChildFails = 3;
			point = PortalSkills.LocateStronghold.knownPoint();
			if (point == null) point = Mc.player().position();
		}

		/** Stronghold bricks in sight within 6 blocks. */
		private static BlockPos bricksInSight() {
			BlockPos feet = Mc.player().blockPosition();
			for (int dx = -6; dx <= 6; dx++)
				for (int dy = -4; dy <= 3; dy++)
					for (int dz = -6; dz <= 6; dz++) {
						BlockPos p = feet.offset(dx, dy, dz);
						String id = Mc.id(Mc.state(p).getBlock());
						if ((id.equals("stone_bricks") || id.equals("mossy_stone_bricks") || id.equals("cracked_stone_bricks")) && Mc.canSee(p))
							return p;
					}
			return null;
		}

		@Override
		protected boolean ownTick() {
			if (walked) return false;
			if (Math.hypot(point.x - Mc.player().getX(), point.z - Mc.player().getZ()) < 4) {
				Bari.stop();
				walked = true;
				return false;
			}
			if (!Bari.pathing() && ticks % 40 == 1) Bari.path(new GoalXZ((int) point.x, (int) point.z));
			if (ticks > 20 * 120) walked = true; // as close as the way allows
			return true;
		}

		@Override
		protected Option next() {
			if (SearchStronghold.inStronghold()) return null;
			BlockPos b = bricksInSight();
			if (b != null) {
				Bari.path(new GoalNear(b, 1));
				return new Option("search_stronghold", null, "stronghold bricks in sight: go in");
			}
			int y = Mc.player().getBlockY();
			if (y <= 8) return null;
			return new Option("stair_down", String.valueOf(Math.max(8, y - 8)), "8 levels down by safe stairs, then look");
		}

		@Override
		protected void finish() {
			if (SearchStronghold.inStronghold() || bricksInSight() != null) {
				Facts.report("in_stronghold");
				done("in the stronghold");
			} else fail(Fail.NOT_FOUND, "dug down to y " + Mc.player().getBlockY() + " without finding it");
		}
	}

	// ------------------------------------------------------------------ 33 stronghold_navigate

	/** stronghold_navigate: search_stronghold rounds until the portal room (its frames) is seen. */
	public static final class Navigate extends Composite {
		private int rounds;

		@Override
		public String name() {
			return "stronghold_navigate";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 10;
			maxChildFails = 3;
		}

		@Override
		protected Option next() {
			if (memory.nearest("end_portal_frame") != null || rounds++ >= 4) return null;
			return new Option("search_stronghold", null, "corridors in sight until the portal room shows");
		}

		@Override
		protected void finish() {
			if (memory.nearest("end_portal_frame") != null) {
				Facts.report("frame_known");
				done("portal room found");
			} else fail(Fail.NOT_FOUND, "no portal room after 4 rounds");
		}
	}

	// ------------------------------------------------------------------ 34 silverfish_control

	/** silverfish_control: break the spawner by the portal frames; hit silverfish that reach us meanwhile. */
	public static final class Silverfish extends Skill {
		private final Act.Breaker breaker = new Act.Breaker();
		private BlockPos spawner;

		@Override
		public String name() {
			return "silverfish_control";
		}

		@Override
		public boolean ownsSafety() {
			return true;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60;
			WorldMemory.Seen f = memory.nearest("end_portal_frame");
			for (WorldMemory.Seen s : memory.all("spawner"))
				if (f == null || s.pos().distSqr(f.pos()) < 16 * 16) spawner = s.pos();
			if (spawner == null) {
				Facts.report("room_safe");
				done("no spawner in the portal room");
			}
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			Perception.Seen fish = Perception.look(4).nearest("silverfish");
			if (fish != null && Act.strike(fish.entity())) return;
			if (Mc.free(spawner)) {
				breaker.stop();
				memory.forget("spawner", spawner);
				Facts.report("room_safe");
				done("broke the silverfish spawner");
				return;
			}
			if (pl.getEyePosition().distanceTo(Vec3.atCenterOf(spawner)) > Mc.reach() - 0.5) {
				if (!Bari.pathing()) Bari.path(new GoalNear(spawner, 2));
				return;
			}
			Bari.stop();
			breaker.tick(spawner);
		}

		@Override
		protected void cleanup() {
			breaker.stop();
			super.cleanup();
		}
	}

	// ------------------------------------------------------------------ 35 boat_cross

	/**
	 * boat_cross [x z]: water between us and where we're going (default: the stronghold point).
	 * Put the boat on the water, get in, steer toward the point (left/right turn the boat, forward
	 * rows), get out at the far bank, break the boat and pick it up. 3x faster than swimming and
	 * drowned can't reach us.
	 */
	public static final class BoatCross extends Skill {
		private enum Phase {PLACE, MOUNT, RIDE, LAND, RECOVER}

		private Phase phase = Phase.PLACE;
		private Vec3 target;
		private AbstractBoat boat;
		private int phaseTicks, still;

		@Override
		public String name() {
			return "boat_cross";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 150;
			if (arg != null && arg.trim().split("\\s+").length == 2) {
				String[] a = arg.trim().split("\\s+");
				target = new Vec3(Integer.parseInt(a[0]), 0, Integer.parseInt(a[1]));
			} else target = PortalSkills.LocateStronghold.knownPoint();
			if (target == null) {
				fail(Fail.NOT_FOUND, "nowhere to cross to");
				return;
			}
			if (Mc.count(Items2.matcher("boat")) == 0 && !(Mc.player().getVehicle() instanceof AbstractBoat)) {
				fail(Fail.NEED_ITEM, "no boat");
				return;
			}
			Bari.stop();
			if (Mc.player().getVehicle() instanceof AbstractBoat b) {
				boat = b;
				phase = Phase.RIDE;
			}
		}

		private void go(Phase p) {
			phase = p;
			phaseTicks = 0;
		}

		private static AbstractBoat boatNear(double r) {
			for (Entity e : Mc.mc().level.entitiesForRendering())
				if (e instanceof AbstractBoat b && b.isAlive() && b.distanceTo(Mc.player()) < r) return b;
			return null;
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			var o = Mc.mc().options;
			phaseTicks++;
			switch (phase) {
				case PLACE -> {
					boat = boatNear(4);
					if (boat != null) {
						go(Phase.MOUNT);
						return;
					}
					BlockPos water = null;
					BlockPos feet = pl.blockPosition();
					for (int dx = -3; dx <= 3 && water == null; dx++)
						for (int dz = -3; dz <= 3 && water == null; dz++)
							for (int dy = -2; dy <= 0; dy++) {
								BlockPos p = feet.offset(dx, dy, dz);
								if (Mc.state(p).getFluidState().is(net.minecraft.tags.FluidTags.WATER) && Mc.free(p.above()) && Mc.canSee(p)) {
									water = p;
									break;
								}
							}
					if (water == null) {
						fail(Fail.NOT_FOUND, "no open water beside us");
						return;
					}
					Mc.holdItem(Items2.matcher("boat"));
					Mc.lookAt(Vec3.atCenterOf(water).add(0, 0.45, 0));
					if (phaseTicks % 10 == 5) Mc.mc().gameMode.useItem(pl, InteractionHand.MAIN_HAND);
					if (phaseTicks > 60) fail(Fail.PLACE_FAILED, "the boat wouldn't go on the water");
				}
				case MOUNT -> {
					if (pl.getVehicle() instanceof AbstractBoat) {
						go(Phase.RIDE);
						return;
					}
					if (pl.distanceTo(boat) > 3) {
						if (!Bari.pathing()) Bari.path(new GoalNear(boat.blockPosition(), 1));
					} else if (phaseTicks % 10 == 1) Mc.mc().gameMode.interact(pl, boat, new net.minecraft.world.phys.EntityHitResult(boat), InteractionHand.MAIN_HAND);
					if (phaseTicks > 20 * 10) fail(Fail.USE_FAILED, "couldn't get into the boat");
				}
				case RIDE -> {
					if (!(pl.getVehicle() instanceof AbstractBoat b)) {
						go(Phase.RECOVER);
						return;
					}
					double want = Math.toDegrees(Math.atan2(-(target.x - b.getX()), target.z - b.getZ()));
					double diff = ((want - b.getYRot()) % 360 + 540) % 360 - 180;
					o.keyLeft.setDown(diff < -8);
					o.keyRight.setDown(diff > 8);
					o.keyUp.setDown(Math.abs(diff) < 60);
					boolean arrived = Math.hypot(target.x - b.getX(), target.z - b.getZ()) < 6;
					still = b.getDeltaMovement().horizontalDistance() < 0.02 && phaseTicks > 40 ? still + 1 : 0;
					if (arrived || still > 30) go(Phase.LAND);
				}
				case LAND -> {
					o.keyUp.setDown(false);
					o.keyLeft.setDown(false);
					o.keyRight.setDown(false);
					o.keyShift.setDown(phaseTicks < 5);
					if (!(pl.getVehicle() instanceof AbstractBoat)) go(Phase.RECOVER);
					else if (phaseTicks > 40) fail(Fail.STUCK, "couldn't get out of the boat");
				}
				case RECOVER -> {
					o.keyShift.setDown(false);
					AbstractBoat b = boatNear(5);
					if (b != null) {
						Mc.lookAt(b.getBoundingBox().getCenter());
						if (phaseTicks % 5 == 0) {
							Mc.mc().gameMode.attack(pl, b);
							Mc.swing();
						}
						if (phaseTicks < 20 * 8) return;
					}
					for (Entity e : Mc.mc().level.entitiesForRendering())
						if (e instanceof ItemEntity it && Items2.matcher("boat").test(it.getItem()) && it.distanceTo(pl) < 6) {
							if (phaseTicks % 10 == 1) Bari.path(new baritone.api.pathing.goals.GoalBlock(it.blockPosition()));
							if (phaseTicks < 20 * 12) return;
						}
					Facts.report("crossed");
					done("crossed by boat" + (Mc.count(Items2.matcher("boat")) > 0 ? ", boat back" : ""));
				}
			}
		}

		@Override
		protected void cleanup() {
			Mc.mc().options.keyShift.setDown(false);
			super.cleanup();
		}
	}
}
