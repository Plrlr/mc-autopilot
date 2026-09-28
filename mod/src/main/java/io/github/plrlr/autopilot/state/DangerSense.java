package io.github.plrlr.autopilot.state;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Reads only nearby client-visible terrain and the shared perception, then asks Danger. */
public final class DangerSense {
	private DangerSense() {}
	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	public static Danger.Verdict assess(Perception seen) {
		LocalPlayer pl = Mc.player();
		BlockPos feet = pl.blockPosition();
		List<Danger.Mob> mobs = new ArrayList<>();
		for (Perception.Seen s : seen.mobs) if (s.hostile() && s.dist() <= 20) {
			mobs.add(new Danger.Mob(s.type(), s.dist(), s.entity().getX() - pl.getX(),
					s.entity().getZ() - pl.getZ(), Mc.canSee(s.entity())));
		}
		List<Danger.Step> steps = new ArrayList<>(4);
		for (int[] d : DIRECTIONS) {
			BlockPos at = feet.offset(d[0], 0, d[1]);
			boolean safe = safeStep(at);
			boolean water = Mc.state(at).getFluidState().is(FluidTags.WATER);
			boolean cover = false;
			Perception.Seen skeleton = seen.nearest("skeleton");
			if (safe && skeleton != null && skeleton.hostile() && skeleton.dist() < 16) {
				Vec3 eye = Vec3.atCenterOf(at).add(0, 1.12, 0);
				Vec3 target = skeleton.entity().getBoundingBox().getCenter();
				cover = pl.level().clip(new ClipContext(eye, target, ClipContext.Block.VISUAL,
						ClipContext.Fluid.NONE, pl)).getType() != HitResult.Type.MISS;
			}
			steps.add(new Danger.Step(d[0], d[1], safe, water, cover));
		}
		boolean source = Mc.canSee(feet) && fire(feet)
				|| Mc.canSee(feet.below()) && fire(feet.below());
		boolean nearby = false;
		for (int[] d : DIRECTIONS) {
			BlockPos at = feet.offset(d[0], 0, d[1]);
			if (Mc.canSee(at) && lava(at)
					|| Mc.canSee(at) && !Mc.solid(at) && Mc.canSee(at.below()) && lava(at.below())) nearby = true;
		}
		boolean armed = Items2.id(pl.getMainHandItem()).endsWith("_sword") || Items2.id(pl.getMainHandItem()).endsWith("_axe");
		boolean canWall = !Mc.dimension().equals("the_nether") && !pl.isOnFire()
				&& Mc.count("throwaway") >= 6 && (pl.getFoodData().getFoodLevel() >= 18 || Mc.count("food") > 0);
		return Danger.assess(new Danger.Input(pl.getHealth(), pl.getArmorValue(), pl.getFoodData().getFoodLevel(),
				armed, Items2.id(pl.getOffhandItem()).equals("shield"), pl.isOnFire(), pl.isInLava(),
				source, nearby, canWall, mobs, steps), Tune.get("survival.melee_lock"),
				Tune.get("reflex.creeper_dist"), Tune.get("combat.flee_hp"), Tune.get("plan.hostile_range"));
	}

	/** Check each neighboring block and up to three visible blocks below it before pressing a key. */
	public static boolean safeStep(BlockPos at) {
		// An occluded floor can hide a drop or lava. Treat unknown terrain as unsafe.
		if (!Mc.canSee(at) || !Mc.canSee(at.above())) return false;
		if ((!Mc.free(at) && !Mc.state(at).getFluidState().is(FluidTags.WATER))
				|| (!Mc.free(at.above()) && !Mc.state(at.above()).getFluidState().is(FluidTags.WATER))) return false;
		for (int drop = 0; drop <= 1; drop++) {
			BlockPos p = at.below(drop);
			if (!Mc.canSee(p)) return false;
			if (lava(p) || fire(p)) return false;
			if (drop > 0 && Mc.solid(p)) return true;
		}
		return Mc.state(at).getFluidState().is(FluidTags.WATER);
	}

	private static boolean lava(BlockPos p) { return Mc.state(p).getFluidState().is(FluidTags.LAVA); }
	private static boolean fire(BlockPos p) {
		String id = Mc.id(Mc.state(p).getBlock());
		return id.equals("fire") || id.equals("soul_fire");
	}
}
