package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * The player moves the 40 skills (docs/skills-40.md) share: break a block, place one, strike,
 * raise the shield, eat, light a spot. Each is what a player does with the normal controls, one
 * small step per tick, so a skill's tick() stays short and the render thread never waits.
 */
final class Act {
	private Act() {}

	/** Breaks one block over several ticks with the best tool held. Keep one per skill. */
	static final class Breaker {
		private BlockPos at;
		private int ticks;

		/** One tick of breaking p. True once it's gone (air or replaceable). */
		boolean tick(BlockPos p) {
			if (Mc.free(p)) {
				at = null;
				return true;
			}
			Mc.lookAt(Vec3.atCenterOf(p));
			Direction face = faceToward(p);
			if (!p.equals(at)) {
				NightSkills.Shelter.holdBestTool(Mc.state(p));
				at = p;
				ticks = 0;
				Mc.mc().gameMode.startDestroyBlock(p, face);
			} else {
				Mc.mc().gameMode.continueDestroyBlock(p, face);
			}
			ticks++;
			Mc.swing();
			return false;
		}

		/** Ticks spent on the current block (callers give up on obsidian-slow blocks). */
		int ticks() {
			return ticks;
		}

		void stop() {
			if (at != null && Mc.mc().gameMode != null) Mc.mc().gameMode.stopDestroyBlock();
			at = null;
		}
	}

	/** The face of p that points at our eyes. */
	static Direction faceToward(BlockPos p) {
		Vec3 d = Mc.player().getEyePosition().subtract(Vec3.atCenterOf(p));
		return Direction.getApproximateNearest(d.x, d.y, d.z);
	}

	/** Holds a throwaway block. False when there's none. */
	static boolean holdBlock() {
		return Mc.holdItem(Items2.matcher("throwaway")) || Mc.holdItem(Items2.matcher("planks"));
	}

	/** Places a throwaway block at p if it's free and not in our body. True if the click was accepted. */
	static boolean place(BlockPos p) {
		if (!Mc.free(p) || !Mc.clearOfPlayer(p) || !holdBlock()) return false;
		return Mc.placeAt(p);
	}

	/** Our attack is charged enough for full damage. */
	static boolean charged() {
		return Mc.player().getAttackStrengthScale(0.5f) >= 0.9f;
	}

	/** Hits e if it's in reach, visible and our swing is charged. True if we swung. */
	static boolean strike(Entity e) {
		LocalPlayer pl = Mc.player();
		if (e == null || !e.isAlive() || pl.distanceTo(e) > 3.2 || !charged() || !Mc.canSee(e)) return false;
		CombatSkills.holdWeapon();
		if (pl.isUsingItem()) Mc.mc().gameMode.releaseUsingItem(pl);
		Mc.lookAt(e.getBoundingBox().getCenter());
		Mc.mc().gameMode.attack(pl, e);
		Mc.swing();
		return true;
	}

	static boolean hasShield() {
		return Items2.id(Mc.player().getOffhandItem()).equals("shield");
	}

	/** Holds the use key for the off-hand shield (the main hand must not hold something usable). */
	static void shield(boolean up) {
		if (up && !hasShield()) up = false;
		if (up && (Items2.isAnyFood(Mc.player().getMainHandItem()) || Items2.id(Mc.player().getMainHandItem()).contains("bucket")))
			CombatSkills.holdWeapon();
		Mc.mc().options.keyUse.setDown(up);
	}

	/** One tick of eating (hold food, hold use). False when there's no food or no need. */
	static boolean eatTick() {
		LocalPlayer pl = Mc.player();
		if (!pl.getFoodData().needsFood() || Mc.count(Items2::isAnyFood) == 0) {
			Mc.mc().options.keyUse.setDown(false);
			return false;
		}
		if (!Items2.isAnyFood(pl.getMainHandItem())) Mc.holdItem(Items2::isAnyFood);
		Mc.mc().options.keyUse.setDown(true);
		return true;
	}

	/** Block light at p (torches; 0 is where monsters spawn). */
	static int blockLight(BlockPos p) {
		return Mc.player().level().getBrightness(LightLayer.BLOCK, p);
	}

	/** Places a torch on the floor at p (air over solid ground). True if accepted. */
	static boolean torch(BlockPos p) {
		if (!Mc.state(p).isAir() || !Mc.solid(p.below())) return false;
		if (!Mc.holdItem(s -> Items2.id(s).equals("torch"))) return false;
		return Mc.useOn(p.below(), Direction.UP);
	}

	/** Solid, not lava, not a drop: safe to step onto (the cell at feet height and the one under it). */
	static boolean safeStep(BlockPos feet) {
		var under = Mc.state(feet.below());
		return Mc.free(feet) && Mc.free(feet.above()) && Mc.solid(feet.below())
				&& under.getFluidState().isEmpty() && !Mc.id(under.getBlock()).contains("magma")
				&& Mc.state(feet).getFluidState().isEmpty();
	}

	/** Lava in any of the 6 cells around p. */
	static boolean lavaNear(BlockPos p) {
		for (Direction d : Direction.values())
			if (Mc.state(p.relative(d)).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return true;
		return Mc.state(p).getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
	}

	/** Horizontal distance to a block position. */
	static double flatDist(BlockPos p) {
		Vec3 me = Mc.player().position();
		double dx = p.getX() + 0.5 - me.x, dz = p.getZ() + 0.5 - me.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
