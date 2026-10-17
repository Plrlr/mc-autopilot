package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.skills.FairProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.List;

public class FlintClearing extends EvolvedSkill {
	private final ArrayDeque<BlockPos> toMine = new ArrayDeque<>();
	private BlockPos cell;
	private int targetTicks;
	private int placeTries;

	public static Option offer(Context c) {
		if (!"collect".equals(c.lastSkill()) || !"NO_ROOM".equals(c.lastCode())) return null;
		if (c.lastArg() == null || !c.lastArg().startsWith("flint")) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 120) return null;
		if (Player.inWater() || Player.inLava() || !Player.onGround()) return null;
		return new Option("flint_clearing", null, "flint had no free flat spot: dig one beside us");
	}

	@Override
	public String name() { return "flint_clearing"; }

	private static boolean bad(String id) {
		if (id == null) return false;
		return id.contains("furnace") || id.contains("table") || id.contains("chest") || id.contains("bed")
				|| id.contains("bedrock") || id.contains("obsidian") || id.contains("portal")
				|| id.contains("water") || id.contains("lava") || id.contains("spawner");
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 30;
		FairProbe probe = Player.probe();
		BlockPos feet = Player.feet();
		BlockPos best = null;
		int bestDigs = 99;
		for (Direction d : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
			BlockPos n = feet.relative(d);
			BlockPos head = n.above();
			BlockPos floor = n.below();
			if (probe.lavaNear(n) || probe.lavaNear(head) || probe.fluid(n) || probe.fluid(head) || probe.fluid(floor)) continue;
			if (bad(Player.blockIfVisible(n)) || bad(Player.blockIfVisible(head)) || bad(Player.blockIfVisible(floor))) continue;
			if (!probe.solid(floor)) continue;
			int digs = (probe.free(n) ? 0 : 1) + (probe.free(head) ? 0 : 1);
			// A side already free and flat didn't satisfy the flint steps; prefer cutting a fresh one.
			int score = digs == 0 ? 5 : digs;
			if (score < bestDigs) { bestDigs = score; best = d == null ? null : n; }
		}
		if (best == null) { fail(Fail.NO_ROOM, "no side cell safe to dig out"); return; }
		cell = best;
		if (!probe.free(cell.above())) toMine.add(cell.above());
		if (!probe.free(cell)) toMine.add(cell);
		if (toMine.isEmpty()) { fail(Fail.ALREADY_DONE, "side cell already open at " + cell); return; }
		targetTicks = 0;
	}

	@Override
	protected void tick() {
		if (cell == null) return;
		if (!toMine.isEmpty()) {
			BlockPos t = toMine.peekFirst();
			if (Player.probe().free(t)) { toMine.removeFirst(); targetTicks = 0; Player.releaseAll(); return; }
			if (Player.probe().lavaNear(t)) { fail(Fail.HAZARD, "lava beside the cell we were digging"); return; }
			if (++targetTicks > 20 * 8) { fail(Fail.STUCK, "couldn't break " + Player.blockIfVisible(t) + " at " + t); return; }
			Player.lookAt(t);
			Player.holdBestToolFor(t);
			Player.mine(t);
			return;
		}
		BlockPos floor = cell.below();
		FairProbe probe = Player.probe();
		if (probe.fluid(cell) || probe.lavaNear(cell)) { fail(Fail.HAZARD, "fluid flowed into the cleared cell"); return; }
		if (probe.visible(floor) && probe.free(floor)) {
			if (++placeTries > 40) { fail(Fail.PLACE_FAILED, "couldn't fill the floor at " + floor); return; }
			boolean held = Player.hold("cobblestone") || Player.hold("cobbled_deepslate") || Player.hold("dirt")
					|| Player.hold("netherrack") || Player.hold("stone");
			if (!held) { fail(Fail.NEED_ITEM, "no block to floor the cleared cell"); return; }
			Player.lookAt(floor);
			Player.place(floor);
			return;
		}
		done("cleared a flat cell at " + cell + " for the gravel");
	}
}
