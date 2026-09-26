package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * build_portal without 10 obsidian: cast the frame in place the way speedrunners do, so no
 * diamond pickaxe is needed. Each frame block is made by pouring lava from a bucket into its
 * spot and water right beside it: a lava source touched by water turns into obsidian.
 *
 * A wall of throwaway blocks goes up behind the frame first; it gives every spot (even the
 * ones in mid-air) a face to aim the buckets at. Lava is fetched one bucket at a time from a
 * pool nearby, and the water is scooped back up after each block. The corners are left out:
 * a portal frame doesn't need them.
 */
public final class CastPortal extends Skill {
	private enum Phase {SITE, APPROACH, WALL, NEXT, FETCH, WALK, LAVA, POUR, SCOOP, BREAK, CLEAR, LIGHT}

	/** Frame width 4 (x 0..3), height 5 (y 0..4); the wall behind also covers y 5 for the water. */
	private static final int WALL_H = 6;

	/**
	 * Cast order: side columns top-down (so a ray to the next block never has to pass a finished
	 * one below... or above it), then the bottom, then the top.
	 */
	private static final int[][] CAST = {
			{0, 3}, {0, 2}, {0, 1}, {3, 3}, {3, 2}, {3, 1}, {1, 0}, {2, 0}, {1, 4}, {2, 4}};

	/** The site, kept across runs of the skill so a retry finishes the same frame. */
	private static BlockPos origin;
	private static Direction along;

	/** Throwaway blocks needed for the wall and a small margin (Baritone may spend a few). */
	public static final int BLOCKS_NEEDED = 28;

	private Phase phase = Phase.SITE;
	private int wait, tries, wallTries;
	private BlockPos lastWall;
	private BlockPos target, water, breaking;
	private Aim lavaAim, waterAim;
	private BucketSkills.FillBucket fetch;
	private int castFails;
	private int approachTicks = 20 * 45;

	private record Aim(BlockPos cell, Vec3 point) {}

	private record Plan(BlockPos stand, Aim lava, Aim water) {}

	@Override
	public String name() {
		return "build_portal";
	}

	@Override
	public boolean interruptible() {
		return false;
	}

	/** True when build_portal should cast (not enough obsidian to place). */
	public static boolean needed() {
		return Mc.count("obsidian") + PortalSkills.placedFrameObsidian() < 10;
	}

	public static void reset() {
		origin = null;
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60 * 6;
		if (!Mc.dimension().equals("overworld")) {
			fail("cast the portal in the overworld (water boils in the nether)");
			return;
		}
		if (Mc.count("flint_and_steel") == 0) {
			fail("need flint and steel");
			return;
		}
		if (Mc.count("water_bucket") == 0) {
			fail("need a water bucket");
			return;
		}
		if (Mc.count("bucket") + Mc.count("lava_bucket") == 0) {
			fail("need a second bucket for lava");
			return;
		}
		Bari.stop();
	}

	private static Direction front() {
		return along.getClockWise();
	}

	private static BlockPos cell(int x, int y) {
		return origin.relative(along, x).above(y);
	}

	private static BlockPos wall(int x, int y) {
		return cell(x, y).relative(front().getOpposite());
	}

	private static boolean obsidian(BlockPos p) {
		return Mc.id(Mc.state(p).getBlock()).equals("obsidian");
	}

