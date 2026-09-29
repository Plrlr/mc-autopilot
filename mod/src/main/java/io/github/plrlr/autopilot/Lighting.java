package io.github.plrlr.autopilot;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
final class Lighting {
	private long lastTorchTick = -1000;
	/** Skills that need the hand or stand still on purpose: no torch in the middle of them. */
	private static final java.util.Set<String> TORCH_BUSY = java.util.Set.of("eat", "craft", "smelt", "build_portal", "fill_bucket",
			"place", "clutch", "shelter", "sleep", "barter", "enderman_boat", "attack", "retreat", "make_obsidian", "obsidian_mold", "obsidian_pool", "dig_portal");

	/**
	 * Torches in the dark (gene cave.torches): 129 of 165 deaths in generations 6-9 were
	 * underground, most to mobs, arrows and creepers in dark caves while mining. Monsters spawn only
	 * in darkness (block light 0), so a torch on the floor wherever block light at our feet is at
	 * or below cave.torch_light keeps the tunnels we work in empty, as players do.
	 */
	void lighting(Autopilot a, LocalPlayer pl) {
		if (!Tune.on("cave.torches") || a.tick % 10 != 0 || a.tick - lastTorchTick < 30) return;
		if (!Mc.dimension().equals("overworld") || pl.level().canSeeSky(pl.blockPosition().above())) return;
		if (a.skill != null && TORCH_BUSY.contains(a.skill.name())) return;
		if (Mc.count("torch") == 0 || !pl.onGround()) return;
		BlockPos feet = pl.blockPosition();
		if (pl.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, feet) > Tune.i("cave.torch_light")) return;
		if (!Mc.state(feet).isAir() || !Mc.solid(feet.below())) return;
		if (!Mc.holdItem(s -> Items2.id(s).equals("torch"))) return;
		Mc.useOn(feet.below(), net.minecraft.core.Direction.UP);
		lastTorchTick = a.tick;
		a.log.event("torch", feet.toShortString());
	}

	/**
	 * "Lost in a cave": underground over a minute and nothing gained (no item picked up, no
	 * skill finished well). The planner then puts goto surface first. Cobblestone, stone, dirt
	 * and the rest of THROWAWAY don't count as a gain (W6): Baritone picks them up by the
	 * dozen while it digs or branch-mines through anything, which kept resetting the clock and
	 * meant "lost" never fired even while genuinely wandering with nothing useful found.
	 */
	void cave(Autopilot a) {
		if (a.tick % 20 != 0) return;
		int total = 0;
		for (var st : Mc.player().getInventory().getNonEquipmentItems()) {
			if (Items2.THROWAWAY.contains(Items2.id(st))) continue;
			total += st.getCount();
		}
		if (total > a.lastItemTotal) a.lastGainTick = a.tick;
		a.lastItemTotal = total;
		long lost = 20L * Tune.i("loop.lost_underground_s");
		a.planner.lostUnderground = a.memory.undergroundTicks(a.tick) > lost && a.tick - a.lastGainTick > lost;
	}

}
