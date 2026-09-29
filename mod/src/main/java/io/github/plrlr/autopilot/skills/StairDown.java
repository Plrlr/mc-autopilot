package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.plan.Facts;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * stair_down &lt;y&gt;: dig a 1-wide staircase down to y, the way a careful player goes deep.
 *
 * 13 fall deaths and 11 lava deaths in generations 41-46; Baritone digs straight down or drops.
 * Each step clears three blocks ahead (head, body, the step's feet) and checks, before stepping,
 * that the block under the step is solid and that no lava touches the cells we open. Lava or a
 * drop ahead: turn to another direction (a player looks before stepping). A torch every 8 steps.
 */
public final class StairDown extends Skill {
	private final Act.Breaker breaker = new Act.Breaker();
	private Direction dir;
	private int targetY, steps, turns, walk;
	private int stoneBefore = -1, stoneWant;
	private BlockPos next;
	private CaveSeal caveSeal;

	@Override
	public String name() {
		return "stair_down";
	}

	@Override
	public boolean workingInPlace() {
		return true;
	}

	/** The 3 blocks to clear for a step from feet going dir: ahead at head, body, and the step down. */
	static BlockPos[] cut(BlockPos feet, Direction dir) {
		BlockPos a = feet.relative(dir);
		return new BlockPos[]{a.above(), a, a.below()};
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60 * 6;
		try {
			if (arg != null && arg.startsWith("stone:")) {
				stoneWant = Integer.parseInt(arg.substring(6));
				stoneBefore = Mc.count("stone");
				targetY = Math.max(Mc.player().level().getMinY() + 5, Mc.player().getBlockY() - 12);
			} else targetY = Integer.parseInt(argName().trim());
		} catch (NumberFormatException e) {
			fail(Fail.ERROR, "stair_down needs a y level");
			return;
		}
		if (Mc.player().getBlockY() <= targetY) {
			done("already at y " + Mc.player().getBlockY());
			return;
		}
		Bari.stop();
		if (Tune.on("cave.seal_openings")) caveSeal = new CaveSeal(new net.minecraft.world.level.block.Block[0]);
		dir = Mc.player().getDirection();
		if (!pickDirection()) fail(Fail.HAZARD, "no safe direction to dig down");
	}

	/** A direction whose next step is safe to dig; false if none of the four is. */
	private boolean pickDirection() {
		BlockPos feet = Mc.player().blockPosition();
		for (int i = 0; i < 4; i++) {
			if (safe(feet, dir)) return true;
			dir = dir.getClockWise();
		}
		return false;
	}

	/** Safe: no lava touching the cells we'd open, solid ground under the step, no bedrock. */
	private static boolean safe(BlockPos feet, Direction dir) {
		for (BlockPos c : cut(feet, dir)) {
			if (Act.lavaNear(c) || !Mc.state(c).getFluidState().isEmpty()) return false;
			if (Mc.id(Mc.state(c).getBlock()).equals("bedrock")) return false;
		}
		BlockPos floor = feet.relative(dir).below(2);
		return Mc.solid(floor) && Mc.state(floor).getFluidState().isEmpty();
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		BlockPos feet = pl.blockPosition();
		if (caveSeal != null) {
			CaveSeal.Status seal = caveSeal.tick();
			if (seal == CaveSeal.Status.FAILED) { fail(Fail.PLACE_FAILED, "couldn't close a cave opening on the stairs"); return; }
			if (seal == CaveSeal.Status.BUSY) { breaker.stop(); Mc.mc().options.keyUp.setDown(false); return; }
			if (seal == CaveSeal.Status.SEALED) {
				// The next step would reopen the wall, so turn the staircase away from the cave.
				breaker.stop();
				Mc.mc().options.keyUp.setDown(false);
				next = null;
				dir = dir.getClockWise();
				turns++;
				return;
			}
		}
		if (stoneBefore >= 0 && Mc.count("stone") - stoneBefore >= stoneWant) {
			breaker.stop();
			done("mined enough stone on the stairs");
			return;
		}
		if (feet.getY() <= targetY && pl.onGround()) {
			breaker.stop();
			Facts.report("at_depth");
			done("reached y " + feet.getY() + " by stairs");
			return;
		}
		if (next != null) {
			// Walking down onto the step.
			pl.setYRot(dir.toYRot());
			Mc.mc().options.keyUp.setDown(!feet.equals(next));
			if (feet.equals(next) || ++walk > 30) {
				Mc.mc().options.keyUp.setDown(false);
				if (!feet.equals(next)) {
					fail(Fail.STUCK, "couldn't step down");
					return;
				}
				next = null;
				walk = 0;
				if (++steps % 8 == 0) Act.torch(feet.relative(dir.getOpposite()).above());
			}
			return;
		}
		if (!safe(feet, dir)) {
			if (++turns > 8 || !pickDirection()) {
				fail(Fail.HAZARD, "lava or a drop on every side at y " + feet.getY());
				return;
			}
		}
		for (BlockPos c : cut(feet, dir)) {
			if (!Mc.free(c)) {
				if (breaker.ticks() > 20 * 12) {
					fail(Fail.NO_PROGRESS, "block too hard to dig");
					return;
				}
				breaker.tick(c);
				return;
			}
		}
		next = feet.relative(dir).below();
	}

	@Override
	protected void cleanup() {
		breaker.stop();
		super.cleanup();
	}
}
