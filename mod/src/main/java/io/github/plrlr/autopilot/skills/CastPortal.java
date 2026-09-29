package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
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
	private enum Phase {SITE, APPROACH, PREPARE, RESERVE_BUCKET, CARVE, DIGIN, WALL, NEXT, PILLAR, FETCH, WALK, LAVA, POUR, SCOOP, REFILL, BREAK, CLEAR, LIGHT}

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

	/** cast_portal (skills/CastPortalSite) made this site fit: cast here. */
	static void useSite(BlockPos o, Direction a) {
		origin = o.immutable();
		along = a;
	}

	/** Throwaway blocks needed for the wall and a small margin (Baritone may spend a few). */
	public static final int BLOCKS_NEEDED = 28;

	private Phase phase = Phase.SITE;
	private int wait, tries, wallTries;
	private BlockPos lastWall;
	private BlockPos target, water, breaking;
	private BlockPos plannedStand, lastTarget;
	private final List<BlockPos> blockedCastStands = new ArrayList<>();
	private CastGeometry.Aim lavaAim, waterAim;
	private BucketSkills.FillBucket fetch;
	private int castFails;
	/** Blocks placed under us to reach high frame blocks (a player builds up to reach). */
	private int pillars;
	private int approachTicks = 20 * 45;
	private int carveTries;
	private boolean dugIn;
	private PortalWorkArea.Site workArea;
	private int prepareFailures;
	private BlockPos spareWaterSpot;

	private record Plan(BlockPos stand, CastGeometry.Aim lava, CastGeometry.Aim water) {}
	/** A water bucket cannot carry lava; preserve one and empty a spare only with the gene. */
	enum BucketPlan {READY, EMPTY_SPARE, MISSING}

	static BucketPlan bucketPlan(int water, int empty, int lava, boolean reserveSpare) {
		if (water < 1) return BucketPlan.MISSING;
		if (empty + lava > 0) return BucketPlan.READY;
		return reserveSpare && water >= 2 ? BucketPlan.EMPTY_SPARE : BucketPlan.MISSING;
	}

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
			fail(Fail.WRONG_PLACE, "cast the portal in the overworld (water boils in the nether)");
			return;
		}
		if (Mc.count("flint_and_steel") == 0) {
			fail(Fail.NEED_ITEM, "need flint and steel");
			return;
		}
		if (Mc.count("water_bucket") == 0) {
			fail(Fail.NEED_ITEM, "need a water bucket");
			return;
		}
		switch (bucketPlan(Mc.count("water_bucket"), Mc.count("bucket"), Mc.count("lava_bucket"),
				Tune.on("portal.reserve_lava_bucket"))) {
			case READY -> {}
			case EMPTY_SPARE -> phase = Phase.RESERVE_BUCKET;
			case MISSING -> {
				fail(Fail.NEED_ITEM, "need a second bucket for lava");
				return;
			}
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
					fail(Fail.NOT_FOUND, "no known lava pool to cast from");
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
						if (!startPrepare(pl) && !startCarve(pl) && !startDigIn(pl)) fail(Fail.NO_ROOM, "no flat open ground for a portal near the lava");
						return;
					}
				}
				phase = Phase.WALL;
				wait = 0;
			}
			case APPROACH -> {
				if (++wait > approachTicks) {
					fail(Fail.UNREACHABLE, "couldn't get near the lava pool");
					return;
				}
				if (wait > 10 && !Bari.pathing()) {
					if (!findSite(pl.blockPosition())) {
						if (!startPrepare(pl) && !startCarve(pl) && !startDigIn(pl)) fail(Fail.NO_ROOM, "no flat open ground for a portal near the lava");
						return;
					}
					phase = Phase.WALL;
					wait = 0;
				}
			}
			case RESERVE_BUCKET -> reserveBucketTick(pl);
			case PREPARE -> prepareTick(pl);
			case CARVE -> {
				if (++wait > 20 * 120) {
					Bari.stop();
					fail(Fail.NO_ROOM, "couldn't dig out a room for the portal");
					return;
				}
				if (wait > 20 && !Bari.get().getBuilderProcess().isActive()) {
					if (!findSite(pl.blockPosition())) {
						if (!startCarve(pl)) fail(Fail.NO_ROOM, "dug a room but the portal still doesn't fit");
						return;
					}
					phase = Phase.WALL;
					wait = 0;
				}
			}
			case DIGIN -> {
				// Dug down into the ground beside the pool: now there's rock for a room.
				if (++wait > 20 * 40) {
					Bari.stop();
					fail(Fail.NO_ROOM, "couldn't dig down beside the lava");
					return;
				}
				if (wait > 20 && !Bari.pathing()) {
					if (!findSite(pl.blockPosition())) {
						if (!startCarve(pl)) fail(Fail.NO_ROOM, "dug in, but no room for the portal down here");
						return;
					}
					phase = Phase.WALL;
					wait = 0;
				}
			}
			case WALL -> wallTick(pl);
			case NEXT -> nextTick(pl);
			case PILLAR -> {
				// Jump and put a block underneath at the top of the jump, then look for an aim again.
				if (++wait > 20 * 4) {
					Mc.mc().options.keyJump.setDown(false);
					phase = Phase.NEXT;
					wait = 0;
					return;
				}
				if (!Mc.holdItem(io.github.plrlr.autopilot.Items2.matcher("throwaway"))) {
					Mc.mc().options.keyJump.setDown(false);
					castFailed(Fail.NEED_ITEM, "no blocks to build up with");
					return;
				}
				pl.setXRot(90f);
				Mc.mc().options.keyJump.setDown(true);
				BlockPos below = pl.blockPosition().below();
				if (!pl.onGround() && Mc.free(below) && Mc.clearOfPlayer(below) && Mc.placeAt(below)) {
					Mc.mc().options.keyJump.setDown(false);
					phase = Phase.NEXT;
					wait = 0;
				}
			}
			case REFILL -> {
				refill.update();
				if (refill.result() == null) return;
				if (!refill.result().ok()) {
					fail(refill.result().code(), "couldn't get water back: " + refill.result().detail());
					return;
				}
				if (obsidian(target)) {
					io.github.plrlr.autopilot.log.Checkpoints.mark("obsidian_placed");
					log("obsidian at " + target.toShortString() + "; water refilled");
					if (Tune.on("portal.retry_cast_view")) castFails = 0;
				}
				phase = Phase.NEXT;
			}
			case FETCH -> {
				fetch.update();
				if (fetch.result() == null) return;
				if (!fetch.result().ok()) {
					fail(fetch.result().code(), "couldn't fill a bucket with lava: " + fetch.result().detail());
					return;
				}
				phase = Phase.NEXT;
			}
			case WALK -> {
				if (++wait > 20 * 30) {
					castFailed(Fail.UNREACHABLE, "couldn't reach a spot to cast from");
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
				CastGeometry.Aim a = CastGeometry.aimAt(pl.getEyePosition(), target);
				CastGeometry.Aim w = water == null ? null : CastGeometry.aimAt(pl.getEyePosition(), water);
				if (a == null || w == null) {
					if (Tune.on("portal.retry_cast_view") && plannedStand != null && !blockedCastStands.contains(plannedStand)) {
						// Baritone can stop off center. Do not pick the same nominal stand on the next try.
						blockedCastStands.add(plannedStand);
					}
					castFailed(Fail.UNREACHABLE, "lost the line of sight to the frame");
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
						castFailed(Fail.USE_FAILED, "the lava didn't land in the frame");
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
					if (!obsidian(target)) castFailed(Fail.USE_FAILED, "the lava didn't harden");
					else {
						io.github.plrlr.autopilot.log.Checkpoints.mark("obsidian_placed");
						log("obsidian at " + target.toShortString() + "; water recovered");
						if (Tune.on("portal.retry_cast_view")) castFails = 0;
						phase = Phase.NEXT;
					}
					return;
				}
				if (wait % 5 != 0) return;
				if (wait > 20 * 20) {
					refillWater("the scoop timed out");
					return;
				}
				// The water sits in the spot we poured into; take it back for the next block.
				BlockPos src = water != null && BucketSkills.isSource(water, "water") ? water : findWaterSource();
				if (src == null) {
					log("no water source near the frame; target is " + Mc.id(Mc.state(target).getBlock()) + ", planned water spot has "
							+ Mc.id(Mc.state(water).getBlock()));
					refillWater("lost the poured water");
					return;
				}
				if (wait == 10 || wait == 40) log("scooping water at " + src.toShortString() + " from " + pl.blockPosition().toShortString()
						+ ", target is " + Mc.id(Mc.state(target).getBlock()));
				if (Bari.pathing()) return;
				// The new obsidian often stands between us and the water (batch 6): aim only at a
				// point the bucket's own ray really reaches, else walk to where one is.
				Vec3 aim = CastGeometry.scoopAim(pl.getEyePosition(), src);
				if (aim == null) {
					for (BlockPos s : BucketSkills.standSpots(src, Vec3.atCenterOf(src), src)) {
						if (CastGeometry.scoopAim(Vec3.atBottomCenterOf(s).add(0, 1.62, 0), src) != null) {
							Bari.path(new GoalBlock(s));
							return;
						}
					}
					refillWater("no spot sees the poured water");
					return;
				}
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
					fail(Fail.USE_FAILED, "couldn't clear a block out of the frame");
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
								fail(Fail.USE_FAILED, "water won't drain out of the frame");
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
					log("portal lit");
					done("cast and lit a nether portal");
					return;
				}
				if (wait++ % 10 != 0) return;
				if (++tries > 6) {
					fail(Fail.USE_FAILED, "couldn't light the portal");
					return;
				}
				if (!Mc.holdItem(s -> Items2.id(s).equals("flint_and_steel"))) {
					fail(Fail.NEED_ITEM, "lost the flint and steel");
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

	private boolean startPrepare(LocalPlayer pl) {
		if (!Tune.on("portal.prepare_work_area")) return false;
		// Reserve 24 blocks for the backing wall; repair only a few shallow holes.
		workArea = PortalWorkArea.choose(pl.blockPosition(), Mc.count("throwaway") - 24);
		if (workArea == null) return false;
		prepareFailures = 0;
		wait = 0;
		phase = Phase.PREPARE;
		log("preparing a dry portal floor at " + workArea.origin().toShortString());
		return true;
	}

	private void prepareTick(LocalPlayer pl) {
		if (++wait > 20 * 90) {
			fail(Fail.NO_ROOM, "couldn't prepare the portal work area");
			return;
		}
		BlockPos next = null;
		for (BlockPos p : workArea.floor()) if (!Mc.solid(p)) { next = p; break; }
		if (next == null) {
			if (!CastGeometry.fits(workArea.origin(), workArea.along(), WALL_H)) {
				fail(Fail.NO_ROOM, "prepared floor still has no usable portal site");
				return;
			}
			origin = workArea.origin();
			along = workArea.along();
			log("portal site ready at " + origin.toShortString());
			phase = Phase.WALL;
			wait = 0;
			return;
		}
		if (!Mc.free(next) || !Mc.solid(next.below()) || CastGeometry.nearLava(next)) {
			fail(Fail.NO_ROOM, "portal floor became unsafe");
			return;
		}
		if (pl.getEyePosition().distanceTo(Vec3.atCenterOf(next)) > Mc.reach() - 0.5) {
			if (!Bari.pathing()) Bari.path(new GoalNear(next, 2));
			return;
		}
		if (Bari.pathing()) Bari.stop();
		if (wait % 5 != 0) return;
		if (!Mc.holdItem(Items2.matcher("throwaway")) || !Mc.placeAt(next)) {
			if (++prepareFailures > 12) fail(Fail.PLACE_FAILED, "couldn't lay the portal floor");
		}
	}

	private void reserveBucketTick(LocalPlayer pl) {
		if (Mc.count("bucket") + Mc.count("lava_bucket") > 0) {
			spareWaterSpot = null;
			phase = Phase.SITE;
			wait = 0;
			return;
		}
		if (++wait > 20 * 8) {
			fail(Fail.NO_ROOM, "no safe spot to empty a spare water bucket");
			return;
		}
		if (spareWaterSpot == null) spareWaterSpot = safeWaterSpot(pl.blockPosition(), pl.getEyePosition());
		if (spareWaterSpot == null) {
			fail(Fail.NO_ROOM, "no safe visible spot for the spare water");
			return;
		}
		if (wait % 5 == 0 && Mc.holdItem(s -> Items2.id(s).equals("water_bucket")))
			Mc.placeAt(spareWaterSpot);
	}

	private static BlockPos safeWaterSpot(BlockPos feet, Vec3 eye) {
		for (int r = 1; r <= 2; r++) {
			for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
				BlockPos p = feet.offset(dx, 0, dz);
				if (!Mc.free(p) || !Mc.solid(p.below()) || !Mc.clearOfPlayer(p)
						|| eye.distanceTo(Vec3.atCenterOf(p)) > Mc.reach() - 0.5 || !Mc.canSee(p)) continue;
				boolean lavaNear = false;
				for (int x = -2; x <= 2 && !lavaNear; x++)
					for (int z = -2; z <= 2 && !lavaNear; z++)
						for (int y = -1; y <= 1; y++)
							if (Mc.id(Mc.state(p.offset(x, y, z)).getBlock()).equals("lava")) { lavaNear = true; break; }
				if (!lavaNear) return p.immutable();
			}
		}
		return null;
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
			log("backing wall ready at " + origin.toShortString());
			phase = Phase.NEXT;
			return;
		}
		BlockPos stand = cell(1, 0).relative(front());
		if (!pl.blockPosition().equals(stand)) {
			if (!Bari.pathing()) {
				if (++wallTries > 12) {
					fail(Fail.UNREACHABLE, "couldn't get in front of the portal site");
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
			fail(Fail.PLACE_FAILED, "couldn't build the wall behind the frame");
			return;
		}
		if (!Mc.holdItem(Items2.matcher("throwaway"))) {
			fail(Fail.NEED_ITEM, "out of blocks for the wall (need about " + BLOCKS_NEEDED + ")");
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
			io.github.plrlr.autopilot.log.Checkpoints.mark("frame_complete");
			log("frame complete at " + origin.toShortString());
			phase = Phase.CLEAR;
			wait = 0;
			return;
		}
		if (!target.equals(lastTarget)) {
			lastTarget = target;
			plannedStand = null;
			blockedCastStands.clear();
		}
		if (lava(target) && BucketSkills.isSource(target, "lava")) {
			// Our lava from an interrupted try: water finishes it.
			water = waterSpotFor(target, pl.getEyePosition());
			if (water != null) {
				waterAim = CastGeometry.aimAt(pl.getEyePosition(), water);
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
			if (++wait > 20 * 6) castFailed(Fail.USE_FAILED, "water won't drain from the frame");
			return;
		}
		if (Mc.count("water_bucket") == 0) {
			fail(Fail.NEED_ITEM, "lost the water bucket");
			return;
		}
		if (Mc.count("lava_bucket") == 0) {
			log("fetching lava for " + target.toShortString());
			fetch = new BucketSkills.FillBucket();
			fetch.begin(memory, "lava");
			phase = Phase.FETCH;
			return;
		}
		Plan plan = planFor(target, pl);
		if (plan == null && pillars < 2 && Mc.count("throwaway") > 0) {
			// The high blocks of the frame can be out of reach from the ground (seed a of the cast
			// test failed NO_ROOM 5 times): build up a block, like a player, and try again.
			pillars++;
			log("building up a block to reach " + target.toShortString());
			phase = Phase.PILLAR;
			wait = 0;
			return;
		}
		if (plan == null) {
			castFailed(Fail.NO_ROOM, "no spot with a clear aim at the frame");
			return;
		}
		water = plan.water().cell();
		plannedStand = plan.stand();
		log("cast " + target.toShortString() + " from " + plannedStand.toShortString() + ", water at " + water.toShortString());
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

	/**
	 * The poured water couldn't be taken back (batch 8, seed b): leave it and fill the bucket at
	 * the nearest water we know instead (the poured source itself or the pool), then go on.
	 */
	private void refillWater(String why) {
		log("refilling the water bucket elsewhere: " + why);
		refill = new BucketSkills.FillBucket();
		refill.begin(memory, "water");
		phase = Phase.REFILL;
	}

	private BucketSkills.FillBucket refill;

	private void castFailed(Fail code, String why) {
		log("failed: " + why);
		Bari.stop();
		if (++castFails > 6) {
			fail(code, why);
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
			if (CastGeometry.aimAt(eye, w) != null) return w;
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
			for (int f = 1; f <= 5; f++) {
				for (int y = -1; y <= 3; y++) {
					BlockPos s = origin.relative(along, x).relative(front(), f).above(y);
					if (Tune.on("portal.retry_cast_view") && blockedCastStands.contains(s)) continue;
					if (!BucketSkills.standable(s)) continue;
					Vec3 eye = Vec3.atBottomCenterOf(s).add(0, 1.62, 0);
					if (eye.distanceTo(Vec3.atCenterOf(t)) > Mc.reach() + 0.5) continue;
					CastGeometry.Aim a = CastGeometry.aimAt(eye, t);
					if (a == null) continue;
					for (BlockPos w : waters) {
						if (w.equals(s) || w.equals(s.above())) continue;
						// After pouring, the lava spot is obsidian: we must still see the water to
						// scoop it back, so the line to it can't cross the lava spot.
						if (!CastGeometry.clearOf(eye, w, t)) continue;
						CastGeometry.Aim b = CastGeometry.aimAt(eye, w);
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
	 * Underground there's never flat open ground for the frame (batch 14's deep run found lava in
	 * 50 s, then failed NO_ROOM 11 times): dig a room instead, 4 wide, 3 deep and as tall as the
	 * wall, with us standing in its front row. The back row stays rock (the wall), and no room
	 * cell may touch lava or water, which would flow in. Baritone's builder clears it by mining.
	 */
	private boolean startCarve(LocalPlayer pl) {
		if (pl.level().canSeeSky(pl.blockPosition().above()) || carveTries >= 2) return false;
		BlockPos feet = pl.blockPosition();
		for (Direction a : new Direction[]{Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH}) {
			Direction front = a.getClockWise();
			BlockPos o = feet.relative(a, -1).relative(front, -2);
			if (!carvable(o, a)) continue;
			carveTries++;
			Bari.stop();
			Bari.get().getBuilderProcess().clearArea(o, o.relative(a, 3).relative(front, 2).above(WALL_H - 1));
			phase = Phase.CARVE;
			wait = 0;
			return true;
		}
		return false;
	}

	/**
	 * On the surface with no flat spot (generation 4: build_portal NO_ROOM x15, lava pools on uneven
	 * ground): dig 4 blocks down right here, as a player would, and carve the room from there.
	 */
	private boolean startDigIn(LocalPlayer pl) {
		if (dugIn || !pl.level().canSeeSky(pl.blockPosition().above())) return false;
		BlockPos feet = pl.blockPosition();
		for (int dy = 1; dy <= 5; dy++) {
			if (!Mc.state(feet.below(dy)).getFluidState().isEmpty()) return false; // water or lava below
		}
		dugIn = true;
		Bari.path(new GoalBlock(feet.getX(), feet.getY() - 4, feet.getZ()));
		phase = Phase.DIGIN;
		wait = 0;
		return true;
	}

	private static boolean carvable(BlockPos o, Direction a) {
		Direction front = a.getClockWise();
		for (int x = 0; x < 4; x++) {
			for (int f = 0; f < 3; f++) {
				BlockPos col = o.relative(a, x).relative(front, f);
				if (!Mc.solid(col.below()) || Mc.state(col.below()).liquid()) return false;
				for (int y = 0; y < WALL_H; y++) {
					BlockPos p = col.above(y);
					if (!Mc.state(p).getFluidState().isEmpty() || CastGeometry.nearLava(p) || nearWater(p)) return false;
				}
			}
			BlockPos back = o.relative(a, x).relative(front.getOpposite());
			for (int y = 0; y < WALL_H; y++) if (!Mc.solid(back.above(y))) return false;
		}
		return true;
	}

	private static boolean nearWater(BlockPos p) {
		for (Direction d : Direction.values()) if (Mc.id(Mc.state(p.relative(d)).getBlock()).equals("water")) return true;
		return false;
	}

	private static boolean findSite(BlockPos feet) {
		for (int r = 2; r <= 16; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					for (int dy = -3; dy <= 3; dy++) {
						BlockPos o = feet.offset(dx, dy, dz);
						for (Direction a : new Direction[]{Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH}) {
							if (CastGeometry.fits(o, a, WALL_H)) {
								origin = o.immutable();
								along = a;
								log("portal site ready at " + origin.toShortString());
								return true;
							}
						}
					}
				}
			}
		}
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
		if (fetch != null && fetch.result() == null) fetch.abort(Fail.INTERRUPTED, "stopped");
		if (refill != null && refill.result() == null) refill.abort(Fail.INTERRUPTED, "stopped");
		if (Mc.mc().gameMode != null) Mc.mc().gameMode.stopDestroyBlock();
		super.cleanup();
	}
}
