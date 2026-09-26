package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalXZ;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.List;

/** Skills that are mostly "walk somewhere with Baritone". */
public final class MoveSkills {
	private MoveSkills() {}

	/**
	 * explore <what to look for>: walk up to ~120 blocks into new ground and stop as soon as the
	 * thing we need comes into view. The argument is a comma list of mob types or remembered
	 * block groups ("cow,pig", "log", "nether_bricks,blaze"), or "any". The direction comes from
	 * WorldMemory: keep heading the same way while the ground is new, turn away from water.
	 */
	public static final class Explore extends Skill {
		private static final int DIST = 120;
		private List<String> targets = List.of();
		private int waterTicks;
		private double startX, startZ;

		@Override
		public String name() {
			return "explore";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 100;
			LocalPlayer pl = Mc.player();
			String a = arg == null ? "any" : arg;
			if (!a.equals("any")) targets = List.of(a.split(","));
			String already = found();
			if (already != null) {
				// Nothing to explore for: whatever needed this should use what's in view instead.
				fail("already see " + already);
				return;
			}
			startX = pl.getX();
			startZ = pl.getZ();
			int h = memory.exploreHeading(startX, startZ, DIST);
			double angle = h * Math.PI / 4;
			Bari.path(new GoalXZ((int) (startX + Math.cos(angle) * DIST), (int) (startZ + Math.sin(angle) * DIST)));
		}

		/** The first target that's in view now, or null. */
		private String found() {
			if (targets.isEmpty()) return null;
			// Same range the planner looks in: if explore "sees" a cow the planner can't, they'd
			// hand the job back and forth forever.
			Perception seen = Perception.look(32);
			LocalPlayer pl = Mc.player();
			for (String t : targets) {
				if (seen.nearest(t) != null) return t;
				WorldMemory.Seen b = memory.nearest(t);
				if (b != null && b.pos().distSqr(pl.blockPosition()) < 48 * 48) return t;
			}
			return null;
		}

		@Override
		protected void tick() {
			if (ticks % 10 != 0) return;
			LocalPlayer pl = Mc.player();
			String f = found();
			if (f != null) {
				done("found " + f);
				return;
			}
			// Swimming across an ocean finds nothing we need and invites drowned; turn around.
			if (pl.isInWater()) waterTicks += 10;
			else waterTicks = Math.max(0, waterTicks - 5);
			// Rivers take a few seconds to cross; only a long swim means open sea.
			if (waterTicks > 20 * 20) {
				memory.markBadAhead(startX, startZ, DIST);
				fail("open water ahead; will turn");
				return;
			}
			if (ticks > 20 && !Bari.pathing()) {
				double moved = Math.hypot(pl.getX() - startX, pl.getZ() - startZ);
				if (moved < 16) {
					memory.markBadAhead(startX, startZ, DIST);
					fail("couldn't make headway that way; will turn");
				} else {
					done("explored " + Math.round(moved) + " blocks" + (targets.isEmpty() ? "" : ", no " + String.join("/", targets) + " yet"));
				}
			}
		}
	}

	/** goto <known block group> | end_center: walk to a remembered place. */
	public static final class Goto extends Skill {
		@Override
		public String name() {
			return "goto";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 120;
			if ("death".equals(arg)) {
				WorldMemory.Seen d = memory.nearest("death");
				if (d == null) {
					fail("no death spot in this dimension");
					return;
				}
				// Stand right on the spot: the drops are scattered around it.
				Bari.path(new GoalNear(d.pos(), 1));
				return;
			}
			if ("end_center".equals(arg)) {
				Bari.path(new GoalXZ(0, 6));
				return;
			}
			WorldMemory.Seen s = memory.nearest(arg == null ? "" : arg);
			if (s == null) {
				fail("no known " + arg + " in this dimension");
				return;
			}
			Bari.path(new GoalGetToBlock(s.pos()));
		}

		@Override
		protected void tick() {
			if (ticks > 20 && !Bari.pathing()) {
				if ("death".equals(arg)) {
					WorldMemory.Seen d = memory.nearest("death");
					// Reached or not, don't try again: pickup handles what's in view from here.
					if (d != null) memory.forget("death", d.pos());
					LocalPlayer pl = Mc.player();
					if (d != null && d.pos().distSqr(pl.blockPosition()) > 6 * 6) {
						fail("couldn't reach the death spot");
						return;
					}
				}
				done("arrived near " + arg);
			}
		}
	}

	/** retreat: run away from nearby hostiles. */
	public static final class Retreat extends Skill {
		@Override
		public String name() {
			return "retreat";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 15;
			List<BlockPos> threats = new ArrayList<>();
			for (Perception.Seen s : Perception.look(20).mobs) if (s.hostile()) threats.add(s.entity().blockPosition());
			if (threats.isEmpty()) {
				done("nothing to run from");
				return;
			}
			Bari.path(new GoalRunAway(24, threats.toArray(new BlockPos[0])));
		}

		@Override
		protected void tick() {
			if (ticks > 20 && !Bari.pathing()) done("got away");
		}
	}

	/** pickup: walk over dropped items nearby. */
	public static final class Pickup extends Skill {
		private ItemEntity current;
		private int picked;
		private int sinceRepath;

		@Override
		public String name() {
			return "pickup";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 30;
		}

		@Override
		protected void tick() {
			if (current == null || !current.isAlive() || sinceRepath++ > 60) {
				if (current != null && !current.isAlive()) picked++;
				List<ItemEntity> items = Perception.look(16).items;
				if (items.isEmpty() || picked >= 12) {
					done(picked > 0 ? "picked up " + picked + " stacks" : "nothing left to pick up");
					return;
				}
				current = items.get(0);
				sinceRepath = 0;
				Bari.path(new GoalBlock(current.blockPosition()));
			}
		}
	}

	/** idle: stand still for a moment (lets the brain wait for day, for a furnace, etc.). */
	public static final class Idle extends Skill {
		@Override
		public String name() {
			return "idle";
		}

		@Override
		protected void start() {
			Bari.stop();
		}

		@Override
		protected void tick() {
			if (ticks >= 60) done("waited 3 s");
		}
	}
}
