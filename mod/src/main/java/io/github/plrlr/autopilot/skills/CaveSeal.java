package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.AutopilotMod;
import io.github.plrlr.autopilot.state.Perception;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Watches blocks the player saw before mining, then closes a revealed cave mouth. In night2 the
 * iron trip at y 36 was interrupted by a zombie and skeleton emerging beside the tunnel; closing
 * the opening is safer than following Baritone into that cave with stone tools and no armor.
 */
final class CaveSeal {
	enum Status { CLEAR, BUSY, SEALED, FAILED }

	private final Block[] wanted;
	private Set<BlockPos> solidLastTick = new HashSet<>();
	private final Map<BlockPos, Integer> sealCount = new HashMap<>();
	private final Map<BlockPos, Direction> oreOpenings = new HashMap<>();
	private BlockPos mouth;
	private int attempts;

	CaveSeal(Block[] wanted) {
		this.wanted = wanted;
	}

	Status tick() {
		if (mouth != null) {
			if (Mc.solid(mouth)) {
				sealCount.merge(mouth, 1, Integer::sum);
				AutopilotMod.LOGGER.info("[cave] sealed opening at {}", mouth.toShortString());
				solidLastTick.clear();
				solidLastTick.add(mouth);
				mouth = null;
				return Status.SEALED;
			}
			if (++attempts > 6 || !Mc.clearOfPlayer(mouth)) return Status.FAILED;
			Act.place(mouth);
			return Status.BUSY;
		}
		BlockPos feet = Mc.player().blockPosition();
		oreOpenings.entrySet().removeIf(e -> e.getKey().distSqr(feet) > 16 || !Mc.canSee(e.getKey()) || !Mc.free(e.getKey()));
		for (var it = oreOpenings.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			if (!Mc.clearOfPlayer(e.getKey()) || !visibleHostileBeyond(e.getKey(), e.getValue())) continue;
			Bari.stop();
			mouth = e.getKey();
			it.remove();
			attempts = 0;
			return Status.BUSY;
		}
		Set<BlockPos> now = new HashSet<>();
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			if (dx == 0 && dz == 0) continue;
			for (int dy = 0; dy <= 2; dy++) {
				BlockPos p = feet.offset(dx, dy, dz);
				if (p.distSqr(feet) > 12 || !Mc.canSee(p)) continue;
				if (!Mc.free(p)) {
					now.add(p);
					continue;
				}
				if (!solidLastTick.contains(p) || !Mc.clearOfPlayer(p)) continue;
				Direction out = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
						: (dz > 0 ? Direction.SOUTH : Direction.NORTH);
				BlockPos beyond = p.relative(out);
				boolean wide = caveShape(air(beyond), air(beyond.relative(out.getClockWise())),
						air(beyond.relative(out.getCounterClockWise())), air(beyond.above(2)));
				boolean hostile = visibleHostileBeyond(p, out);
				// Seen ore in a calm cave is worth taking. A visible hostile makes the gap unsafe.
				if (!wide && !hostile) continue;
				boolean ore = !hostile && wantedOreNear(beyond, out);
				if (!shouldSeal(wide, hostile, ore)) {
					if (wide && ore && oreOpenings.size() < 8) oreOpenings.put(p, out);
					continue;
				}
				if (sealCount.getOrDefault(p, 0) >= 2) return Status.FAILED;
				Bari.stop();
				mouth = p;
				attempts = 0;
				solidLastTick = now;
				return Status.BUSY;
			}
		}
		solidLastTick = now;
		return Status.CLEAR;
	}

	/** A normal two-high tunnel is narrow; side air or a third block of height signals a cave. */
	static boolean caveShape(boolean beyond, boolean left, boolean right, boolean high) {
		return beyond && (left || right || high);
	}

	static boolean shouldSeal(boolean cave, boolean hostile, boolean wantedOre) {
		return (cave || hostile) && (hostile || !wantedOre);
	}

	private static boolean air(BlockPos p) {
		return Mc.canSee(p) && Mc.free(p);
	}

	private static boolean visibleHostileBeyond(BlockPos p, Direction out) {
		for (Perception.Seen mob : Perception.look(10).mobs) {
			if (!mob.hostile() || !Mc.canSee(mob.entity())) continue;
			BlockPos at = mob.entity().blockPosition();
			if ((at.getX() - p.getX()) * out.getStepX() + (at.getZ() - p.getZ()) * out.getStepZ() >= 0
					&& at.distSqr(p) < 10 * 10) return true;
		}
		return false;
	}

	private boolean wantedOreNear(BlockPos p, Direction out) {
		for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
			BlockPos q = p.offset(dx, dy, dz);
			if ((q.getX() - p.getX()) * out.getStepX() + (q.getZ() - p.getZ()) * out.getStepZ() < 0) continue;
			if (!Mc.canSee(q)) continue;
			for (Block block : wanted) if (Mc.state(q).getBlock() == block) return true;
		}
		return false;
	}
}
