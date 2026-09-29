package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Keeping a spare kit in a chest (skills 19 and 20). One death drops everything, and a lava start
 * that died once went back to punching trees (generations 41-46). A player keeps spares at base.
 */
public final class ChestSkills {
	private ChestSkills() {}

	/** The spares worth keeping (never the tools in use: those stay in the hotbar). */
	static final Set<String> SPARES = Set.of("iron_ingot", "raw_iron", "coal", "bucket", "flint", "flint_and_steel",
			"cooked_beef", "cooked_porkchop", "cooked_mutton", "cooked_chicken", "bread", "diamond", "obsidian",
			"gold_ingot", "ender_pearl", "blaze_rod", "string", "arrow");

	/** Base class: find or place a chest (Station), open it, then move one stack per tick. */
	abstract static class ChestJob extends Skill {
		private Station station;
		private int moves, idle;
		BlockPos preferredChest() { return null; }
		void finished(BlockPos at, int moved) {}

		/** One move with the open chest; false when there's nothing more to move. */
		abstract boolean move(ChestMenu m);

		abstract String what();

		@Override
		protected void start() {
			timeoutTicks = 20 * 90;
			station = new Station("chest", ChestMenu.class, memory, preferredChest());
		}

		@Override
		protected void tick() {
			if (!station.ready()) {
				station.tick();
				if (station.error != null) fail(station.errorCode, station.error);
				return;
			}
			ChestMenu m = (ChestMenu) Mc.player().containerMenu;
			if (ticks % 3 != 0) return;
			if (move(m)) {
				moves++;
				idle = 0;
				return;
			}
			if (++idle < 3) return; // let the last clicks settle
			Mc.player().closeContainer();
			BlockPos at = station.pos();
			if (at != null) memory.remember("chest", at, "chest");
			Facts.report("chest_known");
			if (moves == 0 && this instanceof Restock && io.github.plrlr.autopilot.Tune.on("skill.restock_stash_only")) {
				fail(Fail.NOT_FOUND, "no wanted spares in our stash");
				return;
			}
			finished(at, moves);
			done(what() + ": " + moves + " stacks");
		}

		@Override
		protected void cleanup() {
			var pl = Mc.player();
			if (pl != null && pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
			super.cleanup();
		}
	}

	/** stash: put the spares into a chest at base (placing one if we carry it), keeping a working set. */
	public static final class Stash extends ChestJob {
		private static BlockPos stashedAt;
		private static String stashedDim;
		public static void forget() { stashedAt = null; stashedDim = null; }
		public static BlockPos position() {
			return Mc.dimension().equals(stashedDim) ? stashedAt : null;
		}
		@Override void finished(BlockPos at, int moved) {
			if (moved > 0 && at != null) { stashedAt = at.immutable(); stashedDim = Mc.dimension(); }
		}
		@Override
		public String name() {
			return "stash";
		}

		@Override
		String what() {
			return "stashed spares";
		}

		/** Keep this many of each spare on us; the rest goes in the chest. */
		static int keep(String id) {
			return switch (id) {
				case "bucket", "flint_and_steel" -> 1;
				case "cooked_beef", "cooked_porkchop", "cooked_mutton", "cooked_chicken", "bread" -> 8;
				case "coal" -> 8;
				case "iron_ingot" -> 3;
				default -> 0;
			};
		}

		@Override
		boolean move(ChestMenu m) {
			// Only stacks beyond what we keep: count per item, put a stack while over the keep line.
			for (String id : SPARES) {
				int have = Mc.count(id);
				if (have <= keep(id)) continue;
				Predicate<ItemStack> p = s -> Items2.id(s).equals(id);
				if (have - keep(id) >= smallestStack(p) && ChestOps.putOne(m, p)) return true;
			}
			return false;
		}

		private static int smallestStack(Predicate<ItemStack> p) {
			int min = Integer.MAX_VALUE;
			for (int i = 9; i < 36; i++) {
				ItemStack s = Mc.player().getInventory().getItem(i);
				if (!s.isEmpty() && p.test(s)) min = Math.min(min, s.getCount());
			}
			return min;
		}
	}

	/** restock[:items]: take spares back from our chest (the listed items, or every spare). */
	public static final class Restock extends ChestJob {
		private Set<String> want;

		@Override
		public String name() {
			return "restock";
		}

		@Override
		String what() {
			return "restocked";
		}

		@Override
		protected void start() {
			if (io.github.plrlr.autopilot.Tune.on("skill.restock_stash_only") && Stash.position() == null) {
				fail(Fail.NOT_FOUND, "no stash chest known");
				return;
			}
			if (!io.github.plrlr.autopilot.Tune.on("skill.restock_stash_only") && memory.nearest("chest") == null) {
				fail(Fail.NOT_FOUND, "no chest of ours known");
				return;
			}
			want = arg == null || arg.isBlank() ? SPARES : Set.of(arg.split("[,:]"));
			super.start();
		}

		@Override BlockPos preferredChest() {
			return io.github.plrlr.autopilot.Tune.on("skill.restock_stash_only") ? Stash.position() : null;
		}

		@Override
		boolean move(ChestMenu m) {
			return ChestOps.takeOne(m, s -> want.contains(Items2.id(s)));
		}
	}

	/** For the planner: worth stashing (a chest carried or known, and real spares to put away). */
	public static boolean stashWorthIt() {
		int spare = 0;
		for (String id : SPARES) spare += Math.max(0, Mc.count(id) - Stash.keep(id));
		return spare >= 8 && Mc.dimension().equals("overworld");
	}
}
