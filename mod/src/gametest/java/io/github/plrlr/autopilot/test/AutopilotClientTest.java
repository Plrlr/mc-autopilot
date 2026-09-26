package io.github.plrlr.autopilot.test;

import io.github.plrlr.autopilot.Autopilot;
import io.github.plrlr.autopilot.AutopilotMod;
import io.github.plrlr.autopilot.plan.Goal;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;

import java.util.List;

/**
 * Plays a fresh survival world with the rules brain (free) and prints what it does, so a
 * human (or Claude Code) can read the log and screenshots in mod/build/run.
 *
 * -PtestScenario picks the start:
 *   natural    a plain new world from spawn (default)
 *   portal     late-game gear and a lava pool nearby; goal: make obsidian, build and enter a portal
 *   stronghold 12 eyes of ender; goal: find the stronghold
 *   end        placed in the End with gear; goal: kill the dragon
 * The staged scenarios use commands in the throwaway test world only, to skip hours of play and
 * test one late-game step. The mod itself never uses commands.
 * -PtestBrain=auto uses the free AI keys from the real config file (costs free-tier calls).
 */
public class AutopilotClientTest implements FabricClientGameTest {
	private static final int MINUTE = 20 * 60;

	private static final List<String> GEAR = List.of(
			"diamond_pickaxe", "diamond_sword", "shield", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots",
			"cooked_beef 32", "cobblestone 64", "dirt 64", "oak_planks 32", "crafting_table", "furnace");

	@Override
	public void runTest(ClientGameTestContext ctx) {
		int minutes = Integer.getInteger("autopilot.test.minutes", 5);
		String scenario = System.getProperty("autopilot.test.scenario", "natural");
		String brain = System.getProperty("autopilot.test.brain", "mock");
		int tickRate = Integer.getInteger("autopilot.test.tickRate", 20);
		// Small view and simulation distances: far fewer chunks to generate and tick, so the
		// test world keeps up on a laptop (and a faster tick rate becomes possible).
		ctx.runOnClient(mc -> {
			mc.options.renderDistance().set(6);
			mc.options.simulationDistance().set(5);
		});
		// Consistent test settings make a superflat world (no trees); we need a normal one.
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> {
			s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
			s.setDifficulty(Difficulty.EASY);
			s.setSeed(System.getProperty("autopilot.test.seed", "autopilot"));
		}).create()) {
			ctx.waitTicks(100);
			Goal goal = stage(sp, scenario);
			// Test world only: run the game clock faster to see more play per real minute.
			if (tickRate != 20) sp.getServer().runCommand("tick rate " + tickRate);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> {
				Autopilot ap = AutopilotMod.instance();
				ap.tactician.select(brain);
				ap.strategist.setOpusEnabled(Boolean.getBoolean("autopilot.test.opus"));
				ap.enable();
				if (!ap.enabled()) throw new AssertionError("autopilot didn't turn on");
				if (goal != null) ap.forceGoal(goal);
			});
			for (int i = 1; i <= minutes * 2; i++) {
				ctx.waitTicks(MINUTE / 2);
				report(ctx, scenario + " " + i * 30 + "s");
				if (i % 4 == 0) ctx.takeScreenshot("autopilot-" + scenario + "-" + i / 2 + "min");
			}
			ctx.runOnClient(mc -> {
				Autopilot ap = AutopilotMod.instance();
				if (!ap.enabled()) System.out.println("[autopilot-test] NOTE: autopilot turned itself off during the run");
				System.out.println("[autopilot-test] FINAL " + scenario + ": " + ap.statusLine() + ", dimension "
						+ mc.player.level().dimension().identifier().getPath()
						+ ", milestone times " + (ap.milestoneTimes().isEmpty() ? "none" : String.join(" ", ap.milestoneTimes())));
				ap.disable("test finished");
			});
		}
	}

	/** Sets up the scenario with test-world commands; returns the goal to force, or null. */
	private static Goal stage(TestSingleplayerContext sp, String scenario) {
		var server = sp.getServer();
		if (scenario.equals("natural")) return null;
		server.runCommand("time set 1000");
		for (String g : GEAR) server.runCommand("give @a " + g);
		switch (scenario) {
			case "portal" -> {
				server.runCommand("give @a bucket");
				server.runCommand("give @a flint_and_steel");
				// A 4x3 lava pool set into the ground 7 blocks east, with solid ground around it.
				server.runCommand("execute at @p run fill ~6 ~-2 ~-3 ~11 ~-1 ~3 stone");
				server.runCommand("execute at @p run fill ~7 ~-1 ~-1 ~10 ~-1 ~1 lava");
				server.runCommand("execute at @p run fill ~6 ~ ~-3 ~11 ~3 ~3 air");
				// Water to fill the bucket from, 6 blocks west.
				server.runCommand("execute at @p run fill ~-8 ~-2 ~-2 ~-5 ~-1 ~2 stone");
				server.runCommand("execute at @p run fill ~-7 ~-1 ~-1 ~-6 ~-1 ~1 water");
				server.runCommand("execute at @p run fill ~-8 ~ ~-2 ~-5 ~3 ~2 air");
				return Goal.NETHER_PORTAL;
			}
			case "stronghold" -> {
				server.runCommand("give @a ender_eye 12");
				return Goal.FIND_STRONGHOLD;
			}
			case "end" -> {
				server.runCommand("give @a bow");
				server.runCommand("give @a arrow 64");
				server.runCommand("execute in minecraft:the_end run tp @a 0 80 40");
				return Goal.KILL_DRAGON;
			}
			default -> throw new AssertionError("unknown scenario " + scenario);
		}
	}

	private static void report(ClientGameTestContext ctx, String when) {
		ctx.runOnClient(mc -> {
			Autopilot ap = AutopilotMod.instance();
			System.out.println("[autopilot-test] " + when + ": " + ap.statusLine());
			// Where we are and what we carry: enough to see from the log alone what went wrong.
			var pl = mc.player;
			java.util.Map<String, Integer> inv = new java.util.TreeMap<>();
			for (var st : pl.getInventory().getNonEquipmentItems()) {
				if (!st.isEmpty()) inv.merge(io.github.plrlr.autopilot.Items2.id(st), st.getCount(), Integer::sum);
			}
			System.out.println("[autopilot-test]    at " + pl.getBlockX() + " " + pl.getBlockY() + " " + pl.getBlockZ()
					+ " hp " + Math.round(pl.getHealth()) + " food " + pl.getFoodData().getFoodLevel() + " inv " + inv);
			for (String r : ap.recentResults()) System.out.println("[autopilot-test]    " + r);
		});
	}
}
