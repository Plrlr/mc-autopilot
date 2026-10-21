package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ShelterDigOut extends EvolvedSkill {
	private static final List<Direction> DIRS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
	private int dirIdx;
	private int steps;
	private int stall;
	private int lastY;
	private int turns;

	public static Option offer(Context c) {
		if (c.lastSkill() == null) return null;
		boolean afterShelter = "shelter".equals(c.lastSkill()) && "STUCK".equals(c.lastCode());
		boolean resume = "shelter_dig_out".equals(c.lastSkill()) && "INTERRUPTED".equals(c.lastCode());
		if (!afterShelter && !resume) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 180) return null;
		if (!"overworld".equals(Player.dimension()) || Player.inLava()) return null;
		return new Option("shelter_dig_out", null, "shelter couldn't climb out: dig stairs up");
	}

	@Override
	public String name() { return "shelter_dig_out"; }

	private static boolean air(String id) {
		return id != null && (id.equals("air") || id.equals("cave_air") || id.equals("void_air"));
	}

	private static boolean passable(String id) {
		return air(id) || "water".equals(id);
	}

	private static boolean skyAbove(BlockPos feet) {
		for (int i = 2; i <= 9; i++) {
			if (!air(Player.blockIfVisible(feet.above(i)))) return false;
		}
		return true;
	}

	@Override
	protected void start() {
		Player.stopWalking();
		BlockPos f = Player.feet();
		lastY = f.getY();
		// Prefer a side whose step floor we can see is solid (needs no block).
		dirIdx = 0;
		for (int i = 0; i < 4; i++) {
			String id = Player.blockIfVisible(f.relative(DIRS.get(i)));
			if (id == null || (!passable(id) && !"lava".equals(id))) { dirIdx = i; break; }
		}
	}

	@Override
	protected void tick() {
		BlockPos f = Player.feet();
		if (steps >= 1 && skyAbove(f) && Player.onGround()) { done("climbed out after " + steps + " steps"); return; }
		BlockPos entry = memory.surfaceEntry();
		if (steps >= 3 && entry != null && f.getY() >= entry.getY() && Player.onGround()) {
			done("back at surface level after " + steps + " steps"); return;
		}
		if (steps > 40) { fail(Fail.NO_PROGRESS, "40 steps up and still no sky"); return; }
		if (f.getY() > lastY && Player.onGround()) {
			steps += f.getY() - lastY;
			lastY = f.getY();
			stall = 0;
			Player.releaseAll();
			return;
		}
		if (f.getY() < lastY) lastY = f.getY();
		Direction d = DIRS.get(dirIdx);
		BlockPos[] clear = {f.above(2), f.relative(d).above(), f.relative(d).above(2)};
		for (BlockPos p : clear) {
			String id = Player.blockIfVisible(p);
			if ("lava".equals(id)) { turn("lava at " + p); return; }
			if (passable(id)) continue;
			Player.press(Player.Key.FORWARD, false);
			Player.press(Player.Key.JUMP, false);
			if (id == null) {
				// Not visible yet: the block before it hides it; count as stalled.
				if (++stall > 60) turn("can't see " + p);
				return;
			}
			Player.lookAt(p);
			Player.holdBestToolFor(p);
			if (!Player.mine(p) && ++stall > 100) turn("couldn't mine " + p);
			else if (stall > 400) turn("mining took too long");
			return;
		}
		BlockPos floor = f.relative(d);
		String fid = Player.blockIfVisible(floor);
		if ("lava".equals(fid)) { turn("lava in the step"); return; }
		if (passable(fid)) {
			if (Player.count("throwaway") > 0 && Player.hold("throwaway")) {
				Player.lookAt(floor);
				if (!Player.place(floor) && ++stall > 40) turn("step floor won't place");
			} else turn("no floor for the step");
			return;
		}
		BlockPos step = f.relative(d).above();
		Vec3 c = Vec3.atCenterOf(step);
		Player.lookAt(new Vec3(c.x, Player.pos().y + 1.6, c.z));
		Player.press(Player.Key.FORWARD, true);
		Player.press(Player.Key.JUMP, true);
		if (++stall > 60) turn("couldn't hop onto the step");
	}

	private void turn(String why) {
		Player.releaseAll();
		stall = 0;
		if (++turns > 8) { fail(Fail.STUCK, "no way up: " + why); return; }
		dirIdx = (dirIdx + 1) % 4;
	}
}
