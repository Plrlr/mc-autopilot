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
		private enum Phase {DIG, COVER, WAIT}

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
				fail("shelter only makes sense in the overworld");
				return;
			}
			if (Mc.count("throwaway") == 0) {
				fail("need a dirt or cobblestone block to cover the hole");
				return;
			}
			LocalPlayer pl = Mc.player();
			BlockPos feet = pl.blockPosition();
			for (int i = 1; i <= 4; i++) {
				var st = Mc.state(feet.below(i));
				if (st.liquid() || (i == 4 && !Mc.solid(feet.below(i)))) {
					fail("ground here isn't safe to dig into");
					return;
				}
			}
			bottom = feet.below(3);
			Bari.stop();
		}

		private BlockPos digging;

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			switch (phase) {
				case DIG -> {
					// Dig straight down by hand so the hole is a clean 1x1 shaft mobs can't walk into.
					BlockPos feet = pl.blockPosition();
					if (feet.getY() <= bottom.getY() && pl.onGround()) {
						Mc.mc().gameMode.stopDestroyBlock();
						phase = Phase.COVER;
						return;
					}
					if (ticks > 20 * 30) {
						fail("couldn't dig down");
						return;
					}
					BlockPos below = feet.below();
					var st = Mc.state(below);
					if (st.liquid()) {
						fail("hit water or lava while digging");
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
						fail("couldn't cover the shelter");
						return;
					}
					Mc.holdItem(Items2.matcher("throwaway"));
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

	/** sleep: use a known bed, or place one from the inventory, then sleep till morning. */
	public static final class Sleep extends Skill {
		private BlockPos bed;
		private int wait;
		private boolean slept;

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
				fail("you can only sleep at night");
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
				fail("no bed");
				return;
			}
			// A bed needs two free blocks in the facing direction.
			Direction facing = pl.getDirection();
			BlockPos feet = pl.blockPosition();
			BlockPos spot = feet.relative(facing);
			if (!(Mc.free(spot) && Mc.free(spot.relative(facing)) && Mc.solid(spot.below()) && Mc.solid(spot.relative(facing).below()))) {
				fail("no flat room for a bed here");
				return;
			}
			Mc.holdItem(Items2.matcher("bed"));
			Mc.placeAt(spot);
			bed = spot;
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			if (pl.isSleeping()) {
				slept = true;
				return;
			}
			if (slept) {
				done("slept through the night");
				return;
			}
			if (Bari.pathing() && ticks < 20 * 30) return;
			if (wait++ % 20 == 5) {
				if (!Mc.id(Mc.state(bed).getBlock()).endsWith("_bed")) {
					memory.forget("bed", bed);
					fail("the bed is gone");
					return;
				}
				memory.remember("bed", bed, Mc.id(Mc.state(bed).getBlock()));
				Mc.useOn(bed, Direction.UP);
			}
			if (wait > 20 * 6) fail("couldn't sleep (monsters nearby?)");
		}
	}
}
