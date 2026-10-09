package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.skills.FairProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ClimbShaft extends EvolvedSkill {
	private static final List<String> BLOCKS = List.of("cobblestone", "cobbled_deepslate", "dirt", "netherrack",
			"stone", "deepslate", "andesite", "diorite", "granite", "tuff", "blackstone", "cobblestone_slab");
	private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private BlockPos base;
	private int jumpTicks;
	private int bestY, sinceHigher;
	private BlockPos side;
	private int sideTicks, sideIdx;

	public static Option offer(Context c) {
		if (!"goto".equals(c.lastSkill()) || !"surface".equals(c.lastArg()) || !"UNREACHABLE".equals(c.lastCode())) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 150) return null;
		String dim = Player.dimension();
		if (dim == null || !dim.contains("overworld") || Player.inLava()) return null;
		return new Option("climb_shaft", null, "goto surface couldn't find the way up: pillar straight up");
	}

	@Override
	public String name() { return "climb_shaft"; }

	private static String id(BlockPos p) { return Player.blockIfVisible(p); }
	private static boolean air(String s) { return s != null && s.endsWith("air"); }
	private static boolean bad(String s) {
		return s != null && (s.contains("lava") || s.contains("bedrock") || s.contains("obsidian") || s.contains("reinforced") || s.contains("portal"));
	}
	private static boolean passable(String s) { return air(s) || (s != null && s.contains("water")); }

	private boolean openAbove(BlockPos feet, int n) {
		for (int i = 2; i < 2 + n; i++) if (!air(id(feet.above(i)))) return false;
		return true;
	}

	private String holdBlock() {
		for (String b : BLOCKS) if (Player.count(b) > 0 && Player.hold(b)) return b;
		return null;
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 150;
		Player.stopWalking();
		bestY = Player.feet().getY();
	}

	@Override
	protected void tick() {
		BlockPos feet = Player.feet();
		if (feet.getY() > bestY) { bestY = feet.getY(); sinceHigher = 0; } else sinceHigher++;
		if (feet.getY() >= 60 && openAbove(feet, 12)) { done("open shaft above at y " + feet.getY()); return; }
		if (sinceHigher > 20 * 20) { fail(Fail.STUCK, "no new height for 20 s at y " + feet.getY()); return; }
		if (Player.inLava()) { fail(Fail.HAZARD, "in lava"); return; }

		if (side != null) { stepSide(feet); return; }

		BlockPos head = feet.above(), top = feet.above(2);
		String h = id(head), t = id(top), t2 = id(top.above());
		if (bad(h) || bad(t) || (t2 != null && t2.contains("lava")) || FairProbe.lavaSeenNear(top)) { pickSide(feet); return; }

		if (Player.inWater() && passable(t) && passable(h)) {
			Player.press(Player.Key.JUMP, true);
			return;
		}
		if (h != null && !passable(h)) { dig(head); return; }
		if (t == null || !passable(t)) { dig(top); return; }

		// Column open for two blocks: pillar one step.
		if (base == null) {
			if (!Player.onGround()) return;
			if (holdBlock() == null) {
				if (air(t) && air(t2)) { fail(Fail.NEED_ITEM, "no blocks to pillar with at y " + feet.getY()); return; }
				if (t2 != null && !passable(t2)) { dig(top.above()); return; }
				fail(Fail.NEED_ITEM, "no blocks to pillar with");
				return;
			}
			base = feet;
			jumpTicks = 0;
		}
		jumpTicks++;
		Player.turn(Player.yaw(), 90);
		Player.press(Player.Key.JUMP, true);
		if (Player.y() >= base.getY() + 1.0) {
			holdBlock();
			if (Player.place(base)) {
				Player.press(Player.Key.JUMP, false);
				base = null;
				return;
			}
		}
		if (jumpTicks > 25) {
			Player.press(Player.Key.JUMP, false);
			base = null;
		}
	}

	private void dig(BlockPos p) {
		Player.press(Player.Key.JUMP, false);
		base = null;
		Player.holdBestToolFor(p);
		Player.lookAt(p);
		Player.mine(p);
	}

	private void pickSide(BlockPos feet) {
		Player.press(Player.Key.JUMP, false);
		base = null;
		for (int k = 0; k < 4; k++) {
			Direction d = SIDES[(sideIdx + k) % 4];
			BlockPos c = feet.relative(d);
			String a = id(c), b = id(c.above()), below = id(c.below());
			if (bad(a) || bad(b) || FairProbe.lavaSeenNear(c) || FairProbe.lavaSeenNear(c.above())) continue;
			if (below != null && (passable(below) || bad(below))) continue;
			side = c;
			sideIdx = (sideIdx + k + 1) % 4;
			sideTicks = 0;
			return;
		}
		fail(Fail.HAZARD, "lava, bedrock or hard rock above and on every side at y " + feet.getY());
	}

	private void stepSide(BlockPos feet) {
		if (feet.getX() == side.getX() && feet.getZ() == side.getZ()) {
			Player.press(Player.Key.FORWARD, false);
			side = null;
			return;
		}
		if (++sideTicks > 20 * 6) {
			Player.press(Player.Key.FORWARD, false);
			side = null;
			pickSide(feet);
			return;
		}
		String b = id(side.above()), a = id(side);
		if (b != null && !passable(b)) { Player.press(Player.Key.FORWARD, false); dig(side.above()); return; }
		if (a != null && !passable(a)) { Player.press(Player.Key.FORWARD, false); dig(side); return; }
		Vec3 c = Vec3.atBottomCenterOf(side).add(0, 1.6, 0);
		Player.lookAt(c);
		Player.press(Player.Key.FORWARD, true);
	}
}
