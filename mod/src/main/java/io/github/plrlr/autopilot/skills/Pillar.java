package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.state.Perception;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Raise the player out of melee reach, then dismantle the pillar one block at a time. */
public final class Pillar extends Skill {
	private enum Phase { RISE, WAIT, DESCEND }
	private Phase phase = Phase.RISE;
	private int baseY;
	private int placeTries;
	private BlockPos digging;
	private float previousHealth;

	public static boolean canPillar(int blocks, int melee, boolean ranged) {
		return blocks >= 3 && melee >= 2 && !ranged;
	}

	@Override public String name() { return "pillar"; }
	@Override public boolean interruptible() { return false; }
	@Override public boolean workingInPlace() { return true; }

	@Override protected void start() {
		timeoutTicks = 20 * 60 * 9;
		if (!Mc.dimension().equals("overworld")) { fail(Fail.WRONG_PLACE, "pillar is for overworld melee threats"); return; }
		if (Mc.count("throwaway") < 3) { fail(Fail.NEED_ITEM, "need three pillar blocks"); return; }
		Perception seen = Perception.look(32);
		if (ranged(seen)) { fail(Fail.HAZARD, "ranged or explosive threat in sight"); return; }
		BlockPos feet = Mc.player().blockPosition();
		if (!Mc.solid(feet.below()) || !Mc.free(feet.above(2)) || !Mc.free(feet.above(3)) || !Mc.free(feet.above(4))) {
			fail(Fail.NO_ROOM, "no safe floor or head room"); return;
		}
		baseY = feet.getY();
		previousHealth = Mc.player().getHealth();
		Bari.stop();
	}

	private static boolean ranged(Perception seen) {
		for (Perception.Seen mob : seen.mobs)
			if (mob.hostile() && (mob.type().equals("skeleton") || mob.type().equals("stray")
					|| mob.type().equals("bogged") || mob.type().equals("creeper"))) return true;
		return false;
	}

	@Override protected void tick() {
		var pl = Mc.player();
		var o = Mc.mc().options;
		BlockPos feet = pl.blockPosition();
		if (pl.getHealth() < previousHealth - 3) { fail(Fail.HAZARD, "hit while pillaring"); return; }
		previousHealth = pl.getHealth();
		switch (phase) {
			case RISE -> {
				if (feet.getY() >= baseY + 3 && pl.onGround()) { o.keyJump.setDown(false); phase = Phase.WAIT; return; }
				if (ticks > 100 || placeTries > 15) { fail(Fail.PLACE_FAILED, "could not rise three blocks"); return; }
				if (!Mc.holdItem(Items2.matcher("throwaway"))) { fail(Fail.NEED_ITEM, "ran out of pillar blocks"); return; }
				o.keyJump.setDown(true);
				BlockPos under = feet.below();
				if (!pl.onGround() && Mc.free(under) && Mc.clearOfPlayer(under) && ticks % 3 == 0) {
					pl.setXRot(90f);
					if (Mc.placeAt(under)) placeTries++;
				}
			}
			case WAIT -> {
				o.keyJump.setDown(false);
				Perception seen = Perception.look(16);
				if (ranged(seen)) { fail(Fail.HAZARD, "ranged threat found the pillar"); return; }
				if (pl.getHealth() < 16 && pl.getFoodData().needsFood() && Mc.count(Items2.matcher("food")) > 0) {
					Mc.holdItem(Items2.matcher("food")); pl.setXRot(-90f); o.keyUse.setDown(true);
					return;
				}
				o.keyUse.setDown(false);
				Perception.Seen mob = seen.nearestHostile();
				if (mob != null && mob.dist() <= 3.5 && Mc.canSee(mob.entity()) && pl.getAttackStrengthScale(0.5f) >= 0.9f) {
					CombatSkills.holdWeapon();
					Mc.lookAt(mob.entity().getBoundingBox().getCenter());
					Mc.mc().gameMode.attack(pl, mob.entity()); Mc.swing();
				}
				if ((!Mc.isNight() && seen.hostilesWithin(10) == 0) || (ticks > 20 * 30 && seen.hostilesWithin(10) == 0)) phase = Phase.DESCEND;
			}
			case DESCEND -> {
				if (feet.getY() <= baseY && pl.onGround()) { Facts.report("threat_cleared"); done("descended safely"); return; }
				BlockPos under = feet.below();
				if (Mc.free(under)) return;
				if (!under.equals(digging)) {
					NightSkills.Shelter.holdBestTool(Mc.state(under));
					digging = under;
					Mc.mc().gameMode.startDestroyBlock(under, Direction.UP);
				} else Mc.mc().gameMode.continueDestroyBlock(under, Direction.UP);
				Mc.lookAt(net.minecraft.world.phys.Vec3.atCenterOf(under)); Mc.swing();
			}
		}
	}

	@Override protected void cleanup() {
		if (Mc.mc().gameMode != null) Mc.mc().gameMode.stopDestroyBlock();
		super.cleanup();
	}
}
