package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CollectDigFree extends EvolvedSkill {
	private static double stuckAt = -1000;
	private static final List<Direction> DIRS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

	private Vec3 origin;
	private int dirIndex;
	private int attemptTicks;
	private int attempts;
	private Vec3 attemptStart;

	public static Option offer(Context c) {
		double now = Player.gameSeconds();
		if ("collect".equals(c.lastSkill()) && "STUCK".equals(c.lastCode())) stuckAt = c.lastEndSeconds();
		if (now - stuckAt > 60) return null;
		if (Player.inWater() || Player.inLava()) return null;
		String last = c.lastSkill();
		boolean afterStuck = ("collect".equals(last) && "STUCK".equals(c.lastCode()))
				|| ("unstuck".equals(last) && c.main() != null && "collect".equals(c.main().skill()));
		if (!afterStuck) return null;
		return new Option("collect_dig_free", null, "collect got stuck here: dig and walk out by hand first");
	}

	@Override
	public String name() { return "collect_dig_free"; }

	@Override
	protected void start() {
		timeoutTicks = 20 * 25;
		origin = Player.pos();
		stuckAt = -1000;
		dirIndex = bestStartDir();
		beginAttempt();
	}

	private int bestStartDir() {
		BlockPos f = Player.feet();
		for (int i = 0; i < 4; i++) {
			BlockPos n = f.relative(DIRS.get(i));
			if (!solid(n) && !solid(n.above())) return i;
		}
		for (int i = 0; i < 4; i++) {
			BlockPos n = f.relative(DIRS.get(i));
			if (!solid(n.above())) return i;
		}
		return 0;
	}

	private void beginAttempt() {
		attemptTicks = 0;
		attemptStart = Player.pos();
		Direction d = DIRS.get(dirIndex);
		Player.turn(d.toYRot(), 0);
	}

	private static boolean solid(BlockPos p) {
		String id = Player.blockIfVisible(p);
		if (id == null) return false;
		if (id.endsWith("air") || id.contains("water") || id.endsWith("short_grass") || id.endsWith("tall_grass")
				|| id.endsWith("fern") || id.endsWith(":snow") || id.contains("torch") || id.contains("flower")
				|| id.endsWith("dead_bush") || id.contains("vine")) return false;
		return true;
	}

	private static double horiz(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	@Override
	protected void tick() {
		if (Player.inLava()) { fail(Fail.HAZARD, "in lava while digging free"); return; }
		if (horiz(Player.pos(), origin) >= 3.0) {
			Player.releaseAll();
			done("moved " + String.format("%.1f", horiz(Player.pos(), origin)) + " blocks off the stuck spot");
			return;
		}
		attemptTicks++;
		Direction d = DIRS.get(dirIndex);
		Player.turn(d.toYRot(), 0);
		BlockPos f = Player.feet();
		BlockPos front = f.relative(d);
		BlockPos head = front.above();
		BlockPos over = f.above(2);
		BlockPos target = null;
		if (solid(head)) target = head;
		else if (solid(front) && solid(over)) target = over;
		else if (solid(front) && solid(front.above(2))) target = front;
		if (target != null) {
			if (Player.probe().lavaNear(target)) {
				nextDir("lava behind the block");
				return;
			}
			Player.press(Player.Key.FORWARD, false);
			Player.press(Player.Key.JUMP, false);
			Player.holdBestToolFor(target);
			if (!Player.mine(target) && attemptTicks > 20 * 4) nextDir("couldn't mine");
			else if (attemptTicks > 20 * 6) nextDir("mining took too long");
			return;
		}
		Player.press(Player.Key.FORWARD, true);
		Player.press(Player.Key.JUMP, solid(front) || !Player.onGround());
		if (attemptTicks >= 30 && horiz(Player.pos(), attemptStart) < 0.6) nextDir("no headway");
		else if (attemptTicks > 20 * 5) nextDir("too slow");
	}

	private void nextDir(String why) {
		Player.releaseAll();
		if (++attempts >= 8) {
			fail(Fail.STUCK, "couldn't dig or walk free in any direction (last: " + why + ")");
			return;
		}
		dirIndex = (dirIndex + 1) % 4;
		beginAttempt();
	}
}
