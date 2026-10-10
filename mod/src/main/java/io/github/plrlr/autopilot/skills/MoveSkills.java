package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalXZ;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.state.Danger;
import io.github.plrlr.autopilot.state.DangerSense;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.List;

/** Skills that are mostly "walk somewhere with Baritone". */
public final class MoveSkills {
	private MoveSkills() {}

	/**
	 * explore <what to look for>: walk up to ~120 blocks into new ground and stop as soon as the
	 * thing we need comes into view. The argument is a comma list of mob types or remembered
	 * block groups ("cow,pig", "log", "nether_bricks,blaze"), or "any". The direction comes from
	 * WorldMemory: keep heading the same way while the ground is new, turn away from water.
	 */
	public static final class Explore extends Skill {
		private static final int DIST = 120;
		private List<String> targets = List.of();
		private int waterTicks;
		private double startX, startZ;
		/** Already swimming when the leg began: turning back only circles the same sea, so cross it. */
		private boolean startedInWater;

		@Override
		public String name() {
			return "explore";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 100;
			LocalPlayer pl = Mc.player();
			String a = arg == null ? "any" : arg;
			if (!a.equals("any")) targets = List.of(a.split(","));
			String already = found();
			if (already != null) {
				// Nothing to explore for: whatever needed this should use what's in view instead.
				fail(Fail.ALREADY_DONE, "already see " + already);
				return;
			}
			startX = pl.getX();
			startZ = pl.getZ();
			startedInWater = pl.isInWater();
			int h = memory.exploreHeading(startX, startZ, DIST);
			double angle = h * Math.PI / 4;
			Bari.path(new GoalXZ((int) (startX + Math.cos(angle) * DIST), (int) (startZ + Math.sin(angle) * DIST)));
		}

		/** The first target that's in view now, or null. */
		private String found() {
			if (targets.isEmpty()) return null;
			// Same range the planner looks in: if explore "sees" a cow the planner can't, they'd
			// hand the job back and forth forever.
			Perception seen = Perception.look(32);
			LocalPlayer pl = Mc.player();
			for (String t : targets) {
				if (seen.nearest(t) != null) return t;
				// Not the ones collect just failed to reach, or the two keep handing the job back.
				boolean remembered = false;
				for (WorldMemory.Seen b : memory.all(t)) {
					if (b.dim().equals(Mc.dimension()) && b.pos().distSqr(pl.blockPosition()) < 48 * 48 && !SeenMiner.unreachable(b.pos())) {
						remembered = true;
						break;
					}
				}
				// Gene explore.same_blocks: for what collect takes only from seen blocks (logs, sand), the
				// same test collect uses. Logs under deep water (a shipwreck's) were "already seen" here
				// and "none seen" there: collect:log NOT_FOUND then explore:log ALREADY_DONE, 1,194 and
				// 957 times in gens 118-177, while no tree was found.
				if (remembered && (t.equals("log") || t.equals("sand")) && !SeenMiner.anySeen(memory, t, 48)
						&& io.github.plrlr.autopilot.Exposure.mark("explore.same_blocks", "remembered " + t + " that collect can't use")) continue;
				if (remembered) return t;
			}
			return null;
		}

