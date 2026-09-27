package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

/**
 * enderman_boat: the speedrunners' enderman trap. A mob that walks into a boat is stuck in it, and
 * an enderman in a boat can't move, teleport or hit back (minecraft.wiki, Tutorial:Speedrun). So:
 *
 *   PLACE    put our boat on the ground right beside us, on the enderman's side
 *   PROVOKE  stare at its eyes: it turns hostile and comes for us, into the boat
 *   KILL     hit it while it sits there (crits when that gene is on)
 *   PEARL    pick up what it dropped
 *   RECOVER  break the boat and pick it up again: one boat does every enderman
 *
 * If it reaches us without getting into the boat, it's a normal fight to the end.
 */
public final class EndermanBoat extends Skill {
	private enum Phase {PLACE, PROVOKE, KILL, FIGHT, PEARL, RECOVER}

	private Phase phase = Phase.PLACE;
	private Enderman target;
	private AbstractBoat boat;
	private BlockPos spot;
	private Vec3 deathAt;
	private int phaseTicks, critWait;

	@Override
	public String name() {
		return "enderman_boat";
	}

	@Override
	public boolean interruptible() {
		return false;
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 90;
		if (Mc.count(Items2.matcher("boat")) == 0) {
			fail(Fail.NEED_ITEM, "no boat");
			return;
		}
		double best = 32;
		for (Entity e : Mc.mc().level.entitiesForRendering()) {
			if (e instanceof Enderman en && en.isAlive() && Mc.canSee(en) && en.distanceTo(Mc.player()) < best) {
				best = en.distanceTo(Mc.player());
				target = en;
			}
		}
		if (target == null) fail(Fail.NOT_FOUND, "no enderman in sight");
		Bari.stop();
	}

	/** An enderman in sight within 32 blocks: the planner's check, the same as the skill's. */
	public static boolean endermanInSight() {
		for (Entity e : Mc.mc().level.entitiesForRendering())
			if (e instanceof Enderman en && en.isAlive() && Mc.canSee(en) && en.distanceTo(Mc.player()) < 32) return true;
		return false;
	}

