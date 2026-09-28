package io.github.plrlr.autopilot.test;

import io.github.plrlr.autopilot.Autopilot;
import io.github.plrlr.autopilot.AutopilotMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * The combat drill (-PtestScenario=combat): many short fights instead of one long game, so the loop
 * can measure fighting on its own. 152 of 162 runs in gens 28-37 died, nearly all to mobs, and a
 * full run holds one or two fights buried in everything else; a drill plays one every ~30 s.
 *
 * Each round rebuilds a lit, roofed stone arena (no sunlight to burn mobs, no dark spots for extra
 * spawns), resets the player's gear, health and hunger, and summons a few monsters 6-10 blocks out.
 * The round ends when they're dead, the player dies, or after ROUND_S. The seed fixes the order of
 * setups, so two genomes on the same seed fight the same fights (the loop's pairs). One line per
 * round: "FIGHT <n> gear=<g> mobs=<a+b> kills=<k>/<m> damage=<hp> died=<0|1> secs=<s>".
 *
 * Test world only: the harness uses commands to stage the fights; the bot itself never does and
 * sees only what a player sees.
 */
final class CombatDrill {
	private CombatDrill() {}

	private static final int ROUND_S = 60;
	private static final int HALF = 12; // arena: 25 x 25 floor, 5 blocks of air, stone roof

	private record Gear(String name, List<String> give, List<String> slots) {}

	// From what the route really carries at each stage: wood at the start, stone before iron,
	// iron sword and shield after the first iron, the chestplate and helmet before the portal.
	private static final List<Gear> GEAR = List.of(
			new Gear("wood", List.of("wooden_sword"), List.of()),
			new Gear("stone", List.of("stone_sword"), List.of()),
			new Gear("iron", List.of("iron_sword"), List.of("weapon.offhand shield")),
			new Gear("armored", List.of("iron_sword"), List.of("weapon.offhand shield", "armor.chest iron_chestplate", "armor.head iron_helmet")));
	private static final List<String> ALWAYS = List.of("stone_pickaxe", "cobblestone 32", "cooked_beef 8");

	// The killers in the death data: zombies, skeletons (arrows), creepers, and mixes of them.
	private static final List<List<String>> MOBS = List.of(
			List.of("zombie"), List.of("zombie", "zombie"), List.of("skeleton"), List.of("zombie", "skeleton"),
			List.of("creeper"), List.of("spider"), List.of("zombie", "creeper"), List.of("skeleton", "skeleton"),
			List.of("zombie", "zombie", "zombie"));

	static void play(long gameSeconds, String seed) {
		Random rng = new Random(seed.hashCode());
		BlockPos c = FreeRun.onClient(() -> Minecraft.getInstance().player.blockPosition());
		System.out.println("[autopilot-test] COMBAT DRILL at " + c.toShortString() + " for " + gameSeconds + " game s");
		int round = 0;
		while (gs() < gameSeconds) {
			round++;
			Gear gear = GEAR.get(rng.nextInt(GEAR.size()));
			List<String> mobs = MOBS.get(rng.nextInt(MOBS.size()));
			double[] angles = new double[mobs.size()], dists = new double[mobs.size()];
			for (int i = 0; i < mobs.size(); i++) {
				angles[i] = rng.nextDouble() * 2 * Math.PI;
				dists[i] = 6 + rng.nextDouble() * 4;
			}
			if (!fight(round, c, gear, mobs, angles, dists, gameSeconds)) return;
		}
	}

	/** One round. Returns false when the run should stop (out of time or the game hung). */
	private static boolean fight(int round, BlockPos c, Gear gear, List<String> mobs, double[] angles, double[] dists, long endGs) {
		int x = c.getX(), y = c.getY(), z = c.getZ();
		cmd("execute positioned " + x + " " + y + " " + z + " run kill @e[type=!minecraft:player,distance=..40]");
		cmd("fill " + (x - HALF) + " " + (y - 1) + " " + (z - HALF) + " " + (x + HALF) + " " + (y + 5) + " " + (z + HALF) + " minecraft:stone hollow");
		for (int dx = -8; dx <= 8; dx += 8)
			for (int dz = -8; dz <= 8; dz += 8)
				if (dx != 0 || dz != 0) cmd("setblock " + (x + dx) + " " + y + " " + (z + dz) + " minecraft:torch");
		cmd("clear @a");
		for (String g : ALWAYS) cmd("give @a " + g);
		for (String g : gear.give()) cmd("give @a " + g);
		for (String s : gear.slots()) {
			String[] p = s.split(" ");
			cmd("item replace entity @a " + p[0] + " with " + p[1]);
		}
		cmd("effect give @a minecraft:instant_health 1 10");
		cmd("effect give @a minecraft:saturation 1 10");
		cmd("time set 18000");
		cmd("tp @a " + x + " " + y + " " + z);
		pause(1000);
		FreeRun.onClient(() -> {
			Autopilot ap = AutopilotMod.instance();
			if (!ap.enabled()) ap.enable();
		});
		for (int i = 0; i < mobs.size(); i++) {
			int mx = x + (int) Math.round(Math.cos(angles[i]) * dists[i]), mz = z + (int) Math.round(Math.sin(angles[i]) * dists[i]);
			cmd("summon minecraft:" + mobs.get(i) + " " + mx + " " + y + " " + mz);
		}
		pause(500);
		long start = gs();
		int deaths0 = FreeRun.onClient(() -> AutopilotMod.instance().progress.deaths());
		float last = FreeRun.onClient(() -> Minecraft.getInstance().player.getHealth());
		double damage = 0;
		int alive = mobs.size();
		boolean died = false;
		long lastGs = start, stalledSince = System.nanoTime();
		while (true) {
			pause(250);
			long now = gs();
			if (now != lastGs) {
				lastGs = now;
				stalledSince = System.nanoTime();
			} else if (System.nanoTime() - stalledSince > 180e9) {
				System.out.println("[autopilot-test] NOTE: the game stopped advancing at " + now + " s");
				return false;
			}
			float hp = FreeRun.onClient(() -> Minecraft.getInstance().player.getHealth());
			if (hp < last) damage += last - hp;
			last = hp;
			died = FreeRun.onClient(() -> AutopilotMod.instance().progress.deaths()) > deaths0;
			alive = FreeRun.onClient(() -> monstersNear(c));
			if (died || (alive == 0 && now - start >= 2) || now - start >= ROUND_S || now >= endGs) break;
		}
		long secs = gs() - start;
		System.out.println("[autopilot-test] FIGHT " + round + " gear=" + gear.name() + " mobs=" + String.join("+", mobs)
				+ " kills=" + Math.max(0, mobs.size() - alive) + "/" + mobs.size()
				+ " damage=" + Math.round(damage * 10) / 10.0 + " died=" + (died ? 1 : 0) + " secs=" + secs);
		// A death leaves the respawn to the autopilot (the death screen); give it a moment.
		if (died) pause(3000);
		return gs() < endGs;
	}

	/** Monsters still alive in the arena, as the client sees them (the harness, not the bot). */
	private static int monstersNear(BlockPos c) {
		var level = Minecraft.getInstance().level;
		if (level == null) return 0;
		int n = 0;
		for (Entity e : level.entitiesForRendering())
			if (e instanceof Enemy && e.isAlive() && e.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) < 20 * 20) n++;
		return n;
	}

	private static long elapsed, lastSeen;

	/**
	 * Game seconds since the drill began. The autopilot's own clock restarts whenever it's turned
	 * back on (enable() resets it), which kept the first drill run going until the job's time
	 * limit; so the drill adds up the steps itself and treats a drop as a restart from zero.
	 */
	private static long gs() {
		long now = FreeRun.onClient(() -> AutopilotMod.instance().gameSeconds());
		elapsed += now >= lastSeen ? now - lastSeen : now;
		lastSeen = now;
		return elapsed;
	}

	/** A test-world command on the server thread (the framework's runCommand needs the lockstep). */
	private static void cmd(String command) {
		var server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) return;
		try {
			server.submit(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command))
					.get(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			System.out.println("[autopilot-test] NOTE: command failed: " + command + ": " + e);
		}
	}

	private static void pause(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
