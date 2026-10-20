package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

public class ShoreStepOut extends EvolvedSkill {
	private static final String[] FILL = {"cobblestone", "cobbled_deepslate", "dirt", "netherrack", "stone",
			"andesite", "diorite", "granite", "deepslate", "tuff"};
	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	private final List<BlockPos> dig = new ArrayList<>();
	private BlockPos floor, stand;
	private boolean needFloor;
	private int phase, phaseTicks, digTicks;

	public ShoreStepOut() {
		timeoutTicks = 20 * 40;
	}

	public static Option offer(Context c) {
		if (!"shore".equals(c.lastSkill())) return null;
		if (!"TIMEOUT".equals(c.lastCode()) && !"STUCK".equals(c.lastCode())) return null;
		if (!Player.inWater() || Player.inLava()) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 180) return null;
		return new Option("shore_step_out", null, "shore failed here: dig a dry step into the wall beside us and climb out");
	}

	@Override
	public String name() { return "shore_step_out"; }

	private static boolean open(BlockPos p) {
		String b = Player.blockIfVisible(p);
		if (b == null) return false;
		return b.endsWith("air") || b.endsWith("water") || b.contains("seagrass") || b.contains("kelp");
	}

	private static boolean water(BlockPos p) {
		String b = Player.blockIfVisible(p);
		return b != null && b.endsWith("water");
	}

	private static boolean lava(BlockPos p) {
		String b = Player.blockIfVisible(p);
		return b != null && b.contains("lava");
	}

	private static boolean lavaNear(BlockPos p) {
		if (lava(p)) return true;
		for (Direction d : Direction.values()) if (lava(p.relative(d))) return true;
		return false;
	}

	private static boolean haveFill() {
		for (String s : FILL) if (Player.count(s) > 0) return true;
		return false;
	}

	private static boolean holdFill() {
		for (String s : FILL) if (Player.count(s) > 0 && Player.hold(s)) return true;
		return false;
	}

	@Override
	protected void start() {
		if (!Player.inWater()) { done("already out of the water"); return; }
		BlockPos f = Player.feet();
		BlockPos top = null;
		for (int dy = 0; dy <= 4; dy++) {
			BlockPos p = f.above(dy);
			if (water(p)) top = p;
			else if (top != null || dy > 0) break;
		}
		if (top == null) top = f;
		BlockPos above = top.above();
		if (!open(above) && water(above)) { fail(Fail.NOT_FOUND, "no air above the water here"); return; }
		boolean fill = haveFill();
		int best = Integer.MAX_VALUE;
		for (Direction d : SIDES) {
			BlockPos fl = top.relative(d), st = fl.above(), hd = st.above();
			if (lavaNear(st) || lavaNear(hd) || lava(fl)) continue;
			int cost = 0;
			boolean nf = false;
			String fb = Player.blockIfVisible(fl);
			if (fb == null) cost += 1;
			else if (open(fl)) {
				if (!fill) continue;
				nf = true;
				cost += 2;
			}
			if (!open(st)) cost += 2;
			if (!open(hd)) cost += 2;
			if (cost < best) {
				best = cost;
				floor = fl;
				stand = st;
				needFloor = nf;
			}
		}
		if (stand == null) { fail(Fail.NO_ROOM, "no side to step out on (lava or open water all round)"); return; }
		BlockPos ownHead = top.above();
		if (!open(ownHead) && !lavaNear(ownHead)) dig.add(ownHead);
		if (!open(stand)) dig.add(stand);
		if (!open(stand.above())) dig.add(stand.above());
		phase = 0;
	}

	@Override
	protected void tick() {
		if (!Player.inWater() && Player.onGround()) { done("stepped out of the water at " + Player.feet()); return; }
		phaseTicks++;
		if (phase == 0) {
			while (!dig.isEmpty() && open(dig.get(0))) { dig.remove(0); digTicks = 0; }
			if (dig.isEmpty()) { Player.releaseAll(); phase = needFloor ? 1 : 2; phaseTicks = 0; return; }
			BlockPos p = dig.get(0);
			if (lavaNear(p)) { fail(Fail.HAZARD, "lava next to " + p); return; }
			Player.press(Player.Key.JUMP, Player.eyesInWater());
			Player.holdBestToolFor(p);
			Player.lookAt(p);
			Player.mine(p);
			if (++digTicks > 120) { fail(Fail.NO_PROGRESS, "could not mine " + p); }
			return;
		}
		if (phase == 1) {
			if (!open(floor)) { phase = 2; phaseTicks = 0; return; }
			Player.press(Player.Key.JUMP, Player.eyesInWater());
			if (!holdFill()) { fail(Fail.NEED_ITEM, "no block to fill the step floor"); return; }
			Player.lookAt(floor);
			Player.place(floor);
			if (phaseTicks > 60) fail(Fail.PLACE_FAILED, "could not place a floor at " + floor);
			return;
		}
		Player.lookAt(stand);
		Player.press(Player.Key.FORWARD, true);
		Player.press(Player.Key.JUMP, true);
		if (phaseTicks > 100) fail(Fail.STUCK, "could not climb onto the step at " + stand);
	}
}
