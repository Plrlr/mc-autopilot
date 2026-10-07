package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** collect log looks only 3 s and 64 blocks: walk to farther seen logs or roam until one is close. */
public class SeekLog extends EvolvedSkill {
	private static final int CLOSE = 40;
	private final Set<BlockPos> aside = new HashSet<>();
	private BlockPos target;
	private boolean roaming;
	private float heading;
	private Vec3 lastPos;
	private int idle;

	static String group() {
		String g = WorldMemory.groupOf("oak_log");
		return g == null ? "log" : g;
	}

	public static Option offer(Context c) {
		if (c.lastCode() == null || c.lastArg() == null) return null;
		boolean collectMiss = "collect".equals(c.lastSkill()) && "NOT_FOUND".equals(c.lastCode());
		boolean exploreSeen = "explore".equals(c.lastSkill()) && "ALREADY_DONE".equals(c.lastCode());
		if (!collectMiss && !exploreSeen) return null;
		if (!c.lastArg().startsWith("log")) return null;
		if (exploreSeen) {
			Option m = c.main();
			if (m == null || m.arg() == null || !m.arg().startsWith("log")) return null;
		}
		if (Player.gameSeconds() - c.lastEndSeconds() > 60) return null;
		if (Player.inWater() || Player.inLava()) return null;
		String d = Player.dimension();
		if (d != null && !d.contains("overworld")) return null;
		return new Option("seek_log", null, "collect log saw none in range: walk toward seen or new woods");
	}

	@Override
	public String name() { return "seek_log"; }

	private static boolean sameDim(String a, String b) {
		return a == null || b == null || a.equals(b) || a.endsWith(b) || b.endsWith(a);
	}

	private static double hd2(BlockPos p, Vec3 v) {
		double dx = p.getX() + 0.5 - v.x, dz = p.getZ() + 0.5 - v.z;
		return dx * dx + dz * dz;
	}

	private WorldMemory.Seen nearestLog() {
		List<WorldMemory.Seen> all = memory.all(group());
		if (all == null) return null;
		String dim = Player.dimension();
		Vec3 me = Player.pos();
		WorldMemory.Seen best = null;
		double bd = Double.MAX_VALUE;
		for (WorldMemory.Seen s : all) {
			if (s == null || s.pos() == null || aside.contains(s.pos()) || !sameDim(s.dim(), dim)) continue;
			double d = hd2(s.pos(), me);
			if (d < bd) { bd = d; best = s; }
		}
		return best;
	}

	@Override
	protected void start() {
		heading = Player.yaw();
		lastPos = Player.pos();
		if (!checkClose()) pickTarget();
	}

	private boolean checkClose() {
		WorldMemory.Seen s = nearestLog();
		if (s != null && hd2(s.pos(), Player.pos()) <= CLOSE * CLOSE) {
			done("log within " + CLOSE + " blocks at " + s.pos());
			return true;
		}
		return false;
	}

	private void pickTarget() {
		WorldMemory.Seen s = nearestLog();
		if (s != null) {
			roaming = false;
			target = s.pos();
			Player.walkNear(target, 3);
			return;
		}
		roaming = true;
		double r = Math.toRadians(heading);
		BlockPos f = Player.feet();
		target = new BlockPos(f.getX() + (int) Math.round(-Math.sin(r) * 40), f.getY(),
				f.getZ() + (int) Math.round(Math.cos(r) * 40));
		Player.walkNear(target, 8);
	}

	@Override
	protected void tick() {
		if (ticks % 10 == 0 && checkClose()) return;
		if (ticks % 100 == 0 && ticks > 0) {
			Vec3 p = Player.pos();
			boolean stalled = p.distanceTo(lastPos) < 2.0;
			lastPos = p;
			if (stalled) {
				Player.stopWalking();
				if (!roaming && target != null) aside.add(target);
				else heading += 90;
				pickTarget();
				return;
			}
		}
		if (!Player.walking()) {
			if (++idle < 20) return;
			idle = 0;
			if (roaming && target != null && hd2(target, Player.pos()) > 12 * 12) heading += 90;
			pickTarget();
		} else {
			idle = 0;
		}
		if (ticks >= timeoutTicks - 2) fail(Fail.NOT_FOUND, "walked 90 s without a log coming within " + CLOSE + " blocks");
	}

	@Override
	protected void cleanup() {
		Player.stopWalking();
		super.cleanup();
	}
}
