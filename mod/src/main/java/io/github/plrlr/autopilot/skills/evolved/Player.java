package io.github.plrlr.autopilot.skills.evolved;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.skills.Bari;
import io.github.plrlr.autopilot.skills.EvolvedActions;
import io.github.plrlr.autopilot.skills.FairProbe;
import io.github.plrlr.autopilot.skills.Skill;
import io.github.plrlr.autopilot.state.Perception;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Fair player controls and observations, without exposing client or world objects. */
public final class Player {
	private Player() {}

	public enum Key { FORWARD, BACK, LEFT, RIGHT, JUMP, SNEAK, SPRINT, ATTACK, USE }
	public record Mob(String type, Vec3 pos, double dist, boolean hostile) {}

	public static void press(Key k, boolean down) {
		var o = Mc.mc().options;
		var key = switch (k) {
			case FORWARD -> o.keyUp;
			case BACK -> o.keyDown;
			case LEFT -> o.keyLeft;
			case RIGHT -> o.keyRight;
			case JUMP -> o.keyJump;
			case SNEAK -> o.keyShift;
			case SPRINT -> o.keySprint;
			case ATTACK -> o.keyAttack;
			case USE -> o.keyUse;
		};
		key.setDown(down);
	}

	public static void releaseAll() {
		try {
			EvolvedActions.stopMining();
		} finally {
			Skill.releaseKeys();
		}
	}

	public static void lookAt(BlockPos p) { Mc.lookAt(Vec3.atCenterOf(p)); }
	public static void lookAt(Vec3 v) { Mc.lookAt(v); }

	public static void turn(float yaw, float pitch) {
		if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("non-finite rotation");
		Mc.player().setYRot(yaw);
		Mc.player().setXRot(Math.max(-90, Math.min(90, pitch)));
	}

	public static boolean hold(String itemOrGroup) {
		return Mc.player() != null && Mc.holdItem(Items2.matcher(itemOrGroup));
	}

	public static void holdBestToolFor(BlockPos p) { EvolvedActions.holdBestToolFor(p); }
	public static boolean mine(BlockPos p) { return EvolvedActions.mine(p); }

	public static boolean place(BlockPos p) {
		if (!visible(p) || !inReach(Vec3.atCenterOf(p)) || !Mc.clearOfPlayer(p)) return false;
		// Fluids can also be replaced: plugging a visible source is an ordinary player move.
		var state = Mc.state(p);
		if (!state.isAir() && !state.canBeReplaced()) return false;
		// Mc.placeAt inspects every support in order. Choose only supports we can see, then
		// make the same ordinary right-click, so hidden neighbors never leak through this facade.
		for (Direction d : Direction.values()) {
			BlockPos against = p.relative(d);
			if (visible(against) && Mc.solid(against) && !Mc.isInteractive(against)
					&& useOn(against, d.getOpposite())) return true;
		}
		return false;
	}

	public static boolean useOn(BlockPos p, Direction face) {
		Vec3 hit = Vec3.atCenterOf(p).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		return visible(p) && inReach(hit) && Mc.mc().gameMode != null && Mc.useOn(p, face);
	}

	public static boolean useItem() {
		return Mc.player() != null && Mc.mc().gameMode != null && Mc.useItem();
	}

	/** The caller supplies a seen or remembered destination; Baritone never searches for it. */
	public static void walkTo(BlockPos p) { Bari.path(new GoalBlock(p)); }
	public static void walkNear(BlockPos p, int radius) { Bari.path(new GoalNear(p, Math.max(0, radius))); }
	public static boolean walking() { return Bari.pathing(); }
	public static void stopWalking() { Bari.stop(); }

	public static Vec3 pos() { return Mc.player().position(); }
	public static BlockPos feet() { return Mc.player().blockPosition(); }
	public static double y() { return Mc.player().getY(); }
	public static float health() { return Mc.player().getHealth(); }
	public static int food() { return Mc.player().getFoodData().getFoodLevel(); }
	public static int air() { return Mc.player().getAirSupply(); }
	public static boolean inWater() { return Mc.player().isInWater(); }
	public static boolean eyesInWater() { return Mc.player().isUnderWater(); }
	public static boolean inLava() { return Mc.player().isInLava(); }
	public static boolean onGround() { return Mc.player().onGround(); }
	public static float yaw() { return Mc.player().getYRot(); }
	public static float pitch() { return Mc.player().getXRot(); }
	public static int count(String itemOrGroup) { return Mc.count(itemOrGroup); }
	public static boolean isNight() { return Mc.isNight(); }
	public static String dimension() { return Mc.dimension(); }

	/** World ticks keep cooldowns in game time, including across respawns and dimension changes. */
	public static double gameSeconds() {
		return Mc.player() == null ? 0 : Mc.player().level().getGameTime() / 20.0;
	}

	public static double reach() { return Mc.reach(); }
	public static boolean visible(BlockPos p) { return Mc.player() != null && Mc.canSee(p); }
	public static String blockIfVisible(BlockPos p) { return visible(p) ? Mc.id(Mc.state(p).getBlock()) : null; }
	public static FairProbe probe() { return new FairProbe(); }

	public static List<Mob> mobs(double range) {
		List<Mob> out = new ArrayList<>();
		for (Perception.Seen mob : Perception.look(range).mobs) {
			// Perception also hears close threats through walls and keeps distant dragons.
			// Evolved skills get only mobs within the requested range that are actually in sight.
			if (mob.dist() <= range && Mc.canSee(mob.entity()))
				out.add(new Mob(mob.type(), mob.entity().position(), mob.dist(), mob.hostile()));
		}
		return List.copyOf(out);
	}

	public static Mob nearestHostile(double range) {
		for (Mob mob : mobs(range)) if (mob.hostile()) return mob;
		return null;
	}

	private static boolean inReach(Vec3 v) {
		return Mc.player() != null && Mc.player().getEyePosition().distanceTo(v) <= reach();
	}
}
