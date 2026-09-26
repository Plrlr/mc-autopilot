package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalXZ;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Nether portal, stronghold search and the end portal. The hardest part of the run. */
public final class PortalSkills {
	private PortalSkills() {}

	/**
	 * The frame we're building, kept across runs of the skill so a retry finishes it instead of
	 * starting over (or mining it back up for its obsidian).
	 */
	private static BlockPos frameOrigin;
	private static Direction frameAlong;

	/** Obsidian already placed in our unfinished frame; counts toward the 10 we need. */
	public static int placedFrameObsidian() {
		if (frameOrigin == null || Mc.player() == null || !Mc.dimension().equals("overworld")) return 0;
		int n = 0;
		for (int[] c : FRAME) {
			if (!corner(c) && Mc.id(Mc.state(at(frameOrigin, frameAlong, c)).getBlock()).equals("obsidian")) n++;
		}
		return n;
	}

	/** Frame cells {x, y} in build order: bottom row, both sides bottom-up, then the top. */
	private static final int[][] FRAME = {
			{0, 0}, {1, 0}, {2, 0}, {3, 0},
			{0, 1}, {0, 2}, {0, 3}, {3, 1}, {3, 2}, {3, 3},
			{0, 4}, {1, 4}, {2, 4}, {3, 4}};

	private static boolean corner(int[] c) {
		return (c[0] == 0 || c[0] == 3) && (c[1] == 0 || c[1] == 4);
	}

	private static BlockPos at(BlockPos origin, Direction along, int[] c) {
		return origin.relative(along, c[0]).above(c[1]);
	}

	/**
	 * build_portal: place a 4x5 frame block by block like a player (obsidian sides, any block
	 * for the corners so 10 obsidian is enough), then light it with flint and steel.
	 */
	public static final class BuildPortal extends Skill {
		private enum Phase {SITE, WALK, PLACE, LIGHT, WAIT}

		private Phase phase = Phase.SITE;
		private BlockPos stand;
		private int cell, tries, wait;

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
			timeoutTicks = 20 * 150;
			if (!Mc.dimension().equals("overworld")) {
				fail("build the portal in the overworld");
				return;
			}
			if (Mc.count("obsidian") + placedFrameObsidian() < 10 || Mc.count("flint_and_steel") == 0) {
				fail("need 10 obsidian and flint and steel");
				return;
			}
			if (Mc.count("throwaway") + Mc.count("planks") < 4 - placedCorners()) {
				fail("need 4 cobblestone or dirt for the frame corners");
				return;
			}
			Bari.stop();
		}

		private int placedCorners() {
			if (frameOrigin == null) return 0;
			int n = 0;
			for (int[] c : FRAME) if (corner(c) && Mc.solid(at(frameOrigin, frameAlong, c))) n++;
			return n;
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			switch (phase) {
				case SITE -> {
					if (frameOrigin == null || frameOrigin.distSqr(pl.blockPosition()) > 48 * 48 || !siteStillUsable()) {
						if (!findSite(pl.blockPosition())) {
							fail("no flat open ground for a portal here");
							return;
						}
					}
					stand = frameOrigin.relative(frameAlong, 1).relative(frameAlong.getClockWise(), 2);
					if (!Mc.free(stand) || !Mc.free(stand.above()) || !Mc.solid(stand.below())) {
						stand = frameOrigin.relative(frameAlong, 2).relative(frameAlong.getClockWise(), 2);
					}
					Bari.path(new GoalBlock(stand));
					phase = Phase.WALK;
					wait = 0;
				}
				case WALK -> {
					if (++wait > 20 * 40) {
						fail("couldn't reach the portal site");
						return;
					}
					if (wait > 10 && !Bari.pathing()) {
						phase = Phase.PLACE;
						cell = 0;
					}
				}
				case PLACE -> {
					if (ticks % 4 != 0) return;
					while (cell < FRAME.length && Mc.solid(at(frameOrigin, frameAlong, FRAME[cell]))) {
						cell++;
						tries = 0;
					}
					if (cell >= FRAME.length) {
						phase = Phase.LIGHT;
						tries = 0;
						return;
					}
					int[] c = FRAME[cell];
					BlockPos target = at(frameOrigin, frameAlong, c);
					if (!Mc.free(target)) {
						fail("something is in the way of the frame");
						return;
					}
					if (++tries > 8) {
						// Out of reach: step back to where we can reach, then try again.
						if (tries > 30) {
							fail("couldn't place part of the frame");
							return;
						}
						if (!Bari.pathing()) Bari.path(new GoalBlock(stand));
						return;
					}
					boolean held = corner(c)
							? Mc.holdItem(Items2.matcher("throwaway")) || Mc.holdItem(Items2.matcher("planks"))
							: Mc.holdItem(st -> Items2.id(st).equals("obsidian"));
					if (!held) {
						fail(corner(c) ? "out of blocks for the corners" : "out of obsidian");
						return;
					}
					Mc.placeAt(target);
				}
				case LIGHT -> {
					BlockPos inside = at(frameOrigin, frameAlong, new int[]{1, 1});
					if (Mc.id(Mc.state(inside).getBlock()).equals("nether_portal")) {
						memory.remember("nether_portal", inside, "nether_portal");
						frameOrigin = null;
						done("nether portal lit");
						return;
					}
					if (ticks % 10 != 0) return;
					if (++tries > 6) {
						fail("couldn't light the portal");
						return;
					}
					BlockPos base = at(frameOrigin, frameAlong, new int[]{1, 0});
					Mc.holdItem(st -> Items2.id(st).equals("flint_and_steel"));
					Mc.useOn(base, Direction.UP);
				}
				case WAIT -> {
				}
			}
		}

		/** The remembered frame spot is still ours: every cell is either free or already built. */
		private boolean siteStillUsable() {
			for (int[] c : FRAME) {
				BlockPos p = at(frameOrigin, frameAlong, c);
				String id = Mc.id(Mc.state(p).getBlock());
				if (!Mc.free(p) && !id.equals("obsidian") && !(corner(c) && Mc.solid(p))) return false;
			}
			return true;
		}

		/** A flat, open 4-wide strip on solid ground near us, with room in front to stand. */
		private static boolean findSite(BlockPos feet) {
			for (int r = 2; r <= 12; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
						for (int dy = -3; dy <= 3; dy++) {
							BlockPos o = feet.offset(dx, dy, dz);
							for (Direction along : new Direction[]{Direction.EAST, Direction.SOUTH}) {
								if (fits(o, along)) {
									frameOrigin = o;
									frameAlong = along;
									return true;
								}
							}
						}
					}
				}
			}
			return false;
		}

		private static boolean fits(BlockPos o, Direction along) {
			Direction front = along.getClockWise();
			for (int x = 0; x < 4; x++) {
				BlockPos col = o.relative(along, x);
				if (!Mc.solid(col.below()) || Mc.state(col.below()).liquid()) return false;
				for (int y = 0; y < 5; y++) if (!Mc.free(col.above(y))) return false;
				// Room in front (where we stand) and flat ground there.
				for (int f = 1; f <= 2; f++) {
					BlockPos p = col.relative(front, f);
					if (!Mc.free(p) || !Mc.free(p.above()) || !Mc.solid(p.below())) return false;
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
		frameOrigin = null;
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
