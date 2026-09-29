package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The geometry behind casting a portal (CastPortal): where to aim a bucket so its fluid lands in a
 * given spot, whether a line of sight is clear, and whether a site fits the frame and its wall.
 * Kept apart from CastPortal's step-by-step logic so each stays readable.
 */
final class CastGeometry {
	private CastGeometry() {}

	/** A spot the fluid should land in, and the point to look at so a bucket puts it there. */
	record Aim(BlockPos cell, Vec3 point) {}

	/** A point inside the water source that an empty bucket used from `eye` would pick up, or null. */
	static Vec3 scoopAim(Vec3 eye, BlockPos src) {
		Vec3 c = Vec3.atCenterOf(src);
		for (Vec3 p : new Vec3[]{c.add(0, 0.35, 0), c, c.add(0.3, 0.3, 0), c.add(-0.3, 0.3, 0), c.add(0, 0.3, 0.3), c.add(0, 0.3, -0.3)}) {
			if (eye.distanceTo(p) > Mc.reach() - 0.2) continue;
			Vec3 end = p.add(p.subtract(eye).normalize().scale(0.3));
			BlockHitResult hit = Mc.player().level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, Mc.player()));
			if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(src)) return p;
		}
		return null;
	}

	/** True if the straight line from eye to the middle of `to` stays out of the cell `avoid`. */
	static boolean clearOf(Vec3 eye, BlockPos to, BlockPos avoid) {
		Vec3 end = Vec3.atCenterOf(to);
		AABB box = new AABB(avoid);
		for (int i = 0; i <= 40; i++) if (box.contains(eye.lerp(end, i / 40.0))) return false;
		return true;
	}

	/**
	 * A point to look at so that using a bucket puts its fluid into `cell`: a spot on the face
	 * of a solid neighbor, reachable and with nothing solid in the way (fluids don't block).
	 */
	static Aim aimAt(Vec3 eye, BlockPos cell) {
		LocalPlayer pl = Mc.player();
		for (Direction d : Direction.values()) {
			BlockPos n = cell.relative(d);
			if (!Mc.solid(n) || Mc.isInteractive(n)) continue;
			Direction face = d.getOpposite();
			Vec3 c = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
			// Try the face's center and points toward its edges: a slightly higher point often
			// clears the frame block below.
			for (double[] o : new double[][]{{0, 0}, {0.3, 0}, {-0.3, 0}, {0, 0.3}, {0, -0.3}, {0.3, 0.3}, {-0.3, 0.3}}) {
				Vec3 p = c.add(offset(face, o[0], o[1]));
				if (eye.distanceTo(p) > Mc.reach() - 0.3) continue;
				Vec3 end = p.add(p.subtract(eye).normalize().scale(0.3));
				BlockHitResult hit = pl.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, pl));
				if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(n) && hit.getDirection() == face) {
					return new Aim(cell, p);
				}
			}
		}
		return null;
	}

	/** An offset within the plane of a face: u along the face's horizontal axis, v up (or along z for top/bottom). */
	static Vec3 offset(Direction face, double u, double v) {
		return switch (face.getAxis()) {
			case X -> new Vec3(0, v, u);
			case Z -> new Vec3(u, v, 0);
			case Y -> new Vec3(u, 0, v);
		};
	}

	/**
	 * Flat solid ground under the frame, the frame's space and the row above it empty and dry,
	 * room to stand in front, and the wall's spots either empty or already solid. Away from the
	 * lava itself, so flowing lava can't reach the frame.
	 */
	static boolean fits(BlockPos o, Direction a, int wallH) {
		return fitRefusal(o, a, wallH) == null;
	}

	static boolean fits(BlockPos o, Direction a, int wallH, FairProbe seen) {
		return fitRefusal(o, a, wallH, seen) == null;
	}

	/** First failed site check, for the NO_ROOM report in a lava-start drill. */
	static String fitRefusal(BlockPos o, Direction a, int wallH) {
		return fitRefusal(o, a, wallH, new FairProbe());
	}

	private static String fitRefusal(BlockPos o, Direction a, int wallH, FairProbe seen) {
		Direction front = a.getClockWise();
		for (int x = 0; x < 4; x++) {
			BlockPos col = o.relative(a, x);
			if (!seen.visible(col.below()) || !seen.solid(col.below())) return "floor";
			for (int y = 0; y < wallH; y++) if (!seen.free(col.above(y)) || seen.lavaNear(col.above(y))) return "frame";
			BlockPos back = col.relative(front.getOpposite());
			if (!seen.solid(back.below()) && !seen.solid(back)) return "back support";
			for (int y = 0; y < wallH; y++) {
				BlockPos w = back.above(y);
				if (!seen.solid(w) && !seen.free(w)) return "back wall";
			}
			for (int f = 1; f <= 2; f++) {
				BlockPos p = col.relative(front, f);
				if (!seen.free(p) || !seen.free(p.above()) || !seen.visible(p.below()) || !seen.solid(p.below())) return "standing rows";
			}
		}
		return null;
	}

	static boolean nearLava(BlockPos p) {
		return new FairProbe().lavaNear(p);
	}
}
