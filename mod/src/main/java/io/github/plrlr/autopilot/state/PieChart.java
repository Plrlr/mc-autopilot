package io.github.plrlr.autopilot.state;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * What the F3 debug pie chart tells a speedrunner. With the pie chart on "blockentities", the
 * spawner slice shows up while spawners are being drawn: in the rendered chunks and in front of
 * the camera. Runners turn slowly and read the direction where it appears (a "pie-ray"); in the
 * Nether that points at a fortress. It's in the vanilla game and allowed in speedruns.
 *
 * We read the same thing: spawners in the chunks within render distance whose direction lies in
 * the camera's field of view. A direction, never a position, and only for the way we're facing.
 */
public final class PieChart {
	/** Half the horizontal field of view at the default fov and a wide window, in degrees. */
	private static final double HALF_FOV = 50;

	private PieChart() {}

	/** How many spawners the pie chart would show while facing this yaw (degrees, Minecraft's). */
	public static int spawnersInView(float yaw) {
		LocalPlayer pl = Mc.player();
		Level level = pl.level();
		int chunks = Mc.mc().options.getEffectiveRenderDistance();
		int pcx = pl.blockPosition().getX() >> 4, pcz = pl.blockPosition().getZ() >> 4;
		int n = 0;
		for (int dx = -chunks; dx <= chunks; dx++) {
			for (int dz = -chunks; dz <= chunks; dz++) {
				if (!level.hasChunk(pcx + dx, pcz + dz)) continue;
				LevelChunk c = level.getChunk(pcx + dx, pcz + dz);
				for (BlockEntity be : c.getBlockEntities().values()) {
					if (Mc.id(be.getBlockState().getBlock()).equals("spawner") && inView(pl, be.getBlockPos(), yaw)) n++;
				}
			}
		}
		return n;
	}

	private static boolean inView(LocalPlayer pl, BlockPos p, float yaw) {
		double dx = p.getX() + 0.5 - pl.getX(), dz = p.getZ() + 0.5 - pl.getZ();
		// Minecraft's yaw: 0 faces +z, 90 faces -x.
		double toward = Math.toDegrees(Math.atan2(-dx, dz));
		double diff = ((toward - yaw) % 360 + 540) % 360 - 180;
		return Math.abs(diff) <= HALF_FOV;
	}
}
