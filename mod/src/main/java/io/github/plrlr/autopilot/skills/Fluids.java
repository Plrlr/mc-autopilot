package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.plan.Option;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wave 6 (fluids): the bot walked away from water and lava; a player closes them off. From the
 * first all-skills trial (2026-09-29): exploring gave up three times at "open water ahead", the
 * drowning reflex fired three times in 25 s, and an iron trip sat 88 s stuck in a flooded tunnel
 * at y 16. Lava killed 11 times in generations 41-46.
 *
 *   fluid_seal    block the water or lava sources near us (fireproof blocks for lava)
 *   lava_guard    reflex while digging: lava appears beside feet, head or the block ahead: cover it
 *   fluid_cross   water or lava in the way: bridge it (narrow), boat it (wide water), else swim
 *   drain_tunnel  flooded underground: seal the sources, fill the flowing cells, carry on
 */
public final class Fluids {
	private Fluids() {}

	static boolean isLava(BlockPos p) {
		return Mc.state(p).getFluidState().is(FluidTags.LAVA);
	}

	static boolean isWater(BlockPos p) {
		return Mc.state(p).getFluidState().is(FluidTags.WATER);
	}

	static boolean isFluid(BlockPos p) {
		return !Mc.state(p).getFluidState().isEmpty();
	}

	static boolean isSource(BlockPos p) {
		FluidState f = Mc.state(p).getFluidState();
		return !f.isEmpty() && f.isSource();
	}

	/**
	 * Hold a block that won't burn. Every throwaway (dirt, cobblestone, deepslate, netherrack,
	 * stone kinds, blackstone) is fireproof; planks, logs and wool are not, so they're never used
	 * against lava, and neither are sand and gravel (they fall).
	 */
	static boolean holdFireproof() {
		return Mc.holdItem(Items2.matcher("throwaway"));
	}

