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
public final class Station {
	/** Stations we placed ourselves (and may pick back up), by position. Cleared per world. */
	private static final java.util.Map<BlockPos, String> PLACED = new java.util.HashMap<>();

	public static void forgetPlaced() {
		PLACED.clear();
	}

	static void forget(BlockPos p) {
		PLACED.remove(p);
	}

	/** Our own table or furnace still standing within `r` blocks. */
	public static boolean placedNear(BlockPos at, int r) {
		return !placedWithin(at, r).isEmpty();
	}

	static java.util.List<BlockPos> placedWithin(BlockPos at, int r) {
		java.util.List<BlockPos> out = new java.util.ArrayList<>();
		PLACED.entrySet().removeIf(e -> Mc.player() != null && !Mc.id(Mc.state(e.getKey()).getBlock()).equals(e.getValue())
				&& e.getKey().distSqr(at) < 16 * 16);
		for (BlockPos p : PLACED.keySet()) if (p.distSqr(at) <= (double) r * r) out.add(p);
		return out;
	}

	private enum Phase {FIND, WALK, PLACE, RELOCATE, OPEN, WAIT_OPEN}

	private final String group;
	private final Class<?> menuClass;
	private final WorldMemory memory;
	private Phase phase = Phase.FIND;
	private BlockPos pos;
	private int wait;
	private int placeTries;
	private int relocations;
	private int airTicks;
	private BlockPos placing;
	private BlockPos lastPlaced;
	/** Spots where a placing click didn't take; findSpot tries others. */
	private final java.util.List<BlockPos> badSpots = new java.util.ArrayList<>();
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
				// Still falling or climbing (common right after chopping a tree): placing from mid-air
				// picks a new spot every tick and never lands a click.
				if (!pl.onGround() && !pl.isInWater() && ++airTicks < 40) return;
				BlockPos spot = findSpot(pl, badSpots);
				// A few clicks here didn't work: try again from open ground.
				if (spot != null && placeTries >= 4 && relocations < 2) spot = null;
				if (spot == null) {
					// Usually we're up a tree or in leaves after chopping: walk to open ground first.
					BlockPos open = openGround(pl);
					if (open == null || relocations++ >= 2) {
						error = "no room to place a " + group;
						return;
					}
					Bari.path(new baritone.api.pathing.goals.GoalBlock(open));
					phase = Phase.RELOCATE;
					wait = 0;
					placeTries = 0;
					airTicks = 0;
					return;
				}
				if (placeTries++ > 5) {
					error = "couldn't place the " + group;
					return;
				}
				// Select the item this tick and place on the next, so the server has the right
				// item in hand when the click arrives.
				if (placing == null || !placing.equals(spot)) {
					if (!Mc.holdItem(Items2.matcher(group))) {
						error = "no " + group + " to place";
						return;
					}
					placing = spot;
					return;
				}
				placing = null;
				boolean accepted = Mc.placeAt(spot);
				// Trial runs failed to place here often, for no reason the logs showed: record the details.
				io.github.plrlr.autopilot.AutopilotMod.LOGGER.info("[station] place {} at {} from {} ground={} water={} hand={} accepted={} now={}",
						group, spot.toShortString(), pl.blockPosition().toShortString(), pl.onGround(), pl.isInWater(),
						Items2.id(pl.getMainHandItem()), accepted, Mc.id(Mc.state(spot).getBlock()));
				pos = spot;
				lastPlaced = spot;
				wait = 0;
				phase = Phase.OPEN;
			}
			case RELOCATE -> {
				if (++wait > 20 * 30) {
					error = "couldn't reach open ground to place a " + group;
					return;
				}
				if (wait > 10 && !Bari.pathing()) phase = Phase.PLACE;
			}
			case OPEN -> {
				// Give the server a few ticks to confirm a fresh placement before clicking it.
				if (wait++ < 4) return;
				String there = Mc.id(Mc.state(pos).getBlock());
				if (!there.equals(group)) {
					if (pos.equals(lastPlaced)) {
						badSpots.add(pos);
						io.github.plrlr.autopilot.AutopilotMod.LOGGER.info("[station] {} not there after placing at {}: found {}", group, pos.toShortString(), there);
					}
					memory.forget(group, pos);
					phase = Mc.count(group) > 0 ? Phase.PLACE : Phase.FIND;
					if (phase == Phase.FIND) error = group + " disappeared";
					return;
				}
				memory.remember(group, pos, there);
				if (placing == null && pos.equals(lastPlaced)) PLACED.put(pos.immutable(), group);
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

	/** A nearby spot on real ground (not leaves) with room around it, to stand on while placing. */
	static BlockPos openGround(LocalPlayer pl) {
		BlockPos feet = pl.blockPosition();
		for (int r = 2; r <= 10; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					for (int dy = -6; dy <= 2; dy++) {
						BlockPos p = feet.offset(dx, dy, dz);
						if (!Mc.free(p) || !Mc.free(p.above()) || !Mc.solid(p.below())) continue;
						if (Mc.id(Mc.state(p.below()).getBlock()).endsWith("_leaves")) continue;
						int room = 0;
						for (Direction d : Direction.Plane.HORIZONTAL) {
							BlockPos n = p.relative(d);
							if (Mc.free(n) && Mc.solid(n.below())) room++;
						}
						if (room >= 2) return p;
					}
				}
			}
		}
		return null;
	}

	/**
	 * An empty spot within reach, on something solid to click against, not where the player
	 * stands. Tries close spots first, at foot level, then one up and one down.
	 */
	static BlockPos findSpot(LocalPlayer pl) {
		return findSpot(pl, java.util.List.of());
	}

	static BlockPos findSpot(LocalPlayer pl, java.util.List<BlockPos> exclude) {
		BlockPos feet = pl.blockPosition();
		for (int r = 1; r <= 3; r++) {
			for (int dy : new int[]{0, -1, 1}) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
						BlockPos p = feet.offset(dx, dy, dz);
						if (p.equals(feet) || p.equals(feet.above()) || exclude.contains(p)) continue;
						if (!Mc.free(p) || !Mc.solid(p.below()) || !Mc.clearOfPlayer(p)) continue;
						if (pl.getEyePosition().distanceTo(Vec3.atCenterOf(p)) > Mc.reach() - 0.3) continue;
						if (Mc.canSee(p.below()) || Mc.canSee(p)) return p;
					}
				}
			}
		}
		return null;
	}
}