	private static boolean lava(BlockPos p) {
		return Mc.id(Mc.state(p).getBlock()).equals("lava");
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		switch (phase) {
			case SITE -> {
				WorldMemory.Seen pool = memory.nearest("lava");
				if (Mc.count("lava_bucket") == 0 && pool == null) {
					fail("no known lava pool to cast from");
					return;
				}
				if (origin != null && (origin.distSqr(pl.blockPosition()) > 40 * 40 || !siteUsable(origin, along))) origin = null;
				if (origin == null) {
					// Near the lava: every block of the frame is one trip to the pool and back.
					if (pool != null && pool.pos().distSqr(pl.blockPosition()) > 14 * 14) {
						Bari.path(new GoalNear(pool.pos(), 7));
						phase = Phase.APPROACH;
						wait = 0;
						// Walking takes about a second per 4 blocks; allow for detours.
						approachTicks = 20 * (30 + (int) Math.sqrt(pool.pos().distSqr(pl.blockPosition())) / 2);
						timeoutTicks = Math.max(timeoutTicks, ticks + approachTicks + 20 * 60 * 5);
						return;
					}
					if (!findSite(pl.blockPosition())) {
						fail("no flat open ground for a portal near the lava");
						return;
					}
				}
				phase = Phase.WALL;
				wait = 0;
			}
			case APPROACH -> {
				if (++wait > approachTicks) {
					fail("couldn't get near the lava pool");
					return;
				}
				if (wait > 10 && !Bari.pathing()) {
					if (!findSite(pl.blockPosition())) {
						fail("no flat open ground for a portal near the lava");
						return;
					}
					phase = Phase.WALL;
					wait = 0;
				}
			}
			case WALL -> wallTick(pl);
			case NEXT -> nextTick(pl);
			case FETCH -> {
				fetch.update();
				if (fetch.result() == null) return;
				if (!fetch.result().ok()) {
					fail("couldn't fill a bucket with lava: " + fetch.result().detail());
					return;
				}
				phase = Phase.NEXT;
			}
			case WALK -> {
				if (++wait > 20 * 30) {
					castFailed("couldn't reach a spot to cast from");
					return;
				}
				if (wait > 5 && !Bari.pathing()) {
					phase = Phase.LAVA;
					wait = 0;
					tries = 0;
				}
			}
			case LAVA -> {
				// Aim from where we actually stand (Baritone stops a little off-center).
				if (wait++ == 0) {
					Mc.holdItem(s -> Items2.id(s).equals("lava_bucket"));
					return;
				}
				Aim a = aimAt(pl.getEyePosition(), target);
				Aim w = water == null ? null : aimAt(pl.getEyePosition(), water);
				if (a == null || w == null) {
					castFailed("lost the line of sight to the frame");
					return;
				}
				lavaAim = a;
				waterAim = w;
				Mc.lookAt(a.point());
				Mc.useItem();
				log("lava into " + target.toShortString() + " from " + pl.blockPosition().toShortString() + ", water planned at " + water.toShortString());
				phase = Phase.POUR;
				wait = 0;
			}
			case POUR -> {
				wait++;
				if (wait == 1) {
					Mc.holdItem(s -> Items2.id(s).equals("water_bucket"));
					return;
				}
				if (obsidian(target)) {
					// Flowing water nearby already did it.
					phase = Phase.SCOOP;
					wait = 0;
					return;
				}
				if (!lava(target)) {
					if (wait > 8) {
						castFailed("the lava didn't land in the frame");
					}
					return;
				}
				Mc.lookAt(waterAim.point());
				Mc.useItem();
				log("water at " + water.toShortString() + " (lava there: " + Mc.id(Mc.state(target).getBlock()) + ")");
				phase = Phase.SCOOP;
				wait = 0;
			}
			case SCOOP -> {
				wait++;
				if (wait < 8) return;
				if (Mc.count("water_bucket") > 0) {
					if (!obsidian(target)) castFailed("the lava didn't harden");
					else phase = Phase.NEXT;
					return;
				}
				if (wait % 5 != 0) return;
				if (wait > 60) {
					fail("couldn't scoop the water back up");
					return;
				}
				// The water sits in the spot we poured into; take it back for the next block.
				BlockPos src = water != null && BucketSkills.isSource(water, "water") ? water : findWaterSource();
				if (src == null) {
					log("no water source near the frame; target is " + Mc.id(Mc.state(target).getBlock()) + ", planned water spot has "
							+ Mc.id(Mc.state(water).getBlock()));
					fail("lost the water");
					return;
				}
				if (wait == 10 || wait == 40) log("scooping water at " + src.toShortString() + " from " + pl.blockPosition().toShortString()
						+ ", target is " + Mc.id(Mc.state(target).getBlock()));
				// Halfway through without success: move to where the water is in plain view.
				if (wait == 30) {
					List<BlockPos> spots = BucketSkills.standSpots(src, Vec3.atCenterOf(src), src);
					if (!spots.isEmpty()) Bari.path(new GoalBlock(spots.get(0)));
				}
				if (Bari.pathing()) return;
				Vec3 aim = Vec3.atCenterOf(src);
				Mc.holdItem(s -> Items2.id(s).equals("bucket"));
				Mc.lookAt(aim);
				Mc.useItem();
			}
			case BREAK -> {
				if (Mc.free(breaking)) {
					Mc.mc().gameMode.stopDestroyBlock();
					breaking = null;
					phase = Phase.NEXT;
					return;
				}
				if (++wait > 20 * 15) {
					fail("couldn't clear a block out of the frame");
					return;
				}
				var st = Mc.state(breaking);
				Mc.lookAt(Vec3.atCenterOf(breaking));
				if (wait == 1) {
					NightSkills.Shelter.holdBestTool(st);
					Mc.mc().gameMode.startDestroyBlock(breaking, Direction.UP);
				} else {
					Mc.mc().gameMode.continueDestroyBlock(breaking, Direction.UP);
				}
				Mc.swing();
			}
			case CLEAR -> {
				// Leftover water or cobblestone inside the frame stops it from lighting.
				for (int x = 1; x <= 2; x++) {
					for (int y = 1; y <= 3; y++) {
						BlockPos p = cell(x, y);
						if (!Mc.state(p).getFluidState().isEmpty()) {
							if (++wait > 20 * 8) {
								fail("water won't drain out of the frame");
							}
							return;
						}
						if (!Mc.free(p)) {
							breaking = p;
							phase = Phase.BREAK;
							wait = 0;
							return;
						}
					}
				}
				phase = Phase.LIGHT;
				wait = 0;
				tries = 0;
			}
			case LIGHT -> {
				BlockPos inside = cell(1, 1);
				if (Mc.id(Mc.state(inside).getBlock()).equals("nether_portal")) {
					memory.remember("nether_portal", inside, "nether_portal");
					origin = null;
					done("cast and lit a nether portal");
					return;
				}
				if (wait++ % 10 != 0) return;
				if (++tries > 6) {
					fail("couldn't light the portal");
					return;
				}
				if (!Mc.holdItem(s -> Items2.id(s).equals("flint_and_steel"))) {
					fail("lost the flint and steel");
					return;
				}
				BlockPos base = cell(1, 0);
				if (pl.getEyePosition().distanceTo(Vec3.atCenterOf(base)) > Mc.reach() - 0.5) {
					if (!Bari.pathing()) Bari.path(new GoalBlock(base.relative(front()).relative(front())));
					return;
				}
				Mc.useOn(base, Direction.UP);
			}
		}
	}

