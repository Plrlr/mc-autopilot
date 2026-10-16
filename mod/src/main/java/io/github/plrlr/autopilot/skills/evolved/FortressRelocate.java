package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FortressRelocate extends EvolvedSkill {
	private BlockPos target;
	private final Set<BlockPos> excluded = new HashSet<>();
	private int sweep = -1;
	private int lastPath;
	private int repaths;

	public static Option offer(Context c) {
		if (!"fortress".equals(c.lastSkill()) || !"TIMEOUT".equals(c.lastCode())) return null;
		if (!"the_nether".equals(Player.dimension())) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 150) return null;
		if (Player.inLava() || Player.health() < 8) return null;
		Player.Mob m = Player.nearestHostile(6);
		if (m != null && !"blaze".equals(m.type())) return null;
		return new Option("fortress_relocate", null, "fortress timed out here: move to another part of the fortress");
	}

	@Override
	public String name() { return "fortress_relocate"; }

	@Override
	protected void start() {
		timeoutTicks = 20 * 75;
		if (!"the_nether".equals(Player.dimension())) { fail(Fail.WRONG_PLACE, "not in the Nether"); return; }
		target = pick();
		if (target == null) { sweep = 0; return; }
		go();
	}

	private void go() {
		Player.walkNear(target, 2);
		lastPath = ticks;
	}

	private BlockPos pick() {
		List<WorldMemory.Seen> bricks = memory.all("nether_bricks");
		if (bricks == null || bricks.isEmpty()) return null;
		String dim = Player.dimension();
		Set<BlockPos> known = new HashSet<>();
		for (WorldMemory.Seen b : bricks) known.add(b.pos());
		BlockPos me = Player.feet();
		BlockPos best = null, any = null;
		double bd = Double.MAX_VALUE, ad = Double.MAX_VALUE;
		for (WorldMemory.Seen b : bricks) {
			if (b.dim() != null && !b.dim().endsWith(dim) && !dim.endsWith(b.dim())) continue;
			BlockPos p = b.pos();
			if (excluded.contains(p)) continue;
			double d = p.distSqr(me);
			if (d < 16 * 16 || d > 64 * 64) continue;
			boolean floor = (known.contains(p.east()) || known.contains(p.west()))
					&& (known.contains(p.north()) || known.contains(p.south()))
					&& !known.contains(p.above());
			if (floor && d < bd) { bd = d; best = p; }
			if (d < ad) { ad = d; any = p; }
		}
		return best != null ? best : any;
	}

	@Override
	protected void tick() {
		if (sweep >= 0) { tickSweep(); return; }
		Player.Mob blaze = null;
		if (ticks % 5 == 0) {
			for (Player.Mob m : Player.mobs(24)) if ("blaze".equals(m.type())) { blaze = m; break; }
		}
		if (blaze != null && ticks > 20) {
			Player.stopWalking();
			done("blaze in view " + Math.round(blaze.dist()) + " blocks away after moving");
			return;
		}
		if (Player.feet().distSqr(target) <= 4 * 4) {
			Player.stopWalking();
			done("reached another part of the fortress at " + target);
			return;
		}
		if (ticks - lastPath > 40 && !Player.walking()) {
			if (++repaths > 2) {
				excluded.add(target);
				repaths = 0;
				target = pick();
				if (target == null || excluded.size() > 4) {
					fail(Fail.UNREACHABLE, "no path to other fortress parts (" + excluded.size() + " tried)");
					return;
				}
			}
			go();
		}
	}

	/** No fortress bricks known: turn round in place with a few pitch bands so the scan can spot one. */
	private void tickSweep() {
		if (ticks % 3 != 0) return;
		if (memory.nearest("nether_bricks") != null) { done("spotted fortress bricks while looking around"); return; }
		if (sweep >= 36) { fail(Fail.NOT_FOUND, "looked around, no fortress in view"); return; }
		float[] pitches = {0f, -10f, 15f};
		Player.turn((sweep % 12) * 30f, pitches[(sweep / 12) % 3]);
		sweep++;
	}
}
