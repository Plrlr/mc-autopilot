package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
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
import java.util.concurrent.ThreadLocalRandom;

/** Skills that are mostly "walk somewhere with Baritone". */
public final class MoveSkills {
	private MoveSkills() {}

	/** explore north|south|east|west|random: walk ~80 blocks into new ground. */
	public static final class Explore extends Skill {
		@Override
		public String name() {
			return "explore";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 90;
			LocalPlayer pl = Mc.player();
			int dist = 80;
			String dir = arg == null ? "random" : arg;
			double angle = switch (dir) {
				case "north" -> Math.PI * 1.5;
				case "south" -> Math.PI * 0.5;
				case "east" -> 0;
				case "west" -> Math.PI;
				default -> ThreadLocalRandom.current().nextDouble(Math.PI * 2);
			};
			int x = (int) (pl.getX() + Math.cos(angle) * dist);
			int z = (int) (pl.getZ() + Math.sin(angle) * dist);
			Bari.path(new GoalXZ(x, z));
		}

		@Override
		protected void tick() {
			if (ticks > 20 && !Bari.pathing()) done("explored");
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
			if (ticks > 20 && !Bari.pathing()) done("arrived near " + arg);
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
