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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bucket work, done the way a player does it: look at the spot and right-click. The server
 * traces the bucket from where the player looks, so every use first checks our own line of
 * sight lands on the intended block face.
 */
public final class BucketSkills {
	private BucketSkills() {}

	static boolean isSource(BlockPos p, String fluid) {
		if (!Mc.canSee(p)) return false;
		BlockState st = Mc.state(p);
		return Mc.id(st.getBlock()).equals(fluid) && st.getFluidState().isSource();
	}

	/** Where a ray from the eye to `aim` first hits a block (fluids ignored or sources only). */
	static BlockHitResult trace(Vec3 aim, ClipContext.Fluid fluid) {
		LocalPlayer pl = Mc.player();
		return pl.level().clip(new ClipContext(pl.getEyePosition(), aim, ClipContext.Block.OUTLINE, fluid, pl));
	}

	/** A spot to stand on: free for feet and head, solid below, not in or next to lava. */
	static boolean standable(BlockPos p) {
		return standable(p, new FairProbe());
	}

	static boolean standable(BlockPos p, FairProbe seen) {
		return seen.free(p) && seen.free(p.above()) && seen.visible(p.below()) && seen.solid(p.below())
				&& !seen.lavaNear(p);
	}

	/** Standing spots within 3 blocks of `near`, from which the eye is within reach of `aim`. */
	static List<BlockPos> standSpots(BlockPos near, Vec3 aim, BlockPos exclude) {
		List<BlockPos> out = new ArrayList<>();
		FairProbe seen = new FairProbe();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					BlockPos p = near.offset(dx, dy, dz);
					if (p.equals(exclude) || !standable(p, seen)) continue;
					// Eye height is about 1.62 above the feet.
					if (Vec3.atBottomCenterOf(p).add(0, 1.62, 0).distanceTo(aim) <= Mc.reach() - 0.5) out.add(p);
				}
			}
		}
		out.sort(Comparator.comparingDouble(p -> p.distSqr(Mc.player().blockPosition())));
		return out;
	}

	/**
	 * fill_bucket water|lava: fill an empty bucket at the nearest known source of that fluid,
	 * standing on a bank that isn't next to lava.
	 */
	public static final class FillBucket extends Skill {
		private String fluid;
		private BlockPos source;
		private int tries;
		private int before;
		private final List<BlockPos> skipped = new ArrayList<>();

		@Override
		public String name() {
			return "fill_bucket";
		}

		@Override
		public boolean interruptible() {
			return false;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 90;
			fluid = "lava".equals(arg) ? "lava" : "water";
			if (Mc.count("bucket") == 0) {
				fail(Fail.NEED_ITEM, "no empty bucket");
				return;
			}
			before = Mc.count(fluid + "_bucket");
			pickNext();
		}

		private void pickNext() {
			LocalPlayer pl = Mc.player();
			source = null;
			List<WorldMemory.Seen> all = new ArrayList<>(memory.all(fluid));
			all.sort(Comparator.comparingDouble(s -> s.pos().distSqr(pl.blockPosition())));
			int checked = 0;
			for (WorldMemory.Seen s : all) {
				// A source with air above can be scooped from the bank. Only the nearest few: a big
				// lava lake remembers hundreds of blocks and each check scans stand spots.
				if (skipped.contains(s.pos())) continue;
				if (++checked > 24) break;
				if (!Mc.canSee(s.pos())) {
					// Walk toward remembered water/lava, then validate it with the bucket ray on arrival.
					source = s.pos();
					Bari.path(new GoalNear(source, 3));
					return;
				}
				if (isSource(s.pos(), fluid) && Mc.free(s.pos().above()) && !standSpots(s.pos(), Vec3.atCenterOf(s.pos()), s.pos()).isEmpty()) {
					source = s.pos();
					break;
				}
				// No use to a bucket (flowing, covered, no bank): forget it, so explore looks
				// further instead of "already seeing" water we can't take.
				memory.forget(fluid, s.pos());
			}
			if (source == null) {
				fail(Fail.NOT_FOUND, "no known " + fluid + " source with a bank to stand on");
				return;
			}
			List<BlockPos> spots = standSpots(source, Vec3.atCenterOf(source), source);
			Bari.path(new GoalBlock(spots.get(0)));
		}

		@Override
		protected void tick() {
			if (Mc.count(fluid + "_bucket") > before) {
				done("filled a bucket with " + fluid);
				return;
			}
			if (source == null || Bari.pathing() || ticks % 10 != 0) return;
			LocalPlayer pl = Mc.player();
			Vec3 aim = Vec3.atCenterOf(source).add(0, 0.3, 0);
			if (pl.getEyePosition().distanceTo(aim) > Mc.reach()) {
				skipped.add(source);
				if (++tries > 6) fail(Fail.UNREACHABLE, "couldn't get next to the " + fluid);
				else pickNext();
				return;
			}
			Mc.holdItem(s -> Items2.id(s).equals("bucket"));
			Mc.lookAt(aim);
			BlockHitResult hit = trace(aim.add(aim.subtract(pl.getEyePosition()).normalize()), ClipContext.Fluid.SOURCE_ONLY);
			if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(source)) {
				memory.forget(fluid, source);
				if (++tries > 6) fail(Fail.UNREACHABLE, "the " + fluid + " is out of sight");
				else pickNext();
				return;
			}
			boolean accepted = Mc.useItem();
			io.github.plrlr.autopilot.AutopilotMod.LOGGER.info("[bucket] fill {} at {} from {} hand={} accepted={} dist={}", fluid,
					source.toShortString(), pl.blockPosition().toShortString(), Items2.id(pl.getMainHandItem()), accepted,
					String.format("%.1f", pl.getEyePosition().distanceTo(aim)));
			if (++tries > 8) fail(Fail.USE_FAILED, "the bucket didn't fill");
		}
	}

	/**
	 * make_obsidian n: turn lava pool sources into obsidian. Water poured on the ground next to a
	 * lava source flows onto it and hardens it; then the water is scooped back up. The obsidian
	 * is mined afterwards by collect (it needs a diamond pickaxe).
	 */
	public static final class MakeObsidian extends Skill {
		private enum Phase {PICK, WALK, POUR, WAIT, SCOOP}

		private Phase phase = Phase.PICK;
		private int want;
		private BlockPos lava, pourAt;
		private int wait, fails;
		private final List<BlockPos> skipped = new ArrayList<>();

		@Override
		public String name() {
			return "make_obsidian";
		}

		@Override
		public boolean interruptible() {
			return false;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 240;
			want = argCount(10);
			if (Mc.count("water_bucket") == 0) {
				fail(Fail.NEED_ITEM, "need a water bucket");
				return;
			}
			if (!Mc.dimension().equals("overworld")) {
				fail(Fail.WRONG_PLACE, "water evaporates outside the overworld");
			}
		}

		/** Obsidian blocks around here that we could mine. */
		private int obsidianNearby() {
			BlockPos c = Mc.player().blockPosition();
			int n = 0;
			FairProbe seen = new FairProbe();
			for (WorldMemory.Seen block : memory.all("obsidian")) {
				BlockPos p = block.pos();
				if (Math.abs(p.getX() - c.getX()) > 8 || Math.abs(p.getY() - c.getY()) > 4
						|| Math.abs(p.getZ() - c.getZ()) > 8) continue;
				if (seen.visible(p) && Mc.id(Mc.state(p).getBlock()).equals("obsidian")) n++;
			}
			return n;
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			switch (phase) {
				case PICK -> {
					if (Mc.count("obsidian") + obsidianNearby() >= want) {
						done("made enough obsidian to mine");
						return;
					}
					if (Mc.count("water_bucket") == 0) {
						fail(Fail.NEED_ITEM, "lost the water bucket");
						return;
					}
					if (!pickLava()) {
						if (obsidianNearby() > 0) done("made " + obsidianNearby() + " obsidian (no more reachable lava here)");
						else fail(Fail.NOT_FOUND, "no lava source we can safely pour water next to");
						return;
					}
					phase = Phase.WALK;
					wait = 0;
				}
				case WALK -> {
					if (++wait > 20 * 40) {
						skip("took too long to reach");
						return;
					}
					if (wait > 10 && !Bari.pathing()) {
						Vec3 aim = aimFor(pourAt);
						if (pl.getEyePosition().distanceTo(aim) <= Mc.reach()) {
							phase = Phase.POUR;
							wait = 0;
						} else skip("couldn't get in reach");
					}
				}
				case POUR -> {
					if (wait++ % 5 != 0) return;
					if (!isSource(lava, "lava")) {
						phase = Phase.PICK; // already hardened by earlier water
						return;
					}
					if (!Mc.free(pourAt)) {
						skip("the pouring spot got filled");
						return;
					}
					Vec3 aim = aimFor(pourAt);
					BlockHitResult hit = trace(aim.add(0, -0.3, 0), ClipContext.Fluid.NONE);
					if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pourAt.below()) || hit.getDirection() != Direction.UP) {
						skip("can't see the pouring spot");
						return;
					}
					Mc.holdItem(s -> Items2.id(s).equals("water_bucket"));
					Mc.lookAt(aim);
					Mc.useItem();
					phase = Phase.WAIT;
					wait = 0;
				}
				case WAIT -> {
					// Water needs a moment to flow onto the lava.
					if (++wait < 30) return;
					phase = Phase.SCOOP;
					wait = 0;
				}
				case SCOOP -> {
					if (Mc.count("water_bucket") > 0) {
						phase = Phase.PICK;
						return;
					}
					if (wait++ % 5 != 0) return;
					if (wait > 60) {
						fail(Fail.USE_FAILED, "couldn't scoop the water back up");
						return;
					}
					Vec3 aim = Vec3.atCenterOf(pourAt).add(0, 0.3, 0);
					Mc.holdItem(s -> Items2.id(s).equals("bucket"));
					Mc.lookAt(aim);
					Mc.useItem();
				}
			}
		}

		/** Aim at the top face of the block under the pouring spot. */
		private static Vec3 aimFor(BlockPos pourAt) {
			return new Vec3(pourAt.getX() + 0.5, pourAt.getY() + 0.02, pourAt.getZ() + 0.5);
		}

		private void skip(String why) {
			if (lava != null) skipped.add(lava);
			Bari.stop();
			if (++fails > 8) fail(Fail.USE_FAILED, "gave up on this lava pool: " + why);
			else phase = Phase.PICK;
		}

		/** The nearest lava source with an open, solid-floored spot beside it that we can reach. */
		private boolean pickLava() {
			LocalPlayer pl = Mc.player();
			List<WorldMemory.Seen> all = new ArrayList<>(memory.all("lava"));
			all.sort(Comparator.comparingDouble(s -> s.pos().distSqr(pl.blockPosition())));
			// Only the few nearest sources: each check scans stand spots, and doing it for hundreds
			// of remembered lava blocks in one tick would freeze the game.
			int checked = 0;
			for (WorldMemory.Seen s : all) {
				BlockPos l = s.pos();
				if (skipped.contains(l) || l.distSqr(pl.blockPosition()) > 64 * 64 || !isSource(l, "lava")) continue;
				if (++checked > 12) break;
				// Pour beside the lava at its own level, or (for a pool sunk into the ground, the
				// usual case) on the rim one block up, so the water spills over and falls onto it.
				List<BlockPos> pours = new ArrayList<>();
				for (Direction d : Direction.Plane.HORIZONTAL) pours.add(l.relative(d));
				if (Mc.free(l.above())) for (Direction d : Direction.Plane.HORIZONTAL) pours.add(l.above().relative(d));
				for (BlockPos n : pours) {
					if (!Mc.free(n) || !Mc.solid(n.below()) || Mc.state(n.below()).liquid()) continue;
					List<BlockPos> spots = standSpots(n, aimFor(n), n);
					if (spots.isEmpty()) continue;
					lava = l;
					pourAt = n;
					Bari.path(new GoalBlock(spots.get(0)));
					return true;
				}
			}
			return false;
		}
	}
}