	/** Places a fireproof block into p (air or a fluid cell, not our own body). True if the click took. */
	static boolean fill(BlockPos p) {
		var st = Mc.state(p);
		boolean open = st.isAir() || st.canBeReplaced() || !st.getFluidState().isEmpty();
		if (!open || !Mc.clearOfPlayer(p) || !holdFireproof()) return false;
		if (Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(p)) > Mc.reach() - 0.3) return false;
		return Mc.placeAt(p);
	}

	/** Fluid cells within r of `at` (the same fluid kind, or both when kind is null), sources first, nearest first. */
	static List<BlockPos> fluidCells(BlockPos at, int r, String kind) {
		List<BlockPos> out = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++)
			for (int dy = -r; dy <= r; dy++)
				for (int dz = -r; dz <= r; dz++) {
					BlockPos p = at.offset(dx, dy, dz);
					if (!isFluid(p)) continue;
					if ("lava".equals(kind) && !isLava(p) || "water".equals(kind) && !isWater(p)) continue;
					if (!Mc.canSee(p) && p.distSqr(at) > 4) continue; // seen fluids only (fair play)
					out.add(p.immutable());
				}
		out.sort(Comparator.comparing((BlockPos p) -> !isSource(p)).thenComparingDouble(p -> p.distSqr(at)));
		return out;
	}

	// ------------------------------------------------------------------ 41 fluid_seal

	/**
	 * fluid_seal [water|lava]: block the fluid sources (then flows) within 5 blocks, from where we
	 * stand, so nothing floods or burns our work spot. A source is a still block: blocking it stops
	 * everything downstream. Ends when none in reach remain, or when out of blocks.
	 */
	public static final class Seal extends Skill {
		private String kind;
		private int placed, misses;

		@Override
		public String name() {
			return "fluid_seal";
		}

		@Override
		public boolean workingInPlace() {
			return true;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 30;
			kind = arg == null || arg.isBlank() ? null : argName();
			if (Mc.count(Items2.matcher("throwaway")) == 0) fail(Fail.NEED_ITEM, "no blocks to seal with");
			else Bari.stop();
		}

		@Override
		protected void tick() {
			if (ticks % 3 != 0) return;
			BlockPos feet = Mc.player().blockPosition();
			List<BlockPos> cells = fluidCells(feet, 4, kind);
			cells.removeIf(p -> Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(p)) > Mc.reach() - 0.3 || !Mc.clearOfPlayer(p));
			if (cells.isEmpty()) {
				Facts.report("fluid_sealed");
				done("sealed " + placed + " fluid blocks");
				return;
			}
			if (Mc.count(Items2.matcher("throwaway")) == 0) {
				fail(Fail.NEED_ITEM, "ran out of blocks after " + placed);
				return;
			}
			if (fill(cells.get(0))) placed++;
			else if (++misses > 12) fail(Fail.PLACE_FAILED, "couldn't place into the fluid");
		}
	}

	// ------------------------------------------------------------------ 42 lava_guard

	/**
	 * lava_guard: the digging reflex. Autopilot calls guard() every few ticks with the gene on while
	 * a digging skill runs; lava in any cell touching our feet or head, or behind the block we're
	 * breaking, gets a fireproof block at once. A player covers lava the moment they see it.
	 */
	public static final class LavaGuard extends Skill {
		@Override
		public String name() {
			return "lava_guard";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 10;
		}

		@Override
		protected void tick() {
			if (!guard()) done("no lava beside us");
		}

		/** One check. True if it placed a block (the caller skips its other reflexes this tick). */
		public static boolean guard() {
			LocalPlayer pl = Mc.player();
			if (pl == null || pl.isInLava() || Mc.count(Items2.matcher("throwaway")) == 0) return false;
			BlockPos feet = pl.blockPosition();
			List<BlockPos> watch = new ArrayList<>();
			for (Direction d : Direction.values()) {
				watch.add(feet.relative(d));
				watch.add(feet.above().relative(d));
			}
			// The block being mined: lava right behind it pours in when it breaks.
			if (Mc.mc().hitResult instanceof net.minecraft.world.phys.BlockHitResult bh) {
				BlockPos mined = bh.getBlockPos();
				for (Direction d : Direction.values()) watch.add(mined.relative(d));
			}
			for (BlockPos p : watch) {
				if (isLava(p) && Mc.canSee(p) || isLava(p) && p.distSqr(feet) <= 2) {
					if (fill(p)) return true;
				}
			}
			return false;
		}
	}

	// ------------------------------------------------------------------ 43 fluid_cross

	/**
	 * fluid_cross &lt;x z&gt;: water or lava between us and a point. Measure the stretch along the way
	 * (seen fluid cells on the line), then: up to 16 blocks, bridge it (a straight line of blocks,
	 * crouched at the edge, a rail on the lava side); wider water with a boat, boat it; wider water
	 * without, swim it (Baritone, sprint-swimming); wider lava: no.
	 */
	public static final class Cross extends Composite {
		private BlockPos target;
		private boolean tried;

		@Override
		public String name() {
			return "fluid_cross";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 150;
			maxChildFails = 1;
			String[] a = arg == null ? new String[0] : arg.trim().split("\\s+");
			if (a.length != 2) {
				fail(Fail.ERROR, "fluid_cross needs x z");
				return;
			}
			target = new BlockPos(Integer.parseInt(a[0]), Mc.player().getBlockY(), Integer.parseInt(a[1]));
		}

		/** Fluid cells on the straight line to the target at our feet level, and whether any is lava. */
		static int[] stretch(BlockPos from, BlockPos to) {
			int n = 0, lava = 0;
			double len = Math.sqrt(from.distSqr(to));
			for (int i = 1; i <= Math.min(64, (int) len); i++) {
				double f = i / len;
				BlockPos p = new BlockPos((int) Math.floor(from.getX() + (to.getX() - from.getX()) * f), from.getY() - 1,
						(int) Math.floor(from.getZ() + (to.getZ() - from.getZ()) * f));
				if (isFluid(p) || isFluid(p.above())) {
					n++;
					if (isLava(p) || isLava(p.above())) lava++;
				}
			}
			return new int[]{n, lava};
		}

		@Override
		protected Option next() {
			if (tried) return null;
			tried = true;
			int[] s = stretch(Mc.player().blockPosition(), target);
			String xz = target.getX() + " " + target.getZ();
			if (s[0] == 0) return null;
			if (s[0] <= 16 && Mc.count(Items2.matcher("throwaway")) >= s[0] + 4)
				return new Option("nether_bridge", xz, "bridge " + s[0] + " blocks of " + (s[1] > 0 ? "lava" : "water"));
			if (s[1] > 0) {
				fail(Fail.HAZARD, "too much lava to cross (" + s[0] + " blocks)");
				return null;
			}
			if (Mc.count(Items2.matcher("boat")) > 0) return new Option("boat_cross", xz, "boat across " + s[0] + " blocks of water");
			if (Mc.count(Items2.matcher("planks")) >= 5) return new Option("craft", "boat:1", "a boat for the water ahead");
			return new Option("goto", "xz " + xz, "swim across");
		}

		@Override
		protected void finish() {
			Skill.Result r = lastResult();
			if (r == null || r.ok()) {
				Facts.report("crossed");
				done(r == null ? "nothing to cross" : "crossed: " + r.detail());
			} else fail(r.code() == null ? Fail.NO_PROGRESS : r.code(), r.detail());
		}
	}

	// ------------------------------------------------------------------ 44 drain_tunnel

	/**
	 * drain_tunnel: underground and in water (a mine flooded from an aquifer or a spring): seal the
	 * water's sources in reach, then fill the flowing cells at our feet and head level around us,
	 * so the tunnel is dry to work and walk in again.
	 */
	public static final class DrainTunnel extends Composite {
		private boolean sealed;
		private int fills, misses;

		@Override
		public String name() {
			return "drain_tunnel";
		}

		@Override
		public boolean workingInPlace() {
			return true;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60;
			maxChildFails = 1;
			if (Mc.count(Items2.matcher("throwaway")) < 4) fail(Fail.NEED_ITEM, "need blocks to drain a tunnel");
		}

		@Override
		protected boolean ownTick() {
			if (!sealed) return false;
			if (ticks % 3 != 0) return true;
			BlockPos feet = Mc.player().blockPosition();
			for (BlockPos p : fluidCells(feet, 2, "water")) {
				if (p.equals(feet) || p.equals(feet.above())) continue; // our own cells drain once sealed
				if (fill(p)) {
					fills++;
					return true;
				}
			}
			if (fluidCells(feet, 2, "water").stream().noneMatch(p -> !p.equals(feet) && !p.equals(feet.above())) || ++misses > 20) {
				Facts.report("tunnel_dry");
				done("drained: " + fills + " cells filled");
			}
			return true;
		}

		@Override
		protected Option next() {
			if (sealed) return WAIT;
			sealed = true;
			return new Option("fluid_seal", "water", "block the water's sources first");
		}

		@Override
		protected void finish() {
			done("drained");
		}
	}

	/** For the planner: underground, in water, and blocks to work with: drain_tunnel fits. */
	public static boolean floodedTunnel() {
		LocalPlayer pl = Mc.player();
		return pl != null && pl.isInWater() && !pl.level().canSeeSky(pl.blockPosition().above())
				&& Mc.count(Items2.matcher("throwaway")) >= 4 && Mc.dimension().equals("overworld");
	}

	/** Where "keep going this way" points, 40 blocks ahead along our view (for fluid_cross after explore turned back). */
	public static String aheadXZ() {
		LocalPlayer pl = Mc.player();
		Vec3 d = pl.getViewVector(1f).multiply(1, 0, 1).normalize().scale(40);
		return (int) (pl.getX() + d.x) + " " + (int) (pl.getZ() + d.z);
	}

}
