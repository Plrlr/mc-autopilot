package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalXZ;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A remembered overworld spawner plus fresh mobs after a prior wave is a live dungeon. */
public final class SpawnerRoom extends Skill {
	private static final Set<UUID> FIRST_WAVE = new HashSet<>();
	private static BlockPos observed;
	private static long firstTick, lastTick, avoidUntil;
	private static boolean live;
	private BlockPos spawner;
	private Direction away;

	public static void reset() { FIRST_WAVE.clear(); observed = null; live = false; avoidUntil = 0; }

	public static boolean near(WorldMemory memory, BlockPos at, int radius) {
		if (!Mc.dimension().equals("overworld")) return false; // blaze farms must survive
		for (WorldMemory.Seen s : memory.all("spawner"))
			if (s.dim().equals(Mc.dimension()) && !portalSpawner(memory, s.pos())
					&& s.pos().distSqr(at) <= radius * radius) return true;
		return false;
	}

	private static boolean portalSpawner(WorldMemory memory, BlockPos at) {
		for (WorldMemory.Seen frame : memory.all("end_portal_frame"))
			if (frame.dim().equals(Mc.dimension()) && frame.pos().distSqr(at) <= 16 * 16) return true;
		return false;
	}

	public static void observe(WorldMemory memory, Perception seen, long tick) {
		lastTick = tick;
		if (!Mc.dimension().equals("overworld")) return;
		WorldMemory.Seen s = memory.nearest("spawner");
		if (s == null || portalSpawner(memory, s.pos())) return;
		if (!s.pos().equals(observed)) { observed = s.pos(); FIRST_WAVE.clear(); firstTick = 0; live = false; }
		for (Perception.Seen mob : seen.mobs) {
			if (!mob.hostile() || mob.entity().blockPosition().distSqr(observed) > 8 * 8) continue;
			if (firstTick == 0) firstTick = tick;
			if (tick - firstTick >= 20 * 10 && !FIRST_WAVE.contains(mob.entity().getUUID())) live = true;
			FIRST_WAVE.add(mob.entity().getUUID());
		}
	}

	public static boolean escapeNeeded(WorldMemory memory) {
		return live && lastTick >= avoidUntil && observed != null
				&& near(memory, Mc.player().blockPosition(), 12);
	}

	@Override public String name() { return "spawner_escape"; }
	@Override public boolean ownsSafety() { return true; }

	@Override protected void start() {
		timeoutTicks = 20 * 30;
		if (observed == null || !Mc.dimension().equals("overworld")) { fail(Fail.NOT_FOUND, "no live dungeon"); return; }
		spawner = observed;
		avoidUntil = lastTick + 20 * 60;
		Bari.stop();
		var p = Mc.player().blockPosition();
		away = Math.abs(p.getX() - spawner.getX()) >= Math.abs(p.getZ() - spawner.getZ())
				? (p.getX() >= spawner.getX() ? Direction.EAST : Direction.WEST)
				: (p.getZ() >= spawner.getZ() ? Direction.SOUTH : Direction.NORTH);
	}

	@Override protected void tick() {
		BlockPos feet = Mc.player().blockPosition();
		if (feet.distSqr(spawner) > 16 * 16) { done("left the spawner room"); return; }
		// Seal the opening between us and the spawner before leaving, if we carry blocks.
		if (ticks <= 8) {
			BlockPos wall = feet.relative(away.getOpposite()).above(ticks > 4 ? 1 : 0);
			if (Mc.free(wall)) Act.place(wall);
			return;
		}
		if (ticks == 9 || !Bari.pathing() && ticks % 40 == 0)
			Bari.path(new GoalXZ(feet.getX() + away.getStepX() * 20, feet.getZ() + away.getStepZ() * 20));
	}
}