		@Override
		protected void tick() {
			if (ticks % 10 != 0) return;
			LocalPlayer pl = Mc.player();
			String f = found();
			if (f != null) {
				done("found " + f);
				return;
			}
			// Swimming across an ocean finds nothing we need and invites drowned; turn around.
			if (startedInWater) {
				// Crossing from open water: dry footing is what shore needed; hand back to it.
				if (ticks > 40 && !pl.isInWater() && pl.onGround()) {
					Bari.stop();
					done("reached dry ground after crossing water");
					return;
				}
				waterTicks = 0;
			} else if (pl.isInWater()) waterTicks += 10;
			else waterTicks = Math.max(0, waterTicks - 5);
			// Rivers take a few seconds to cross; only a long swim means open sea.
			if (waterTicks > 20 * 20) {
				memory.markBadAhead(startX, startZ, DIST);
				if (io.github.plrlr.autopilot.Tune.on("move.shore_first")) done("open water ahead; find shore");
				else fail(Fail.HAZARD, "open water ahead; will turn");
				return;
			}
			if (ticks > 20 && !Bari.pathing()) {
				double moved = Math.hypot(pl.getX() - startX, pl.getZ() - startZ);
				if (moved < 16) {
					memory.markBadAhead(startX, startZ, DIST);
					fail(Fail.UNREACHABLE, "couldn't make headway that way; will turn");
				} else {
					done("explored " + Math.round(moved) + " blocks" + (targets.isEmpty() ? "" : ", no " + String.join("/", targets) + " yet"));
				}
			}
		}
	}

	/** goto <known block group> | end_center: walk to a remembered place. */
	public static final class Goto extends Skill {
		/** goto surface v2: the staircase fallback, and the best height reached so far. */
		private StairUp climb;
		private int bestY = Integer.MIN_VALUE, sinceHigher;

		/**
		 * Gene nav.surface_v2. Baritone's search gave up within a second in 18 of 35 failed trips up
		 * (gens 57-58: "couldn't find the way up"), often boxed in by our own shelter. Give it 3 s to
		 * start and judge it by height gained: no new height in 15 s, or no path, and we dig a
		 * staircase up toward where we came down (StairUp), which needs no search and no blocks.
		 */
		private void surfaceV2(LocalPlayer pl) {
			if (climb != null) {
				climb.update();
				if (climb.result() == null) return;
				if (climb.result().ok()) done("climbed to open sky by stairs");
				else fail(Fail.UNREACHABLE, "couldn't find the way up (stairs: " + climb.result().detail() + ")");
				return;
			}
			if (pl.getBlockY() > bestY) {
				bestY = pl.getBlockY();
				sinceHigher = 0;
			} else sinceHigher++;
			boolean noPath = ticks > 60 && !Bari.pathing();
			if (noPath || sinceHigher > 20 * 15) {
				Bari.stop();
				BlockPos entry = memory.surfaceEntry();
				climb = new StairUp();
				climb.begin(memory, "sky" + (entry == null ? "" : " toward " + entry.getX() + " " + entry.getZ()));
			}
		}
		/** When goto surface last failed to climb (ms). */
		private static long surfaceFailedAt;

		/**
		 * True for two minutes after goto surface failed: without a pickaxe or blocks Baritone
		 * can't climb, and offering it again looped STUCK for 12 minutes (batch 11, seed d).
		 */
		public static boolean surfaceBlocked() {
			return System.currentTimeMillis() - surfaceFailedAt < 120_000;
		}

		@Override
		public String name() {
			return "goto";
		}

		@Override
		protected void cleanup() {
			if (climb != null && climb.result() == null) climb.abort(Fail.INTERRUPTED, "goto ended");
			super.cleanup();
			Result r = result();
			if ("surface".equals(arg) && r != null && !r.ok() && r.code() != Fail.INTERRUPTED && r.code() != Fail.DIED)
				surfaceFailedAt = System.currentTimeMillis();
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 120;
			if ("surface".equals(arg)) {
				// Room for the staircase after Baritone's try (StairUp's own limit is 90 s).
				if (io.github.plrlr.autopilot.Tune.on("nav.surface_v2")) timeoutTicks = 20 * 360;
				// Out of a cave the way we came in; with no known entry, dig up toward the sky.
				LocalPlayer pl = Mc.player();
				if (pl.level().canSeeSky(pl.blockPosition().above())) {
					done("already under open sky");
					return;
				}
				BlockPos entry = memory.surfaceEntry();
				Bari.path(entry != null ? new GoalBlock(entry) : new baritone.api.pathing.goals.GoalYLevel(Math.max(70, pl.getBlockY() + 30)));
				return;
			}
			if ("death".equals(arg)) {
				WorldMemory.Seen d = memory.nearest("death");
				if (d == null) {
					fail(Fail.NOT_FOUND, "no death spot in this dimension");
					return;
				}
				// Stand right on the spot: the drops are scattered around it.
				Bari.path(new GoalNear(d.pos(), 1));
				return;
			}
			if ("end_center".equals(arg)) {
				Bari.path(new GoalXZ(0, 6));
				return;
			}
			// "xz <x> <z>": a column to walk (or swim) to, e.g. across water for fluid_cross.
			if (arg != null && arg.startsWith("xz ")) {
				String[] a = arg.substring(3).trim().split("\\s+");
				try {
					Bari.path(new GoalXZ(Integer.parseInt(a[0]), Integer.parseInt(a[1])));
				} catch (RuntimeException e) {
					fail(Fail.ERROR, "goto xz needs two numbers");
				}
				return;
			}
			WorldMemory.Seen s = memory.nearest(arg == null ? "" : arg);
			if (s == null) {
				fail(Fail.NOT_FOUND, "no known " + arg + " in this dimension");
				return;
			}
			Bari.path(new GoalGetToBlock(s.pos()));
		}