	/** Puts up the wall behind the frame, bottom row first so every block has something to rest on. */
	private void wallTick(LocalPlayer pl) {
		if (ticks % 4 != 0) return;
		BlockPos next = null;
		for (int y = 0; y < WALL_H && next == null; y++) {
			for (int x = 0; x < 4; x++) {
				BlockPos w = wall(x, y);
				if (Mc.free(w)) {
					next = w;
					break;
				}
			}
		}
		if (next == null) {
			phase = Phase.NEXT;
			return;
		}
		BlockPos stand = cell(1, 0).relative(front());
		if (!pl.blockPosition().equals(stand)) {
			if (!Bari.pathing()) {
				if (++wallTries > 12) {
					fail("couldn't get in front of the portal site");
					return;
				}
				Bari.path(new GoalBlock(stand));
			}
			return;
		}
		// The same spot still empty after several clicks: give up rather than click forever.
		if (!next.equals(lastWall)) tries = 0;
		lastWall = next;
		if (++tries > 8) {
			fail("couldn't build the wall behind the frame");
			return;
		}
		if (!Mc.holdItem(Items2.matcher("throwaway"))) {
			fail("out of blocks for the wall (need about " + BLOCKS_NEEDED + ")");
			return;
		}
		Mc.placeAt(next);
	}

