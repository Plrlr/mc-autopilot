package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.schematic.ISchematic;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Nether portal, stronghold search and the end portal. The hardest part of the run. */
public final class PortalSkills {
	private PortalSkills() {}

	/** A 4x5 obsidian frame (corners optional) with an empty inside, for Baritone's builder. */
	static final class PortalFrame implements ISchematic {
		@Override
		public boolean inSchematic(int x, int y, int z, BlockState current) {
			boolean corner = (x == 0 || x == 3) && (y == 0 || y == 4);
			return !corner;
		}

		@Override
		public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
			boolean frame = x == 0 || x == 3 || y == 0 || y == 4;
			return frame ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState();
		}

		@Override
		public int widthX() {
			return 4;
		}

		@Override
		public int heightY() {
			return 5;
		}

		@Override
		public int lengthZ() {
			return 1;
		}
	}

	/** build_portal: build the frame with Baritone's builder, then light it with flint and steel. */
	public static final class BuildPortal extends Skill {
		private BlockPos origin;
		private int lightTries;

		@Override
		public String name() {
			return "build_portal";
		}

		@Override
		public boolean interruptible() {
			return false;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 240;
			if (!Mc.dimension().equals("overworld")) {
				fail("build the portal in the overworld");
				return;
			}
			if (Mc.count("obsidian") < 10 || Mc.count("flint_and_steel") == 0) {
				fail("need 10 obsidian and flint and steel");
				return;
			}
			LocalPlayer pl = Mc.player();
			BlockPos feet = pl.blockPosition();
			// Build a few blocks away along X so the player isn't standing inside the frame.
			origin = feet.offset(-1, 0, 3);
			Bari.get().getBuilderProcess().build("autopilot_portal", new PortalFrame(), origin);
		}

		@Override
		protected void tick() {
			if (Bari.get().getBuilderProcess().isActive()) return;
			if (ticks < 20) return;
			BlockPos insideBottom = origin.offset(1, 1, 0);
			if (Mc.id(Mc.state(insideBottom).getBlock()).equals("nether_portal")) {
				memory.remember("nether_portal", insideBottom, "nether_portal");
				done("nether portal lit");
				return;
			}
			if (!frameComplete()) {
				fail("the frame isn't complete (builder stopped)");
				return;
			}
			if (ticks % 10 != 0) return;
			if (lightTries++ > 4) {
				fail("couldn't light the portal");
				return;
			}
			BlockPos base = origin.offset(1, 0, 0);
			if (Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(base)) > 4) {
				Bari.path(new GoalGetToBlock(base));
				return;
			}
			Mc.holdItem(s -> Items2.id(s).equals("flint_and_steel"));
			Mc.useOn(base, Direction.UP);
		}

		private boolean frameComplete() {
			PortalFrame f = new PortalFrame();
			for (int x = 0; x < 4; x++) {
				for (int y = 0; y < 5; y++) {
					if (!f.inSchematic(x, y, 0, null)) continue;
					boolean frame = x == 0 || x == 3 || y == 0 || y == 4;
					if (frame && !Mc.id(Mc.state(origin.offset(x, y, 0)).getBlock()).equals("obsidian")) return false;
				}
			}
			return true;
		}
	}

	/** enter_portal nether|overworld|end: walk into a known portal and wait for the dimension change. */
	public static final class EnterPortal extends Skill {
		private String startDim;
		private BlockPos portal;

		@Override
		public String name() {
			return "enter_portal";
		}

		@Override
		public boolean interruptible() {
			return false;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 90;
			startDim = Mc.dimension();
			String group = "end".equals(arg) ? "end_portal" : "nether_portal";
			WorldMemory.Seen s = memory.nearest(group);
			if (s == null) {
				fail("no known " + group + " here");
				return;
			}
			portal = s.pos();
			Bari.path(new GoalBlock(portal));
		}

		@Override
		protected void tick() {
			if (!Mc.dimension().equals(startDim)) {
				done("now in " + Mc.dimension());
				return;
			}
			// Standing in a nether portal takes 4 s; just keep still inside it.
			if (Mc.player().blockPosition().equals(portal)) {
				if (Bari.pathing()) Bari.stop();
				return;
			}
			if (ticks > 40 && !Bari.pathing()) {
				if (ticks % 40 == 0) Bari.path(new GoalBlock(portal));
			}
		}
	}

	/** The last eye throws, kept across runs of the skill to triangulate the stronghold. */
	private record Throw(Vec3 from, Vec3 dir) {}

	private static final List<Throw> THROWS = new ArrayList<>();

	public static void resetThrows() {
		THROWS.clear();
	}

	/** locate_stronghold: throw an eye of ender, watch where it flies, walk that way. */
	public static final class LocateStronghold extends Skill {
		private enum Phase {THROW, WATCH, WALK}

		private Phase phase = Phase.THROW;
		private Vec3 from;
		private EyeOfEnder eye;
		private Vec3 eyeStart;
		private int watch;

		@Override
		public String name() {
			return "locate_stronghold";
		}

		@Override
		public boolean interruptible() {
			return false;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 150;
			if (!Mc.dimension().equals("overworld")) {
				fail("eyes of ender only work in the overworld");
				return;
			}
			if (Mc.count("ender_eye") == 0) {
				fail("no eyes of ender");
				return;
			}
			Bari.stop();
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			switch (phase) {
				case THROW -> {
					if (ticks < 5) return;
					Mc.holdItem(s -> Items2.id(s).equals("ender_eye"));
					if (ticks < 8) return;
					from = pl.position();
					pl.setXRot(-10f);
					Mc.useItem();
					phase = Phase.WATCH;
				}
				case WATCH -> {
					watch++;
					if (eye == null) {
						for (Entity e : Mc.mc().level.entitiesForRendering()) {
							if (e instanceof EyeOfEnder ee && ee.distanceTo(pl) < 4) {
								eye = ee;
								eyeStart = ee.position();
							}
						}
						if (watch > 20 && eye == null) {
							fail("the eye didn't fly (no stronghold in range?)");
						}
						return;
					}
					if (watch < 35 && eye.isAlive()) return;
					Vec3 move = eye.position().subtract(eyeStart);
					Vec3 flat = new Vec3(move.x, 0, move.z);
					if (flat.length() < 1.5) {
						// The eye went down instead of away: the stronghold is right below.
						BlockPos below = pl.blockPosition();
						Bari.path(new GoalBlock(below.getX(), Math.max(pl.level().getMinY() + 10, 20), below.getZ()));
						phase = Phase.WALK;
						timeoutTicks = ticks + 20 * 120;
						return;
					}
					Vec3 dir = flat.normalize();
					THROWS.add(new Throw(from, dir));
					Vec3 goal = triangulate();
					if (goal == null) goal = from.add(dir.scale(180));
					Bari.path(new GoalXZ((int) goal.x, (int) goal.z));
					phase = Phase.WALK;
				}
				case WALK -> {
					if (memory.nearest("end_portal_frame") != null) {
						done("found the end portal frames");
						return;
					}
					if (ticks > 40 && !Bari.pathing()) done("walked toward the stronghold; throw again");
				}
			}
		}

		/** Crossing point of the last two throws' lines, if they're far enough apart to trust. */
		private static Vec3 triangulate() {
			if (THROWS.size() < 2) return null;
			Throw a = THROWS.get(THROWS.size() - 2), b = THROWS.get(THROWS.size() - 1);
			if (a.from.distanceTo(b.from) < 60) return null;
			double cross = a.dir.x * b.dir.z - a.dir.z * b.dir.x;
			if (Math.abs(cross) < 0.05) return null;
			double dx = b.from.x - a.from.x, dz = b.from.z - a.from.z;
			double t = (dx * b.dir.z - dz * b.dir.x) / cross;
			if (t < 0 || t > 3000) return null;
			return a.from.add(a.dir.scale(t));
		}
	}

	/** fill_end_portal: put an eye of ender into every empty frame we know about. */
	public static final class FillEndPortal extends Skill {
		private BlockPos current;
		private int tries;

		@Override
		public String name() {
			return "fill_end_portal";
		}

		@Override
		public boolean interruptible() {
			return false;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 120;
			if (memory.all("end_portal_frame").isEmpty()) fail("no end portal frames seen yet");
		}

		@Override
		protected void tick() {
			if (memory.nearest("end_portal") != null) {
				done("the end portal is open");
				return;
			}
			if (ticks % 10 != 0) return;
			if (current == null) {
				current = nextEmptyFrame();
				if (current == null) {
					if (ticks > 60) done("all known frames have eyes; look for more frames");
					return;
				}
				tries = 0;
			}
			BlockState st = Mc.state(current);
			if (!(st.getBlock() instanceof EndPortalFrameBlock) || st.getValue(EndPortalFrameBlock.HAS_EYE)) {
				current = null;
				return;
			}
			if (Mc.count("ender_eye") == 0) {
				fail("out of eyes of ender");
				return;
			}
			if (Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(current)) > 4) {
				if (!Bari.pathing()) Bari.path(new GoalGetToBlock(current));
				if (++tries > 30) {
					fail("can't reach a frame");
				}
				return;
			}
			Bari.stop();
			Mc.holdItem(s -> Items2.id(s).equals("ender_eye"));
			Mc.useOn(current, Direction.UP);
		}

		private BlockPos nextEmptyFrame() {
			BlockPos best = null;
			double bd = Double.MAX_VALUE;
			for (WorldMemory.Seen s : memory.all("end_portal_frame")) {
				BlockState st = Mc.state(s.pos());
				if (!(st.getBlock() instanceof EndPortalFrameBlock) || st.getValue(EndPortalFrameBlock.HAS_EYE)) continue;
				double d = s.pos().distSqr(Mc.player().blockPosition());
				if (d < bd) {
					bd = d;
					best = s.pos();
				}
			}
			return best;
		}
	}
}
