package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalGetToBlock;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Gets a crafting table / furnace open: walk to a known one, or place one from the inventory,
 * then right-click it. Used by craft and smelt.
 */
final class Station {
	private enum Phase {FIND, WALK, PLACE, OPEN, WAIT_OPEN}

	private final String group;
	private final Class<?> menuClass;
	private final WorldMemory memory;
	private Phase phase = Phase.FIND;
	private BlockPos pos;
	private int wait;
	private int placeTries;
	String error;

	Station(String group, Class<?> menuClass, WorldMemory memory) {
		this.group = group;
		this.menuClass = menuClass;
		this.memory = memory;
	}

	boolean ready() {
		return menuClass.isInstance(Mc.player().containerMenu);
	}

	void tick() {
		if (ready() || error != null) return;
		LocalPlayer pl = Mc.player();
		switch (phase) {
			case FIND -> {
				WorldMemory.Seen s = memory.nearestStation(group);
				if (s != null) {
					pos = s.pos();
					if (inReach(pos)) phase = Phase.OPEN;
					else {
						Bari.path(new GoalGetToBlock(pos));
						phase = Phase.WALK;
						wait = 0;
					}
				} else if (Mc.count(group) > 0) {
					phase = Phase.PLACE;
				} else {
					error = "no " + group + " nearby or in the inventory";
				}
			}
			case WALK -> {
				wait++;
				if (wait > 10 && !Bari.pathing()) {
					if (inReach(pos)) phase = Phase.OPEN;
					else if (Mc.count(group) > 0) phase = Phase.PLACE;
					else error = "couldn't reach the " + group;
				}
				if (wait > 20 * 60) error = "took too long to reach the " + group;
			}
			case PLACE -> {
				Bari.stop();
				BlockPos spot = findSpot(pl);
				if (spot == null || placeTries++ > 3) {
					error = "no room to place a " + group;
					return;
				}
				if (!Mc.holdItem(Items2.matcher(group))) {
					error = "no " + group + " to place";
					return;
				}
				Mc.placeAt(spot);
				pos = spot;
				wait = 0;
				phase = Phase.OPEN;
			}
			case OPEN -> {
				// Give the server a few ticks to confirm a fresh placement before clicking it.
				if (wait++ < 4) return;
				String there = Mc.id(Mc.state(pos).getBlock());
				if (!there.equals(group)) {
					memory.forget(group, pos);
					phase = Mc.count(group) > 0 ? Phase.PLACE : Phase.FIND;
					if (phase == Phase.FIND) error = group + " disappeared";
					return;
				}
				memory.remember(group, pos, there);
				Bari.stop();
				Mc.useOn(pos, faceToward(pl, pos));
				wait = 0;
				phase = Phase.WAIT_OPEN;
			}
			case WAIT_OPEN -> {
				if (++wait > 40) error = "the " + group + " didn't open";
			}
		}
	}

	private static boolean inReach(BlockPos p) {
		return Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(p)) <= Mc.reach() - 0.3;
	}

	private static Direction faceToward(LocalPlayer pl, BlockPos p) {
		Vec3 d = pl.getEyePosition().subtract(Vec3.atCenterOf(p));
		return Direction.getApproximateNearest(d.x, d.y, d.z);
	}

	/** An empty spot next to the player, on solid ground, not where the player stands. */
	static BlockPos findSpot(LocalPlayer pl) {
		BlockPos feet = pl.blockPosition();
		for (int r = 1; r <= 2; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					for (int dy = 0; dy >= -1; dy--) {
						BlockPos p = feet.offset(dx, dy, dz);
						if (Mc.free(p) && Mc.solid(p.below()) && Mc.canSee(p.below())) return p;
					}
				}
			}
		}
		return null;
	}
}
