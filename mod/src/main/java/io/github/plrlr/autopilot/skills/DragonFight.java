package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * dragon: the End fight the way players do it, deterministic (code does mechanics, the brain only
 * picks when). The dragon's pattern is fixed: it circles, now and then lands on the fountain in the
 * middle of the island, sits there a while (breathing purple acid), and takes off again. So:
 *
 *   - stay safe: never near the island's edge (the dragon's hits knock you into the void; a test run
 *     died that way), never in the breath cloud, and within reach of the fountain;
 *   - while it circles: wait a few blocks from the fountain, shield up when it's close;
 *   - when it sits: walk to its head and strike the head (full damage; the body takes a quarter),
 *     with critical hits (jump, strike on the way down) when the gene allows.
 *
 * Crystals are the planner's step before this one (shoot end_crystal while any are in sight).
 * Everything here is what a player sees: the dragon's phase and body are sent to every client.
 */
public final class DragonFight extends Skill {
	private int critWait;
	private int hits;
	private String doing = "";

	@Override
	public String name() {
		return "dragon";
	}

	@Override
	public boolean interruptible() {
		return false;
	}

	@Override
	public boolean ownsSafety() {
		return true;
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60 * 15;
		if (!Mc.dimension().equals("the_end")) {
			fail(Fail.WRONG_PLACE, "the dragon is in the End");
			return;
		}
		CombatSkills.holdWeapon();
	}

	private static EnderDragon dragon() {
		for (Entity e : Mc.mc().level.entitiesForRendering()) {
			if (e instanceof EnderDragon d && d.isAlive()) return d;
		}
		return null;
	}

	/** The fountain's top in the middle of the island (x 0, z 0). */
	private static BlockPos fountain() {
		int y = Mc.player().level().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
		return new BlockPos(0, y, 0);
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		EnderDragon d = dragon();
		var o = Mc.mc().options;
		if (d == null || d.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
			if (ticks > 40) {
				done("the dragon is dead (" + hits + " hits)");
				return;
			}
			return;
		}
		BlockPos center = fountain();
		// 1. Safety: the void and the breath come before any attack.
		if (nearEdge(pl.blockPosition()) || horizontal(pl.position(), center) > Tune.get("dragon.max_center_dist")) {
			say("back toward the middle");
			o.keyJump.setDown(false);
			o.keyDown.setDown(false);
			if (!Bari.pathing()) Bari.path(new GoalNear(center, 4));
			return;
		}
		AreaEffectCloud cloud = breathNear(pl);
		if (cloud != null) {
			say("out of the dragon's breath");
			Vec3 away = pl.position().subtract(cloud.position()).multiply(1, 0, 1).normalize().scale(6);
			Bari.path(new GoalNear(BlockPos.containing(pl.position().add(away)), 1));
			return;
		}
		var phase = d.getPhaseManager().getCurrentPhase();
		boolean down = phase.isSitting() || phase.getPhase() == EnderDragonPhase.LANDING;
		// Eat while it's in the air (the test runs sat at 5 health with 32 steak in the bag).
		boolean hungry = pl.getFoodData().getFoodLevel() < 20 && Mc.count(Items2::isAnyFood) > 0;
		if (hungry && pl.getHealth() <= Tune.i("dragon.eat_hp") && (!down || pl.distanceTo(d.head) > 8)) {
			say("eating");
			if (Bari.pathing()) Bari.stop();
			if (!Items2.isAnyFood(pl.getMainHandItem())) Mc.holdItem(Items2::isAnyFood);
			Mc.mc().options.keyUse.setDown(true);
			return;
		}
		if (!Items2.id(pl.getMainHandItem()).endsWith("_sword")) {
			Mc.mc().options.keyUse.setDown(false);
			CombatSkills.holdWeapon();
		}
		if (!down) {
			// 2. It's in the air: wait near the fountain for it to land, shield toward it when close.
			say("waiting for it to land");
			o.keyJump.setDown(false);
			// Wait a little way out: the fountain is where it lands and breathes (the first fight
			// stood right beside it and lost most of its health there).
			double wait = Tune.get("dragon.wait_dist");
			double toSpot = horizontal(pl.position(), center);
			if (toSpot > wait + 3 || toSpot < wait - 3) {
				int x = (int) Math.round(wait);
				int y = pl.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, 0);
				if (!Bari.pathing()) Bari.path(new GoalNear(new BlockPos(x, y, 0), 1));
				Mc.mc().options.keyUse.setDown(false);
				return;
			}
			if (Bari.pathing()) Bari.stop();
			Mc.lookAt(d.position());
			boolean shield = Items2.id(pl.getOffhandItem()).equals("shield") && pl.distanceTo(d) < 24;
			Mc.mc().options.keyUse.setDown(shield);
			return;
		}
		// 3. It's sitting on the fountain: go to the head and hit it.
		Mc.mc().options.keyUse.setDown(false);
		Entity head = d.head;
		double dist = pl.distanceTo(head);
		if (dist > 3.2) {
			say("to the dragon's head");
			// The head hangs over the fountain's edge: any standing spot within 2 blocks of it will do.
			if (!Bari.pathing() || ticks % 20 == 0) Bari.path(new GoalNear(BlockPos.containing(head.position()), 2));
			return;
		}
		if (Bari.pathing()) Bari.stop();
		say("hitting the head");
		Mc.lookAt(head.getBoundingBox().getCenter());
		if (pl.getAttackStrengthScale(0.5f) < 0.95f) return;
		if (Tune.on("combat.crits") && critWait < 12 && !pl.isInWater()) {
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
		Mc.mc().gameMode.attack(pl, head);
		Mc.swing();
		hits++;
	}

	private void say(String what) {
		doing = what;
	}

	/** Any drop to the void (no block under a column within 12 blocks down) within 2 blocks. */
	private static boolean nearEdge(BlockPos feet) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean ground = false;
				for (int dy = 1; dy <= 12 && !ground; dy++) {
					if (Mc.solid(feet.offset(dx, -dy, dz))) ground = true;
				}
				if (!ground) return true;
			}
		}
		return false;
	}

	private static AreaEffectCloud breathNear(LocalPlayer pl) {
		for (Entity e : Mc.mc().level.entitiesForRendering()) {
			if (e instanceof AreaEffectCloud c && horizontal(c.position(), pl.blockPosition()) < c.getRadius() + 1.5
					&& Math.abs(c.getY() - pl.getY()) < 3) return c;
		}
		return null;
	}

	private static double horizontal(Vec3 a, BlockPos b) {
		double dx = a.x - (b.getX() + 0.5), dz = a.z - (b.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	@Override
	protected void cleanup() {
		Mc.mc().options.keyUse.setDown(false);
		super.cleanup();
	}
}
