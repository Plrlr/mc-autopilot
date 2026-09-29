package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * stair_up [steps|sky] [toward x z]: dig a 1-wide staircase up, the way a player climbs out of a
 * hole or back to the surface. Needs no blocks and no path search.
 *
 * Gens 57-58's logs: 18 of 35 failed "goto surface" were "couldn't find the way up" (Baritone's
 * search gave up within a second), mostly right after a night shelter ended with the bot at the
 * bottom of its covered 3-deep shaft; unstuck then spent 45 s walking and tunnelling sideways
 * (41 timeouts). Each step: clear the head room above us, stand the step ahead (placing a block
 * if it's air and we carry one), clear the two cells above the step, jump onto it. Water or lava
 * showing in a cell we'd open turns us to another side; a falling block is dug again.
 * Done under open sky, after `steps` steps, or when there's no side left to climb.
 */
public final class StairUp extends Skill {
	private final Act.Breaker breaker = new Act.Breaker();
	private Direction dir;
	private int maxSteps = 64, steps, turns, jumpTicks, cellTicks;
	private BlockPos next, toward;

	@Override
	public String name() {
		return "stair_up";
	}

	@Override
	public boolean workingInPlace() {
		return true;
	}

	@Override
	protected void start() {
		// Up to 64 steps of about 2 s each, plus turns: 90 s gave up 10 blocks into a 60-block climb
		// (local run 4 on night2, 2026-09-29: y 36 -> 46, then "stairs: timed out").
		timeoutTicks = 20 * 240;
		String[] a = arg == null ? new String[0] : arg.trim().split("\\s+");
		for (int i = 0; i < a.length; i++) {
			if (a[i].matches("\\d+")) maxSteps = Integer.parseInt(a[i]);
			if (a[i].equals("toward") && i + 2 < a.length) {
				try {
					toward = new BlockPos(Integer.parseInt(a[i + 1]), 0, Integer.parseInt(a[i + 2]));
				} catch (NumberFormatException ignored) {
				}
			}
		}
		Bari.stop();
		LocalPlayer pl = Mc.player();
		if (underSky(pl)) {
			done("already under open sky");
			return;
		}
		dir = firstDirection(pl.blockPosition());
		if (!pickDirection()) fail(Fail.HAZARD, "no side to climb");
	}

	private static boolean underSky(LocalPlayer pl) {
		return pl.level().canSeeSky(pl.blockPosition().above());
	}

	/** Toward the target if we have one (the way we came down), else the way we face. */
	private Direction firstDirection(BlockPos feet) {
		if (toward == null) return Mc.player().getDirection();
		int dx = toward.getX() - feet.getX(), dz = toward.getZ() - feet.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** The cells a step opens: our head room, then the body and head cells above the step. */
	static BlockPos[] cut(BlockPos feet, Direction dir) {
		BlockPos step = feet.relative(dir);
		return new BlockPos[]{feet.above(2), step.above(), step.above(2)};
	}

	/** Fluid in a cell we'd open (only cells next to us, which a player sees), or bedrock. */
	private static boolean safe(BlockPos feet, Direction dir) {
		for (BlockPos c : cut(feet, dir)) {
			if (FairProbe.lavaSeenNear(c)) return false;
			if (Mc.canSee(c) && (!Mc.state(c).getFluidState().isEmpty()
					|| Mc.state(c).getDestroySpeed(Mc.player().level(), c) < 0)) return false;
		}
		BlockPos step = feet.relative(dir);
		if (!Mc.canSee(step)) return true; // Rechecked once digging exposes the step.
		if (!Mc.state(step).getFluidState().isEmpty()) return false;
		// The step must hold us: solid already, or air we can fill with a block.
		return Mc.solid(step) || Mc.count(io.github.plrlr.autopilot.Items2.matcher("throwaway")) > 0;
	}

	private boolean pickDirection() {
		BlockPos feet = Mc.player().blockPosition();
		for (int i = 0; i < 4; i++) {
			if (safe(feet, dir)) return true;
			dir = dir.getClockWise();
		}
		return false;
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		BlockPos feet = pl.blockPosition();
		if (underSky(pl) && pl.onGround()) {
			breaker.stop();
			done("climbed " + steps + " steps to open sky");
			return;
		}
		if (steps >= maxSteps && pl.onGround()) {
			breaker.stop();
			done("climbed " + steps + " steps");
			return;
		}
		var o = Mc.mc().options;
		if (next != null) {
			// Jumping onto the step: face it, hold forward and jump until our feet are on it.
			pl.setYRot(dir.toYRot());
			pl.setXRot(0f);
			o.keyUp.setDown(true);
			o.keyJump.setDown(true);
			if (feet.equals(next) && pl.onGround()) {
				releaseKeys();
				next = null;
				jumpTicks = 0;
				steps++;
			} else if (++jumpTicks > 40) {
				releaseKeys();
				next = null;
				jumpTicks = 0;
				if (++turns > 8) {
					fail(Fail.STUCK, "couldn't step up at y " + feet.getY());
					return;
				}
				dir = dir.getClockWise();
			}
			return;
		}
		if (!safe(feet, dir)) {
			if (++turns > 8 || !pickDirection()) {
				fail(Fail.HAZARD, "water, lava or bedrock on every side at y " + feet.getY());
				return;
			}
		}
		for (BlockPos c : cut(feet, dir)) {
			if (!Mc.free(c)) {
				// Gravel and sand fall back in: each cell gets a fresh 12 s, the skill its timeout.
				if (!c.equals(lastCell)) {
					lastCell = c;
					cellTicks = 0;
				}
				if (++cellTicks > 20 * 12) {
					fail(Fail.NO_PROGRESS, "block too hard to dig");
					return;
				}
				breaker.tick(c);
				return;
			}
		}
		BlockPos step = feet.relative(dir);
		if (!Mc.solid(step)) {
			// A gap where the step should be: fill it, as a player does.
			if (!Act.place(step)) {
				dir = dir.getClockWise();
				if (++turns > 8) fail(Fail.PLACE_FAILED, "couldn't fill the step");
			}
			return;
		}
		breaker.stop();
		next = step.above();
	}

	private BlockPos lastCell;

	@Override
	protected void cleanup() {
		breaker.stop();
		releaseKeys();
		super.cleanup();
	}
}