	private void go(Phase p) {
		phase = p;
		phaseTicks = 0;
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		phaseTicks++;
		var o = Mc.mc().options;
		switch (phase) {
			case PLACE -> {
				if (boat == null) boat = boatNear(pl.position(), 3);
				if (boat != null) {
					go(Phase.PROVOKE);
					return;
				}
				if (phaseTicks > 60) {
					fail(Fail.PLACE_FAILED, "couldn't put the boat down");
					return;
				}
				if (spot == null) spot = boatSpot(pl);
				if (spot == null) {
					fail(Fail.NO_ROOM, "no flat spot beside us for the boat");
					return;
				}
				if (!Items2.matcher("boat").test(pl.getMainHandItem())) {
					Mc.holdItem(Items2.matcher("boat"));
					return;
				}
				// A boat goes where the crosshair points (BoatItem traces the view itself): look at the
				// top of the ground block, then use the boat. useOn against a face didn't place it.
				Mc.lookAt(Vec3.atCenterOf(spot.below()).add(0, 0.5, 0));
				if (phaseTicks % 10 == 5) {
					var r = Mc.mc().gameMode.useItem(pl, net.minecraft.world.InteractionHand.MAIN_HAND);
					// What the game made of it: the result and what the crosshair was on.
					io.github.plrlr.autopilot.AutopilotMod.LOGGER.info("[boat] use -> {} at {} hand {} crosshair {}", r, spot.toShortString(),
							Items2.id(pl.getMainHandItem()), Mc.mc().hitResult == null ? "none" : Mc.mc().hitResult.getType()
							+ (Mc.mc().hitResult instanceof net.minecraft.world.phys.BlockHitResult b ? " " + b.getBlockPos().toShortString() + " " + Mc.id(Mc.state(b.getBlockPos()).getBlock()) : ""));
				}
			}
			case PROVOKE -> {
				if (!target.isAlive()) {
					fail(Fail.NOT_FOUND, "the enderman went away");
					return;
				}
				if (target.getVehicle() instanceof AbstractBoat) {
					go(Phase.KILL);
					return;
				}
				// Staring at its eyes makes it come for us; the boat is in its way.
				Mc.lookAt(target.getEyePosition());
				boolean angry = target.isCreepy() || target.getTarget() == pl;
				if (phaseTicks % 40 == 0)
					io.github.plrlr.autopilot.AutopilotMod.LOGGER.info("[boat] provoking: angry {} dist {}", angry, Math.round(target.distanceTo(pl)));
				// Still calm after 8 s: step closer while staring (the angle it needs is tighter far away);
				// after 16 s, a hit makes it come for sure.
				if (!angry && phaseTicks == 20 * 8 && target.distanceTo(pl) > 6) Bari.path(new baritone.api.pathing.goals.GoalNear(target.blockPosition(), 5));
				if (!angry && phaseTicks > 20 * 16 && target.distanceTo(pl) < 4 && pl.getAttackStrengthScale(0.5f) > 0.9f) {
					Mc.mc().gameMode.attack(pl, target);
					Mc.swing();
				}
				if (target.distanceTo(pl) < 2.2 && phaseTicks > 20 * 3) go(Phase.FIGHT);
				if (phaseTicks > 20 * Tune.i("pearls.provoke_s")) fail(Fail.NOT_FOUND, "the enderman didn't come to the boat");
			}
			case KILL, FIGHT -> {
				if (!target.isAlive()) {
					deathAt = target.position();
					o.keyJump.setDown(false);
					go(Phase.PEARL);
					return;
				}
				double d = pl.distanceTo(target);
				if (phase == Phase.FIGHT && d > 3.2) {
					if (phaseTicks % 10 == 1) Bari.path(new GoalBlock(target.blockPosition()));
					if (phaseTicks > 20 * 30) fail(Fail.UNREACHABLE, "lost the enderman");
					return;
				}
				if (d > 4.5) return; // in the boat but a bit far: it can't come, and neither do we need to
				Bari.stop();
				if (!Items2.id(pl.getMainHandItem()).endsWith("_sword")) CombatSkills.holdWeapon();
				Mc.lookAt(target.getBoundingBox().getCenter());
				if (pl.getAttackStrengthScale(0.5f) < 0.95f) return;
				if (Tune.on("combat.crits") && critWait < 12) {
					critWait++;
					if (pl.onGround()) {
						o.keyJump.setDown(true);
						return;
					}
					o.keyJump.setDown(false);
					if (pl.getDeltaMovement().y >= 0) return;
				}
				critWait = 0;
				o.keyJump.setDown(false);
				Mc.mc().gameMode.attack(pl, target);
				Mc.swing();
			}
			case PEARL -> {
				ItemEntity pearl = null;
				for (Entity e : Mc.mc().level.entitiesForRendering())
					if (e instanceof ItemEntity it && Items2.id(it.getItem()).equals("ender_pearl") && it.position().distanceTo(deathAt) < 4) pearl = it;
				if (pearl == null || phaseTicks > 20 * 6) {
					if (phaseTicks < 15 && pearl == null) return; // the drop lands a moment later
					go(Phase.RECOVER);
					return;
				}
				if (phaseTicks % 10 == 1) Bari.path(new GoalBlock(pearl.blockPosition()));
			}
			case RECOVER -> {
				// Break the boat (a few hits) and take it along for the next enderman.
				if (boat != null && boat.isAlive()) {
					if (pl.distanceTo(boat) > 3) {
						if (phaseTicks % 10 == 1) Bari.path(new GoalBlock(boat.blockPosition()));
					} else {
						Mc.lookAt(boat.getBoundingBox().getCenter());
						if (phaseTicks % 5 == 0) {
							Mc.mc().gameMode.attack(pl, boat);
							Mc.swing();
						}
					}
					if (phaseTicks > 20 * 10) done("killed the enderman; left the boat");
					return;
				}
				ItemEntity drop = null;
				for (Entity e : Mc.mc().level.entitiesForRendering())
					if (e instanceof ItemEntity it && Items2.matcher("boat").test(it.getItem()) && it.distanceTo(pl) < 8) drop = it;
				if (drop == null || phaseTicks > 20 * 14) {
					done("killed the enderman in the boat" + (Mc.count(Items2.matcher("boat")) > 0 ? ", boat back" : ""));
					return;
				}
				if (phaseTicks % 10 == 1) Bari.path(new GoalBlock(drop.blockPosition()));
			}
		}
	}

	/** A flat, open block beside us toward the enderman (else any side), with solid ground under it. */
	private BlockPos boatSpot(LocalPlayer pl) {
		BlockPos feet = pl.blockPosition();
		Vec3 to = target.position().subtract(pl.position());
		Direction first = Direction.getApproximateNearest(to.x, 0, to.z);
		Direction[] order = {first, first.getClockWise(), first.getCounterClockWise(), first.getOpposite()};
		// Two blocks out, on clean ground: a tuft of grass in our own square caught the crosshair and
		// the boat failed to place (the boat test's log: 'use -> Fail, crosshair short_grass').
		for (Direction d : order) {
			BlockPos p = feet.relative(d, 2), mid = feet.relative(d);
			if (Mc.state(p).isAir() && Mc.state(p.above()).isAir() && Mc.solid(p.below()) && !Mc.solid(mid) && Mc.state(mid).isAir()
					&& Mc.state(feet).isAir()) return p;
		}
		for (Direction d : order) {
			BlockPos p = feet.relative(d, 2);
			if (Mc.state(p).isAir() && Mc.state(p.above()).isAir() && Mc.solid(p.below())) return p;
		}
		return null;
	}

	private static AbstractBoat boatNear(Vec3 at, double r) {
		for (Entity e : Mc.mc().level.entitiesForRendering())
			if (e instanceof AbstractBoat b && b.isAlive() && b.position().distanceTo(at) < r) return b;
		return null;
	}

	@Override
	protected void cleanup() {
		Mc.mc().options.keyJump.setDown(false);
		super.cleanup();
	}
}