		@Override
		protected void tick() {
			if ("surface".equals(arg)) {
				LocalPlayer pl = Mc.player();
				if (ticks % 10 == 0 && pl.level().canSeeSky(pl.blockPosition().above())) {
					done("back under open sky");
					return;
				}
				if (io.github.plrlr.autopilot.Tune.on("nav.surface_v2")) {
					surfaceV2(pl);
					return;
				}
				if (ticks > 20 && !Bari.pathing()) fail(Fail.UNREACHABLE, "couldn't find the way up");
				return;
			}
			if (ticks > 20 && !Bari.pathing()) {
				if ("death".equals(arg)) {
					WorldMemory.Seen d = memory.nearest("death");
					// Reached or not, don't try again: pickup handles what's in view from here.
					if (d != null) memory.forget("death", d.pos());
					LocalPlayer pl = Mc.player();
					if (d != null && d.pos().distSqr(pl.blockPosition()) > 6 * 6) {
						fail(Fail.UNREACHABLE, "couldn't reach the death spot");
						return;
					}
				}
				done("arrived near " + arg);
			}
		}
	}

	/** retreat: run away from nearby hostiles. */
	public static final class Retreat extends Skill {
		private double startDist;

		@Override
		public String name() {
			return "retreat";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 15;
			if (Tune.on("survival.danger_v2")) {
				startDist = nearestThreat();
				Bari.stop();
				return;
			}
			List<BlockPos> threats = new ArrayList<>();
			for (Perception.Seen s : Perception.look(20).mobs) if (s.hostile()) threats.add(s.entity().blockPosition());
			if (threats.isEmpty()) {
				done("nothing to run from");
				return;
			}
			startDist = nearestThreat();
			Bari.path(new GoalRunAway(24, threats.toArray(new BlockPos[0])));
		}

		@Override
		protected void tick() {
			if (Tune.on("survival.danger_v2")) {
				Perception seen = Perception.look(20);
				Danger.Verdict verdict = DangerSense.assess(seen);
				if (verdict.kind() != Danger.Kind.RETREAT || verdict.dx() == 0 && verdict.dz() == 0) {
					Mc.mc().options.keyUp.setDown(false);
					Perception.Seen threat = seen.nearestHostile();
					if (threat == null || threat.dist() >= 12 || threat.dist() > startDist + 3
							|| threat.type().equals("skeleton") && !Mc.canSee(threat.entity())) done("got away");
					else fail(Fail.NO_PROGRESS, "no safe retreat step");
					return;
				}
				// Recheck the next block every tick. A long Baritone escape can cross an unseen ledge.
				Mc.player().setYRot((float) Math.toDegrees(Math.atan2(-verdict.dx(), verdict.dz())));
				Mc.mc().options.keyUp.setDown(true);
				if (nearestThreat() >= 12 || nearestThreat() > startDist + 3) done("got away");
				return;
			}
			if (ticks <= 20 || Bari.pathing()) return;
			// Baritone stopping isn't the same as getting away: with no path it stops where it
			// stands, and 99 "got away" retreats in batch 11 (seed a) never moved the bot.
			double now = nearestThreat();
			if (now >= 12 || now > startDist + 3) done("got away");
			else fail(Fail.NO_PROGRESS, "couldn't get away (nearest monster " + Math.round(now) + " blocks)");
		}

