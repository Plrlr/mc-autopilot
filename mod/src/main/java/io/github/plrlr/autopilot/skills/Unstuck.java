package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Gets the bot out of a spot it has stopped moving in (Autopilot's box check: the bot stayed inside
 * a small square while a moving skill ran). Plain player controls, cheapest step first:
 *
 *   SWIM    in water: hold jump and swim toward the nearest dry ground it can see
 *   WALK    jump and walk out, trying each of the four directions
 *   TUNNEL  mine a 2-high way through whatever is in front (never into lava or water)
 *   PILLAR  in a hole: jump and place a block underneath, mining the block overhead
 *
 * Done as soon as the bot is out of the box (moved `stuck.box` blocks, or 2 up). If every step
 * fails, it fails with STUCK and the planner moves on to something else. Which step worked is in
 * the result, so the logs (and the learned brain) see what gets it out of what.
 *
 * Gene nav.unstuck_v2 adds CLIMB (StairUp, 2 steps: out of the box by height) and reorders:
 * in a pit or under a roof, CLIMB comes first; otherwise WALK, CLIMB, then TUNNEL. Gens 57-58: 41
 * unstuck tries timed out at 45 s mostly while tunnelling sideways out of a covered shelter shaft,
 * where one staircase step up was the way out.
 */
public final class Unstuck extends Skill {
	private enum Step { SWIM, WALK, CLIMB, TUNNEL, PILLAR }

	private Step step;
	private Vec3 start;
	private int stepTicks;
	private int dirIndex;
	private Direction[] dirs;
	private Direction landDir;
	private BlockPos breaking;
	private int tunnelled;
	private int placed;
	private boolean v2, climbed;
	private StairUp climb;

	@Override
	public String name() {
		return "unstuck";
	}

	@Override
	public boolean interruptible() {
		return false;
	}

	@Override
	protected void start() {
		Bari.stop();
		LocalPlayer pl = Mc.player();
		start = pl.position();
		timeoutTicks = 20 * 45;
		// Try the way we were facing first, then the others.
		Direction facing = pl.getDirection();
		dirs = new Direction[]{facing, facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite()};
		landDir = pl.isInWater() ? nearestLand(pl.blockPosition()) : null;
		v2 = Tune.on("nav.unstuck_v2");
		if (v2) timeoutTicks = 20 * 60;
		step = pl.isInWater() ? Step.SWIM : v2 && boxedAbove(pl.blockPosition()) ? Step.CLIMB : Step.WALK;
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		if (outOfBox(pl)) {
			done("got out (" + step.name().toLowerCase() + ")");
			return;
		}
		stepTicks++;
		var o = Mc.mc().options;
		switch (step) {
			case SWIM -> {
				if (landDir != null) face(landDir);
				o.keyJump.setDown(true);
				o.keyUp.setDown(true);
				if (stepTicks > 20 * 6) next(Step.WALK);
			}
			case WALK -> {
				// 1.5 s per direction, jumping the whole time (steps up one block, hops out of dips).
				face(dirs[dirIndex]);
				o.keyUp.setDown(true);
				o.keyJump.setDown(true);
				if (stepTicks > 30) {
					releaseKeys();
					stepTicks = 0;
					if (++dirIndex >= dirs.length) {
						dirIndex = 0;
						next(v2 && !climbed ? Step.CLIMB : Step.TUNNEL);
					}
				}
			}
			case CLIMB -> climb();
			case TUNNEL -> tunnel(pl);
			case PILLAR -> pillar(pl);
		}
	}

