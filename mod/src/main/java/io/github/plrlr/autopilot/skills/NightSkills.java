package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Getting through the night: dig a small shelter, or sleep in a bed. */
public final class NightSkills {
	private NightSkills() {}

	/** shelter: dig 3 blocks down, cover the hole, wait for morning. */
	public static final class Shelter extends Skill {
		private enum Phase {MOVE, DIG, COVER, WAIT}

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

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 12;
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
					if (!Mc.isNight()) done("it's morning");
				}
			}
		}

		@Override
		protected void cleanup() {
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