		private static double nearestThreat() {
			Perception.Seen h = Perception.look(20).nearestHostile();
			return h == null ? Double.MAX_VALUE : h.dist();
		}
	}

	/**
	 * pickup [stations]: walk over dropped items nearby. With "stations", first break the crafting
	 * table and furnace we placed here (a furnace needs a pickaxe to drop), so they come along.
	 */
	public static final class Pickup extends Skill {
		private ItemEntity current;
		private int picked;
		private int sinceRepath;
		private BlockPos breaking;
		private int breakTicks;

		@Override
		public String name() {
			return "pickup";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 30;
		}

		/** Breaks our stations one by one; true while there's still one to break. */
		private boolean breakStations() {
			LocalPlayer pl = Mc.player();
			if (breaking != null && Mc.free(breaking)) {
				Mc.mc().gameMode.stopDestroyBlock();
				breaking = null;
			}
			if (breaking == null) {
				List<BlockPos> ours = Station.placedWithin(pl.blockPosition(), 8);
				if (ours.isEmpty()) return false;
				breaking = ours.get(0);
				breakTicks = 0;
			}
			if (pl.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(breaking)) > Mc.reach() - 0.5) {
				if (!Bari.pathing()) Bari.path(new GoalGetToBlock(breaking));
				return true;
			}
			if (Bari.pathing()) Bari.stop();
			if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
			if (++breakTicks > 20 * 8) {
				// Can't break it (wrong tool, out of reach): leave it.
				Station.forget(breaking);
				breaking = null;
				return true;
			}
			var st = Mc.state(breaking);
			Mc.lookAt(net.minecraft.world.phys.Vec3.atCenterOf(breaking));
			if (breakTicks == 1) {
				NightSkills.Shelter.holdBestTool(st);
				Mc.mc().gameMode.startDestroyBlock(breaking, net.minecraft.core.Direction.UP);
			} else {
				Mc.mc().gameMode.continueDestroyBlock(breaking, net.minecraft.core.Direction.UP);
			}
			Mc.swing();
			return true;
		}

		@Override
		protected void cleanup() {
			if (Mc.mc().gameMode != null) Mc.mc().gameMode.stopDestroyBlock();
			super.cleanup();
		}

		@Override
		protected void tick() {
			if ("stations".equals(arg) && breakStations()) return;
			if (current == null || !current.isAlive() || sinceRepath++ > 60) {
				if (current != null && !current.isAlive()) picked++;
				List<ItemEntity> items = new java.util.ArrayList<>(Perception.look(16).items);
				// Not the junk we tossed ourselves (Tidy), or it would be picked straight back up.
				if (io.github.plrlr.autopilot.Tune.on("inv.tidy")) items.removeIf(it -> io.github.plrlr.autopilot.Tidy.unwanted(it.getItem()));
				if (items.isEmpty() || picked >= 12) {
					done(picked > 0 ? "picked up " + picked + " stacks" : "nothing left to pick up");
					return;
				}
				current = items.get(0);
				sinceRepath = 0;
				Bari.path(new GoalBlock(current.blockPosition()));
			}
		}
	}

	/** idle: stand still for a moment (lets the brain wait for day, for a furnace, etc.). */
	public static final class Idle extends Skill {
		@Override
		public String name() {
			return "idle";
		}

		@Override
		protected void start() {
			Bari.stop();
		}

		@Override
		protected void tick() {
			if (ticks >= 60) done("waited 3 s");
		}
	}
}
