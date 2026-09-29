package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/** Getting through the night: dig a small shelter, or sleep in a bed. */
public final class NightSkills {
	private NightSkills() {}

	/**
	 * shelter: dig 3 blocks down, cover the hole, wait for morning.
	 * shelter heal: underground with monsters closing in, block up the gaps around us right here
	 * and wait to heal (eating if needed) instead of running through tunnels, where the trials'
	 * retreats kept dying to arrows and zombies.
	 */
	public static final class Shelter extends Skill {
		private enum Phase {MOVE, DIG, COVER, WAIT, WALL_IN, HEAL}

		private Phase phase = Phase.DIG;
		private BlockPos bottom;
		private int coverTries;

		@Override
		public String name() {
			return "shelter";
		}

		@Override
		public boolean interruptible() {
			return false;
		}

		private int wallTries;
		private boolean centered;
		private boolean sealed;
		/** Walled in for the night (not to heal): wait for morning once sealed. */
		private boolean nightWall;
		private float sealHealth;

		/** Healing behind blocks with every side closed: the only time the mob reflexes stand down. */
		public boolean sealed() {
			return (phase == Phase.HEAL || phase == Phase.WAIT && nightWall) && sealed;
		}

		@Override
		protected void start() {
			if ("heal".equals(arg)) {
				timeoutTicks = 20 * 60;
				Bari.stop();
				phase = Phase.WALL_IN;
				return;
			}
			timeoutTicks = 20 * 60 * 12;
			// Faster than digging a hole (gene night.wall_in): four sides and a roof right here,
			// a few seconds with 9 blocks, then wait for morning inside.
			if (io.github.plrlr.autopilot.Tune.on("night.wall_in") && Mc.count(Items2.matcher("throwaway")) >= 9
					&& Mc.dimension().equals("overworld")) {
				nightWall = true;
				Bari.stop();
				phase = Phase.WALL_IN;
				return;
			}
			if (!Mc.dimension().equals("overworld")) {
				fail(Fail.WRONG_PLACE, "shelter only makes sense in the overworld");
				return;
			}
			// No cover block needed up front: digging the shaft drops dirt or cobblestone.
			LocalPlayer pl = Mc.player();
			BlockPos spot = safeColumnNear(pl.blockPosition());
			if (spot == null) {
				fail(Fail.NO_ROOM, "no safe ground to dig into nearby");
				return;
			}
			bottom = spot.below(3);
			if (spot.equals(pl.blockPosition())) {
				Bari.stop();
			} else {
				Bari.path(new GoalBlock(spot));
				phase = Phase.MOVE;
			}
		}