	/** Picks the next frame block and gets what's needed to cast it. */
	private void nextTick(LocalPlayer pl) {
		target = null;
		for (int[] c : CAST) {
			BlockPos p = cell(c[0], c[1]);
			if (!obsidian(p)) {
				target = p;
				break;
			}
		}
		if (target == null) {
			phase = Phase.CLEAR;
			wait = 0;
			return;
		}
		if (lava(target) && BucketSkills.isSource(target, "lava")) {
			// Our lava from an interrupted try: water finishes it.
			water = waterSpotFor(target, pl.getEyePosition());
			if (water != null) {
				waterAim = aimAt(pl.getEyePosition(), water);
				phase = Phase.POUR;
				wait = 0;
				return;
			}
		}
		if (!Mc.free(target) && Mc.state(target).getFluidState().isEmpty()) {
			// Cobblestone from lava meeting water, or anything else in the way.
			breaking = target;
			phase = Phase.BREAK;
			wait = 0;
			return;
		}
		if (!Mc.state(target).getFluidState().isEmpty()) {
			// Flowing water from the last block drains in a second or two.
			if (++wait > 20 * 6) castFailed("water won't drain from the frame");
			return;
		}
		if (Mc.count("water_bucket") == 0) {
			fail("lost the water bucket");
			return;
		}
		if (Mc.count("lava_bucket") == 0) {
			fetch = new BucketSkills.FillBucket();
			fetch.begin(memory, "lava");
			phase = Phase.FETCH;
			return;
		}
		Plan plan = planFor(target, pl);
		if (plan == null) {
			castFailed("no spot with a clear aim at the frame");
			return;
		}
		water = plan.water().cell();
		wait = 0;
		if (pl.blockPosition().equals(plan.stand())) {
			phase = Phase.LAVA;
		} else {
			Bari.path(new GoalBlock(plan.stand()));
			phase = Phase.WALK;
		}
	}

	/** Trial runs showed the cast step by step only in the game log; keep it there. */
	private static void log(String s) {
		io.github.plrlr.autopilot.AutopilotMod.LOGGER.info("[cast] {}", s);
	}

	private void castFailed(String why) {
		log("failed: " + why);
		Bari.stop();
		if (++castFails > 6) {
			fail(why);
			return;
		}
		phase = Phase.NEXT;
		wait = 0;
	}

	/** Water source left in or around the frame (it may have spread from where we poured). */
	private BlockPos findWaterSource() {
		for (int x = -2; x <= 5; x++) {
			for (int y = -1; y <= WALL_H + 1; y++) {
				for (int f = -2; f <= 3; f++) {
					BlockPos p = cell(x, y).relative(front(), f);
					if (BucketSkills.isSource(p, "water")) return p;
				}
			}
		}
		return null;
	}

	/** Where to put the water for a lava block: an empty spot beside or above it, never below. */
	private BlockPos waterSpotFor(BlockPos t, Vec3 eye) {
		for (BlockPos w : waterCandidates(t)) {
			if (aimAt(eye, w) != null) return w;
		}
		return null;
	}

	private List<BlockPos> waterCandidates(BlockPos t) {
		List<BlockPos> out = new ArrayList<>();
		// Lava only reacts to water above or beside it.
		for (BlockPos w : new BlockPos[]{t.above(), t.relative(along), t.relative(along.getOpposite()), t.relative(front())}) {
			if (Mc.free(w) && !obsidian(w) && Mc.clearOfPlayer(w)) out.add(w);
		}
		return out;
	}

	/** A place to stand from which both the lava spot and a water spot can be aimed at. */
	private Plan planFor(BlockPos t, LocalPlayer pl) {
		Plan best = null;
		double bestD = Double.MAX_VALUE;
		List<BlockPos> waters = waterCandidates(t);
		for (int x = -2; x <= 5; x++) {
			for (int f = 1; f <= 4; f++) {
				for (int y = -1; y <= 2; y++) {
					BlockPos s = origin.relative(along, x).relative(front(), f).above(y);
					if (!BucketSkills.standable(s)) continue;
					Vec3 eye = Vec3.atBottomCenterOf(s).add(0, 1.62, 0);
					if (eye.distanceTo(Vec3.atCenterOf(t)) > Mc.reach() + 0.5) continue;
					Aim a = aimAt(eye, t);
					if (a == null) continue;
					for (BlockPos w : waters) {
						if (w.equals(s) || w.equals(s.above())) continue;
						Aim b = aimAt(eye, w);
						if (b == null) continue;
						double d = s.distSqr(pl.blockPosition());
						if (d < bestD) {
							bestD = d;
							best = new Plan(s.immutable(), a, b);
						}
						break;
					}
				}
			}
		}
		return best;
	}

