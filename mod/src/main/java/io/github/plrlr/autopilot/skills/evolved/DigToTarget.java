package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.skills.FairProbe;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DigToTarget extends EvolvedSkill {
	private static final Map<String, String> SOURCE = Map.of(
			"log", "oak_log", "stone", "stone", "cobblestone", "stone", "coal", "coal_ore",
			"raw_iron", "iron_ore", "raw_gold", "gold_ore", "diamond", "diamond_ore");

	private String item, group;
	private int before;
	private BlockPos target, lastFeet;
	private int lastMoveTick;
	private final Set<BlockPos> skip = new HashSet<>();

	public static Option offer(Context c) {
		if (!"collect".equals(c.lastSkill()) || !"STUCK".equals(c.lastCode()) || c.lastArg() == null) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 25) return null;
		if (Player.inWater() || Player.inLava()) return null;
		String it = c.lastArg().split(":")[0];
		String src = SOURCE.get(it);
		if (src == null) return null;
		String g = WorldMemory.groupOf(src);
		if (g == null || c.memory() == null) return null;
		WorldMemory.Seen s = c.memory().nearest(g);
		if (s == null || s.pos().distSqr(Player.feet()) > 24 * 24) return null;
		return new Option("dig_to_target", c.lastArg(), "collect got stuck: dig straight to a seen " + it + " by hand");
	}

	@Override
	public String name() { return "dig_to_target"; }

	@Override
	protected void start() {
		timeoutTicks = 20 * 60;
		item = arg == null ? "" : arg.split(":")[0];
		String src = SOURCE.get(item);
		group = src == null ? null : WorldMemory.groupOf(src);
		if (group == null) { fail(Fail.NO_RECIPE, "no source block for " + item); return; }
		before = Player.count(item);
		lastFeet = Player.feet();
		lastMoveTick = 0;
		Player.stopWalking();
		pick();
		if (target == null) fail(Fail.NOT_FOUND, "no " + item + " remembered within 24 blocks");
	}

	private void pick() {
		target = null;
		BlockPos f = Player.feet();
		double best = 24 * 24;
		List<WorldMemory.Seen> all = memory.all(group);
		if (all == null) return;
		for (WorldMemory.Seen s : all) {
			if (skip.contains(s.pos()) || !Player.dimension().equals(s.dim())) continue;
			double d = s.pos().distSqr(f);
			if (d < best) { best = d; target = s.pos(); }
		}
	}

	private static boolean solid(String id) {
		return id != null && !id.contains("air") && !id.contains("water") && !id.contains("lava");
	}

	/** Mines p if solid; true when it is still blocking (work done this tick). */
	private boolean clear(BlockPos p, FairProbe probe) {
		String id = Player.blockIfVisible(p);
		if (id == null) {
			if (!probe.solid(p)) return false;
		}
		if (id != null && !solid(id)) return false;
		if (probe.lavaNear(p) || (id != null && id.contains("lava"))) throw new IllegalStateException("lava");
		Player.lookAt(p);
		Player.holdBestToolFor(p);
		Player.mine(p);
		lastMoveTick = ticks;
		return true;
	}

	@Override
	protected void tick() {
		if (Player.count(item) > before) { done("hand-mined " + (Player.count(item) - before) + " " + item); return; }
		if (Player.inLava()) { fail(Fail.HAZARD, "in lava"); return; }
		BlockPos f = Player.feet();
		if (!f.equals(lastFeet)) { lastFeet = f; lastMoveTick = ticks; }
		if (ticks - lastMoveTick > 20 * 8) { fail(Fail.STUCK, "no move or dig for 8 s toward " + target); return; }
		if (target == null) { fail(Fail.NOT_FOUND, "no " + item + " left in memory nearby"); return; }
		String tid = Player.blockIfVisible(target);
		if (tid != null && !solid(tid)) {
			skip.add(target);
			pick();
			return;
		}
		FairProbe probe = Player.probe();
		try {
			double ex = Player.pos().x, ey = Player.pos().y + 1.62, ez = Player.pos().z;
			double dx = target.getX() + 0.5 - ex, dy = target.getY() + 0.5 - ey, dz = target.getZ() + 0.5 - ez;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist <= Player.reach() - 0.3 && Player.visible(target)) {
				Player.press(Player.Key.FORWARD, false);
				Player.press(Player.Key.JUMP, false);
				if (probe.lavaNear(target)) { skip.add(target); pick(); return; }
				Player.lookAt(target);
				Player.holdBestToolFor(target);
				Player.mine(target);
				lastMoveTick = ticks;
				return;
			}
			int hx = target.getX() - f.getX(), hz = target.getZ() - f.getZ();
			int ty = target.getY() - f.getY();
			Direction dir;
			if (hx == 0 && hz == 0) {
				// straight above or below: don't dig down blind; step to the side is beyond us
				skip.add(target); pick(); return;
			}
			if (Math.abs(hx) >= Math.abs(hz)) dir = hx > 0 ? Direction.EAST : Direction.WEST;
			else dir = hz > 0 ? Direction.SOUTH : Direction.NORTH;
			BlockPos ahead = f.relative(dir);
			boolean up = ty > 0 && probe.solid(ahead) && solid(Player.blockIfVisible(ahead) == null ? "stone" : Player.blockIfVisible(ahead));
			Player.press(Player.Key.FORWARD, false);
			if (up) {
				if (clear(f.above(2), probe)) return;
				if (clear(ahead.above(2), probe)) return;
				if (clear(ahead.above(), probe)) return;
			} else {
				if (clear(ahead.above(), probe)) return;
				if (clear(ahead, probe)) return;
				if (ty < 0 && Player.onGround() && clear(ahead.below(), probe)) return;
			}
			float yaw = (float) Math.toDegrees(Math.atan2(-(ahead.getX() + 0.5 - Player.pos().x), ahead.getZ() + 0.5 - Player.pos().z));
			Player.turn(yaw, 0);
			Player.press(Player.Key.FORWARD, true);
			Player.press(Player.Key.JUMP, up && Player.onGround());
		} catch (IllegalStateException e) {
			skip.add(target);
			pick();
			if (target == null) fail(Fail.HAZARD, "lava seen on the way to the " + item);
		}
	}
}