		/** A spot within 6 blocks whose next 3 blocks down are diggable solid ground over a solid floor. */
		private static BlockPos safeColumnNear(BlockPos feet) {
			for (int r = 0; r <= 6; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
						for (int dy = 0; dy >= -1; dy--) {
							BlockPos p = feet.offset(dx, dy, dz);
							if (Mc.free(p) && Mc.free(p.above()) && safeBelow(p)) return p;
						}
					}
				}
			}
			return null;
		}

		private static boolean safeBelow(BlockPos p) {
			for (int i = 1; i <= 4; i++) {
				var st = Mc.state(p.below(i));
				if (st.liquid() || !Mc.solid(p.below(i))) return false;
				// Bedrock and obsidian can't be dug in time. (Sand and gravel are fine: we dig through
				// them downward and nothing is left above to fall.)
				String id = Mc.id(st.getBlock());
				if (i <= 3 && (id.equals("bedrock") || id.equals("obsidian"))) return false;
			}
			// Water or lava next to the shaft would flow in.
			for (int i = 1; i <= 3; i++) {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (Mc.state(p.below(i).relative(d)).liquid()) return false;
				}
			}
			return true;
		}

		private BlockPos digging;
		private Skill exit;
		private String exitWhy;

		private void exitTick() {
			exit.update();
			if (exit.result() == null) return;
			if (exit.result().ok()) done(exitWhy + "; climbed out");
			else fail(Fail.STUCK, exitWhy + ", but couldn't climb out: " + exit.result().detail());
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			switch (phase) {
				case MOVE -> {
					if (ticks > 20 * 20) {
						fail(Fail.UNREACHABLE, "couldn't reach a spot to dig in");
						return;
					}
					if (ticks > 10 && !Bari.pathing()) {
						BlockPos top = bottom.above(3);
						if (pl.blockPosition().equals(top)) phase = Phase.DIG;
						else fail(Fail.UNREACHABLE, "couldn't reach a spot to dig in");
					}
				}
				case DIG -> {
					// Dig straight down by hand so the hole is a clean 1x1 shaft mobs can't walk into.
					BlockPos feet = pl.blockPosition();
					if (feet.getY() <= bottom.getY() && pl.onGround()) {
						Mc.mc().gameMode.stopDestroyBlock();
						phase = Phase.COVER;
						return;
					}
					if (ticks > 20 * 30) {
						fail(Fail.USE_FAILED, "couldn't dig down");
						return;
					}
					BlockPos below = feet.below();
					var st = Mc.state(below);
					if (st.liquid()) {
						fail(Fail.HAZARD, "hit water or lava while digging");
						return;
					}
					if (Mc.free(below)) return; // falling into the hole
					Mc.lookAt(net.minecraft.world.phys.Vec3.atCenterOf(below).add(0, 0.5, 0));
					if (!below.equals(digging)) {
						holdBestTool(st);
						digging = below;
						Mc.mc().gameMode.startDestroyBlock(below, Direction.UP);
					} else {
						Mc.mc().gameMode.continueDestroyBlock(below, Direction.UP);
					}
					Mc.swing();
				}
				case COVER -> {
					if (ticks % 5 != 0) return;
					BlockPos lid = bottom.above(2);
					if (!Mc.free(lid)) {
						phase = Phase.WAIT;
						return;
					}
					if (coverTries++ > 6) {
						fail(Fail.PLACE_FAILED, "couldn't cover the shelter");
						return;
					}
					if (!Mc.holdItem(Items2.matcher("throwaway")) && !Mc.holdItem(Items2.matcher("planks"))) {
						fail(Fail.NEED_ITEM, "no block to cover the hole");
						return;
					}
					Mc.placeAt(lid);
				}
				case WAIT -> {
					if (exit != null) {
						exitTick();
						return;
					}
					String why = !Mc.isNight() ? "it's morning"
							// Waiting out a whole night costs up to 7 minutes of a run (batch/loop gen 1: up to
							// 495 s in shelters). A gene bounds it; the loop decides how long hiding pays.
							: ticks > 20 * io.github.plrlr.autopilot.Tune.i("night.shelter_max_s") ? "waited long enough; back to work" : null;
					if (why == null) return;
					// Gene night.shelter_exit: climb out of the covered shaft before handing back. Ending
					// at its bottom left the next skill boxed in: gens 57-58 logged "shelter -> it's morning"
					// then "goto surface: couldn't find the way up" or "stuck" and a 45 s unstuck timeout.
					if (io.github.plrlr.autopilot.Tune.on("night.shelter_exit") && !pl.level().canSeeSky(pl.blockPosition().above())) {
						exitWhy = why;
						exit = new StairUp();
						exit.begin(memory, "sky");
						return;
					}
					done(why);
				}
				case WALL_IN -> {
					// Center on the block first: off-center, the hitbox overlaps a side spot and that
					// side would stay open (laptop review).
					if (!centered) {
						centered = true;
						Bari.path(new GoalBlock(pl.blockPosition()));
						return;
					}
					if (Bari.pathing() && ticks < 20 * 3) return;
					if (ticks % 3 != 0) return;
					BlockPos feet = pl.blockPosition();
					// Feet level first, then head level (it rests on those), then the roof.
					List<BlockPos> around = new ArrayList<>();
					for (Direction d : Direction.Plane.HORIZONTAL) around.add(feet.relative(d));
					for (Direction d : Direction.Plane.HORIZONTAL) around.add(feet.above().relative(d));
					around.add(feet.above(2));
					BlockPos gap = null;
					for (BlockPos p : around) {
						if (Mc.free(p) && Mc.clearOfPlayer(p)) {
							gap = p;
							break;
						}
					}
					// Closed in, or out of blocks or tries (a mob standing in the gap): heal anyway.
					if (gap == null || ++wallTries > 30 || !Mc.holdItem(Items2.matcher("throwaway"))) {
						// Sealed only if no spot around is open at all: a spot skipped because it
						// overlaps our hitbox (standing off-center) is still a way in.
						sealed = true;
						for (BlockPos p : around) if (Mc.free(p)) sealed = false;
						sealHealth = pl.getHealth();
						phase = nightWall ? Phase.WAIT : Phase.HEAL;
						return;
					}
					Mc.placeAt(gap);
				}
				case HEAL -> {
					// Health only comes back with 18+ hunger; with no food, waiting only lets monsters gather.
					if (pl.getFoodData().getFoodLevel() < 18 && Mc.count(Items2.matcher("food")) == 0) {
						fail(Fail.NEED_ITEM, "can't heal: hunger " + pl.getFoodData().getFoodLevel() + " and no food");
						return;
					}
					// Something reaches us through the blocks: hiding isn't working, let the reflexes act.
					if (pl.getHealth() < sealHealth - 2) {
						fail(Fail.HAZARD, "hit while hiding (health " + Math.round(pl.getHealth()) + ")");
						return;
					}
					sealHealth = Math.max(sealHealth, pl.getHealth());
					boolean hungry = pl.getFoodData().getFoodLevel() < 18;
					if (hungry) {
						if (!Items2.matcher("food").test(pl.getMainHandItem())) Mc.holdItem(Items2.matcher("food"));
						pl.setXRot(-90f);
						Mc.mc().options.keyUse.setDown(true);
					} else {
						Mc.mc().options.keyUse.setDown(false);
					}
					if (pl.getHealth() >= 18) done("healed behind blocks");
					else if (ticks > 20 * 50) done("waited 50 s behind blocks (health " + Math.round(pl.getHealth()) + ")");
				}
			}
		}

		@Override
		protected void cleanup() {
			if (exit != null && exit.result() == null) exit.abort(Fail.INTERRUPTED, "shelter ended");
			if (Mc.mc().gameMode != null) Mc.mc().gameMode.stopDestroyBlock();
			super.cleanup();
		}

		/** Hold whatever breaks this block fastest (shovel for dirt, pickaxe for stone). */
		static void holdBestTool(net.minecraft.world.level.block.state.BlockState st) {
			int best = -1;
			float bestSpeed = 1.0f;
			for (int i = 0; i < 36; i++) {
				float sp = Mc.player().getInventory().getItem(i).getDestroySpeed(st);
				if (sp > bestSpeed) {
					bestSpeed = sp;
					best = i;
				}
			}
			if (best >= 0) {
				var target = Mc.player().getInventory().getItem(best);
				Mc.holdItem(s -> s == target);
			}
		}
	}

	/**
	 * sleep: use a known bed, or place one from the inventory, then sleep till morning. A bed we
	 * placed is picked back up afterwards, so it travels with us for the next night.
	 */
	public static final class Sleep extends Skill {
		private BlockPos bed;
		private int wait;
		private boolean slept;
		private boolean placedByUs;
		private int breakTicks = -1;

		@Override
		public String name() {
			return "sleep";
		}

		@Override
		public boolean interruptible() {
			return false;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60;
			if (!Mc.isNight()) {
				fail(Fail.WRONG_PLACE, "you can only sleep at night");
				return;
			}
			WorldMemory.Seen known = memory.nearest("bed");
			LocalPlayer pl = Mc.player();
			if (known != null && memory.nearestStation("bed") != null) {
				bed = known.pos();
				Bari.path(new GoalGetToBlock(bed));
				return;
			}
			if (Mc.count("bed") == 0) {
				fail(Fail.NEED_ITEM, "no bed");
				return;
			}
			Bari.stop();
			needPlace = true;
		}

		private boolean needPlace;
		private int placeTicks;
		private int relocations;

		/**
		 * A bed takes two flat free blocks in a row next to us. Looking at the first one turns us
		 * that way, which is the direction the bed extends in.
		 */
		private static BlockPos bedSpot(LocalPlayer pl) {
			BlockPos feet = pl.blockPosition();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos a = feet.relative(d), b = a.relative(d);
				if (Mc.free(a) && Mc.free(b) && Mc.solid(a.below()) && Mc.solid(b.below()) && Mc.clearOfPlayer(a)) return a;
			}
			return null;
		}

		/** Places our bed, walking to open ground first if there's no room here. */
		private void placeTick(LocalPlayer pl) {
			if (Bari.pathing()) return;
			if (placeTicks++ % 5 != 0) return;
			BlockPos spot = bedSpot(pl);
			if (spot == null) {
				BlockPos open = Station.openGround(pl);
				if (open == null || relocations++ >= 2) {
					fail(Fail.NO_ROOM, "no flat room for a bed nearby");
					return;
				}
				Bari.path(new GoalBlock(open));
				return;
			}
			if (placeTicks > 60) {
				fail(Fail.PLACE_FAILED, "the bed wouldn't place");
				return;
			}
			if (!Items2.id(pl.getMainHandItem()).endsWith("_bed")) {
				Mc.holdItem(Items2.matcher("bed"));
				return;
			}
			Mc.placeAt(spot);
			if (Mc.id(Mc.state(spot).getBlock()).endsWith("_bed") || placeTicks > 10) {
				bed = spot;
				placedByUs = true;
				needPlace = false;
				wait = 0;
			}
		}

		@Override
		protected void cleanup() {
			if (Mc.mc().gameMode != null) Mc.mc().gameMode.stopDestroyBlock();
			super.cleanup();
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			if (needPlace) {
				placeTick(pl);
				return;
			}
			if (pl.isSleeping()) {
				slept = true;
				return;
			}
			if (slept) {
				if (!placedByUs) {
					done("slept through the night");
					return;
				}
				// Break our bed (it breaks almost instantly) and let the drop fly to us.
				if (breakTicks < 0) breakTicks = 0;
				breakTicks++;
				boolean gone = !Mc.id(Mc.state(bed).getBlock()).endsWith("_bed");
				if (!gone && breakTicks < 60) {
					Mc.lookAt(net.minecraft.world.phys.Vec3.atCenterOf(bed));
					if (breakTicks == 1) Mc.mc().gameMode.startDestroyBlock(bed, Direction.UP);
					else Mc.mc().gameMode.continueDestroyBlock(bed, Direction.UP);
					Mc.swing();
					return;
				}
				if (gone) memory.forget("bed", bed);
				if (breakTicks < 80 && Mc.count("bed") == 0) return; // give the drop a moment to reach us
				done("slept through the night" + (Mc.count("bed") > 0 ? " and packed the bed" : ""));
				return;
			}
			if (Bari.pathing() && ticks < 20 * 30) return;
			if (wait++ % 20 == 5) {
				if (!Mc.id(Mc.state(bed).getBlock()).endsWith("_bed")) {
					memory.forget("bed", bed);
					fail(Fail.NOT_FOUND, "the bed is gone");
					return;
				}
				memory.remember("bed", bed, Mc.id(Mc.state(bed).getBlock()));
				Mc.useOn(bed, Direction.UP);
			}
			if (wait > 20 * 6) fail(Fail.USE_FAILED, "couldn't sleep (monsters nearby?)");
		}
	}
}