	/**
	 * A point to look at so that using a bucket puts its fluid into `cell`: a spot on the face
	 * of a solid neighbor, reachable and with nothing solid in the way (fluids don't block).
	 */
	private static Aim aimAt(Vec3 eye, BlockPos cell) {
		LocalPlayer pl = Mc.player();
		for (Direction d : Direction.values()) {
			BlockPos n = cell.relative(d);
			if (!Mc.solid(n) || Mc.isInteractive(n)) continue;
			Direction face = d.getOpposite();
			Vec3 c = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
			// Try the face's center and points toward its edges: a slightly higher point often
			// clears the frame block below.
			for (double[] o : new double[][]{{0, 0}, {0.3, 0}, {-0.3, 0}, {0, 0.3}, {0, -0.3}, {0.3, 0.3}, {-0.3, 0.3}}) {
				Vec3 p = c.add(offset(face, o[0], o[1]));
				if (eye.distanceTo(p) > Mc.reach() - 0.3) continue;
				Vec3 end = p.add(p.subtract(eye).normalize().scale(0.3));
				BlockHitResult hit = pl.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, pl));
				if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(n) && hit.getDirection() == face) {
					return new Aim(cell, p);
				}
			}
		}
		return null;
	}

	/** An offset within the plane of a face: u along the face's horizontal axis, v up (or along z for top/bottom). */
	private static Vec3 offset(Direction face, double u, double v) {
		return switch (face.getAxis()) {
			case X -> new Vec3(0, v, u);
			case Z -> new Vec3(u, v, 0);
			case Y -> new Vec3(u, 0, v);
		};
	}

	private static boolean findSite(BlockPos feet) {
		for (int r = 2; r <= 12; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					for (int dy = -3; dy <= 3; dy++) {
						BlockPos o = feet.offset(dx, dy, dz);
						for (Direction a : new Direction[]{Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH}) {
							if (fits(o, a)) {
								origin = o.immutable();
								along = a;
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	/**
	 * Flat solid ground under the frame, the frame's space and the row above it empty and dry,
	 * room to stand in front, and the wall's spots either empty or already solid. Away from the
	 * lava itself, so flowing lava can't reach the frame.
	 */
	private static boolean fits(BlockPos o, Direction a) {
		Direction front = a.getClockWise();
		for (int x = 0; x < 4; x++) {
			BlockPos col = o.relative(a, x);
			if (!Mc.solid(col.below()) || Mc.state(col.below()).liquid()) return false;
			for (int y = 0; y < WALL_H; y++) if (!Mc.free(col.above(y)) || nearLava(col.above(y))) return false;
			BlockPos back = col.relative(front.getOpposite());
			if (!Mc.solid(back.below()) && !Mc.solid(back)) return false;
			for (int y = 0; y < WALL_H; y++) {
				BlockPos w = back.above(y);
				if (!Mc.solid(w) && !Mc.free(w)) return false;
			}
			for (int f = 1; f <= 2; f++) {
				BlockPos p = col.relative(front, f);
				if (!Mc.free(p) || !Mc.free(p.above()) || !Mc.solid(p.below())) return false;
			}
		}
		return true;
	}

	private static boolean nearLava(BlockPos p) {
		for (Direction d : Direction.values()) if (Mc.id(Mc.state(p.relative(d)).getBlock()).equals("lava")) return true;
		return false;
	}

	private static boolean siteUsable(BlockPos o, Direction a) {
		for (int[] c : CAST) {
			BlockPos p = o.relative(a, c[0]).above(c[1]);
			if (!Mc.free(p) && !obsidian(p) && !lava(p)) return false;
		}
		return true;
	}

	@Override
	protected void cleanup() {
		if (fetch != null && fetch.result() == null) fetch.abort("stopped");
		if (Mc.mc().gameMode != null) Mc.mc().gameMode.stopDestroyBlock();
		super.cleanup();
	}
}