	/** In a pit or under a roof: our head room is closed, or 3+ sides at head height are solid. */
	private static boolean boxedAbove(BlockPos feet) {
		if (Mc.solid(feet.above(2))) return true;
		int walls = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) if (Mc.solid(feet.above().relative(d))) walls++;
		return walls >= 3;
	}

	/** Two staircase steps up (2 blocks up is out of the box); then walk, or tunnel if walking was tried. */
	private void climb() {
		if (climb == null) {
			climbed = true;
			climb = new StairUp();
			climb.begin(memory, "2");
		}
		climb.update();
		if (climb.result() == null) return;
		climb = null;
		next(walkTried ? Step.TUNNEL : Step.WALK);
	}

	/** True once WALK has run (CLIMB came first in a pit, so WALK is next; else TUNNEL is). */
	private boolean walkTried;

	private void next(Step s) {
		if (step == Step.WALK) walkTried = true;
		if (climb != null && climb.result() == null) climb.abort(Fail.INTERRUPTED, "unstuck moved on");
		climb = null;
		releaseKeys();
		if (Mc.mc().gameMode != null) Mc.mc().gameMode.stopDestroyBlock();
		breaking = null;
		step = s;
		stepTicks = 0;
	}

	private boolean outOfBox(LocalPlayer pl) {
		Vec3 p = pl.position();
		double dx = p.x - start.x, dz = p.z - start.z;
		return Math.sqrt(dx * dx + dz * dz) >= Tune.get("stuck.box") || p.y - start.y >= 2 || (start.y - p.y >= 3 && pl.onGround());
	}

	/** Mine the two blocks in front (feet and head), then walk into the gap; up to 4 blocks deep. */
	private void tunnel(LocalPlayer pl) {
		Direction d = dirs[dirIndex];
		BlockPos feet = pl.blockPosition();
		BlockPos low = feet.relative(d), high = low.above();
		if (dangerous(low) || dangerous(high) || dangerous(low.relative(d)) || unbreakable(low) || unbreakable(high)
				|| stepTicks > 20 * 12) {
			// This way is lava, water, bedrock or too slow: next direction, or climb when all are tried.
			if (++dirIndex >= dirs.length) next(Step.PILLAR);
			else next(Step.TUNNEL);
			return;
		}
		BlockPos target = Mc.free(high) ? (Mc.free(low) ? null : low) : high;
		if (target == null) {
			face(d);
			Mc.mc().options.keyUp.setDown(true);
			if (stepTicks % 20 == 0 && ++tunnelled > 4) next(Step.PILLAR);
			return;
		}
		Mc.mc().options.keyUp.setDown(false);
		mine(target);
	}

	/** Jump, place a block below at the top of the jump, mine anything overhead; up to 6 blocks. */
	private void pillar(LocalPlayer pl) {
		BlockPos head = pl.blockPosition().above(2);
		if (!Mc.free(head)) {
			if (dangerous(head) || unbreakable(head)) {
				fail(Fail.STUCK, "boxed in: can't walk, tunnel or climb out");
				return;
			}
			mine(head);
			return;
		}
		// A jump needs ~3 blocks of headroom: with the block 3 up solid the hop stays too low for
		// the block underneath to free up, and nothing is ever placed ("climbed 0 blocks").
		BlockPos roof = head.above();
		if (pl.onGround() && !Mc.free(roof) && !dangerous(roof) && !unbreakable(roof)) {
			Mc.mc().options.keyJump.setDown(false);
			mine(roof);
			return;
		}
		if (placed >= 6 || stepTicks > 20 * 20) {
			fail(Fail.STUCK, "couldn't get out (walked, tunnelled and climbed " + placed + " blocks)");
			return;
		}
		if (!Mc.holdItem(Items2.matcher("throwaway")) && !Mc.holdItem(Items2.matcher("planks"))) {
			fail(Fail.STUCK, "stuck and no blocks to climb with");
			return;
		}
		pl.setXRot(90f);
		Mc.mc().options.keyJump.setDown(true);
		BlockPos below = pl.blockPosition().below();
		if (!pl.onGround() && Mc.free(below) && Mc.clearOfPlayer(below) && Mc.placeAt(below)) placed++;
	}

	private void mine(BlockPos p) {
		Mc.lookAt(Vec3.atCenterOf(p));
		if (!p.equals(breaking)) {
			NightSkills.Shelter.holdBestTool(Mc.state(p));
			breaking = p;
			Mc.mc().gameMode.startDestroyBlock(p, Direction.UP);
		} else {
			Mc.mc().gameMode.continueDestroyBlock(p, Direction.UP);
		}
		Mc.swing();
	}

	private static void face(Direction d) {
		Mc.player().setYRot(d.toYRot());
		Mc.player().setXRot(0f);
	}

	private static boolean dangerous(BlockPos p) {
		return Mc.state(p).liquid();
	}

	private static boolean unbreakable(BlockPos p) {
		return Mc.state(p).getDestroySpeed(Mc.player().level(), p) < 0;
	}

	/** The direction of the nearest dry, standable ground within 12 blocks at about our level. */
	private static Direction nearestLand(BlockPos from) {
		Direction best = null;
		int bestDist = Integer.MAX_VALUE;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int i = 1; i <= 12; i++) {
				BlockPos c = from.relative(d, i);
				boolean ground = false;
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos g = c.above(dy);
					if (Mc.solid(g) && !Mc.state(g.above()).liquid() && Mc.free(g.above())) ground = true;
				}
				if (ground) {
					if (i < bestDist) {
						bestDist = i;
						best = d;
					}
					break;
				}
			}
		}
		return best;
	}

	@Override
	protected void cleanup() {
		if (climb != null && climb.result() == null) climb.abort(Fail.INTERRUPTED, "unstuck ended");
		if (Mc.mc().gameMode != null) Mc.mc().gameMode.stopDestroyBlock();
		super.cleanup();
	}
}
