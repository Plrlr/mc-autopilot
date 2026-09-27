package io.github.plrlr.autopilot.test;

import io.github.plrlr.autopilot.Autopilot;
import io.github.plrlr.autopilot.AutopilotMod;
import io.github.plrlr.autopilot.plan.Goal;
import io.github.plrlr.autopilot.plan.Option;
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
 *   cast       the speedrun kit (iron pickaxe, two buckets, flint and steel, blocks) and a lava pool;
 *              goal: cast the portal from lava and water without a diamond pickaxe, and enter it
 *   stronghold 12 eyes of ender; goal: find the stronghold
 *   end        placed in the End with gear; goal: kill the dragon
 * The staged scenarios use commands in the throwaway test world only, to skip hours of play and
 * test one late-game step. The mod itself never uses commands.
 * -PtestBrain=auto uses the free AI keys from the real config file (costs free-tier calls).
 */
public class AutopilotClientTest implements FabricClientGameTest {

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
			// Quick single-fix tests: -PtestTask="craft furnace:1" -PtestGive="cobblestone 8,crafting_table"
			// runs just that skill with those items, in daylight, and stops when it ends.
			String task = System.getProperty("autopilot.test.task", "").trim();
			String give = System.getProperty("autopilot.test.give", "").trim();
			for (String g : give.isEmpty() ? new String[0] : give.split(",")) sp.getServer().runCommand("give @a " + g.trim());
			if (!task.isEmpty()) sp.getServer().runCommand("time set 1000");
			// Test world only: run the game clock faster to see more play per real minute.
			if (tickRate != 20) sp.getServer().runCommand("tick rate " + tickRate);
			ctx.waitTicks(40);
			arrive(sp, scenario);
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> System.out.println("[autopilot-test] start at " + mc.player.blockPosition().toShortString()));
			ctx.runOnClient(mc -> {
				Autopilot ap = AutopilotMod.instance();
				ap.tactician.select(brain);
				ap.strategist.setOpusEnabled(Boolean.getBoolean("autopilot.test.opus"));
				ap.enable();
				if (!ap.enabled()) throw new AssertionError("autopilot didn't turn on");
				if (goal != null) ap.forceGoal(goal);
				if (!task.isEmpty()) {
					int sp1 = task.indexOf(' ');
					ap.runTask(new Option(sp1 < 0 ? task : task.substring(0, sp1), sp1 < 0 ? null : task.substring(sp1 + 1), "test task"));
				}
			});
			// Screenshots only where they explain something: each milestone, each death, a failed
			// task, and the end.
			int shotMilestones = 0, shotDeaths = 0;
			for (int sec = 1; sec <= minutes * 60; sec++) {
				ctx.waitTicks(20);
				if (sec % 30 == 0) report(ctx, scenario + " " + sec + "s");
				int[] st = ctx.computeOnClient(mc -> {
					Autopilot ap = AutopilotMod.instance();
					return new int[]{ap.milestoneTimes().size(), ap.progress.deaths(), ap.taskResult() == null ? 0 : ap.taskResult().ok() ? 1 : 2};
				});
				if (st[0] > shotMilestones) {
					shotMilestones = st[0];
					ctx.takeScreenshot("milestone-" + shotMilestones);
				}
				if (st[1] > shotDeaths) {
					shotDeaths = st[1];
					ctx.takeScreenshot("death-" + shotDeaths);
				}
				if (!task.isEmpty() && st[2] > 0) {
					int secs = sec;
					ctx.runOnClient(mc -> {
						var r = AutopilotMod.instance().taskResult();
						System.out.println("[autopilot-test] TASK " + task + " -> " + (r.ok() ? "ok" : "failed " + r.code()) + ": " + r.detail() + " after " + secs + " s");
					});
					if (st[2] == 2) ctx.takeScreenshot("task-failed");
					break;
				}
			}
			if (!task.isEmpty() && ctx.computeOnClient(mc -> AutopilotMod.instance().taskResult() == null))
				System.out.println("[autopilot-test] TASK " + task + " -> failed TIMEOUT: still running when the test ended");
			ctx.takeScreenshot("final");
			ctx.runOnClient(mc -> {
				Autopilot ap = AutopilotMod.instance();
				if (!ap.enabled()) System.out.println("[autopilot-test] NOTE: autopilot turned itself off during the run");
				System.out.println("[autopilot-test] FINAL " + scenario + ": " + ap.statusLine() + ", dimension "
						+ mc.player.level().dimension().identifier().getPath()
						+ ", milestone times " + (ap.milestoneTimes().isEmpty() ? "none" : String.join(" ", ap.milestoneTimes()))
						+ ", checkpoints " + (io.github.plrlr.autopilot.log.Checkpoints.summary().isEmpty() ? "none"
						: String.join(" ", io.github.plrlr.autopilot.log.Checkpoints.summary())));
				for (String l : ap.lessons.worst(6)) System.out.println("[autopilot-test] LESSON " + l);
				ap.disable("test finished");
			});
		}
	}

	/** Sets up the scenario with test-world commands; returns the goal to force, or null. */
	private static Goal stage(TestSingleplayerContext sp, String scenario) {
		var server = sp.getServer();
		if (scenario.equals("natural")) return null;
		server.runCommand("time set 1000");
		if (scenario.equals("cast")) {
			// The full kit the route carries by the cast (shield, chestplate, helmet worn): without
			// them the portal step sent the bot mining 26 iron instead of casting (batch 12).
			for (String g : List.of("iron_pickaxe", "iron_sword", "shield", "bucket 2", "flint_and_steel", "cobblestone 64", "cooked_beef 16"))
				server.runCommand("give @a " + g);
			server.runCommand("item replace entity @a armor.chest with iron_chestplate");
			server.runCommand("item replace entity @a armor.head with iron_helmet");
			lavaAndWater(server::runCommand);
			return Goal.NETHER_PORTAL;
		}
		if (scenario.equals("nether") || scenario.equals("blaze")) {
			// What the speedrun route really carries into the Nether since 9980cfe: iron tools, a
			// shield, and an iron chestplate + helmet (worn, not just carried - the planner puts
			// armor on at once) before it ever makes the portal.
			for (String g : List.of("iron_pickaxe", "iron_sword", "shield", "cooked_beef 16", "cobblestone 64", "flint_and_steel"))
				server.runCommand("give @a " + g);
			server.runCommand("item replace entity @a armor.chest with iron_chestplate");
			server.runCommand("item replace entity @a armor.head with iron_helmet");
			// Blocks can only be set once the Nether chunks are loaded ("That position is not
			// loaded"): go there first, build in arrive() after a wait.
			server.runCommand("execute in minecraft:the_nether run tp @a 0 70 0");
			return Goal.BLAZE_RODS;
		}
		for (String g : GEAR) server.runCommand("give @a " + g);
		switch (scenario) {
			case "portal" -> {
				server.runCommand("give @a bucket");
				server.runCommand("give @a flint_and_steel");
				lavaAndWater(server::runCommand);
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

	/** Building for scenarios that teleport first: runs after the player has arrived and chunks are loaded. */
	private static void arrive(TestSingleplayerContext sp, String scenario) {
		var server = sp.getServer();
		if (!scenario.equals("nether") && !scenario.equals("blaze")) return;
		// A pocket of air on a netherrack floor, so the arrival spot isn't inside rock or over lava.
		server.runCommand("execute in minecraft:the_nether run fill -3 70 -3 3 74 3 air");
		server.runCommand("execute in minecraft:the_nether run fill -3 69 -3 3 69 3 netherrack");
		if (scenario.equals("blaze")) {
			// A small walled nether-brick room with a blaze spawner 4 blocks away: the fight alone.
			server.runCommand("execute in minecraft:the_nether run fill -6 69 -6 6 76 6 nether_bricks hollow");
			server.runCommand("execute in minecraft:the_nether run setblock 4 70 0 spawner{SpawnData:{entity:{id:\"minecraft:blaze\"}}}");
		}
		server.runCommand("execute in minecraft:the_nether run tp @a 0 70 0");
	}

	/** A 4x3 lava pool set into the ground 7 blocks east, and water to fill a bucket from 6 blocks west. */
	private static void lavaAndWater(java.util.function.Consumer<String> run) {
		run.accept("execute at @p run fill ~6 ~-2 ~-3 ~11 ~-1 ~3 stone");
		run.accept("execute at @p run fill ~7 ~-1 ~-1 ~10 ~-1 ~1 lava");
		run.accept("execute at @p run fill ~6 ~ ~-3 ~11 ~3 ~3 air");
		run.accept("execute at @p run fill ~-8 ~-2 ~-2 ~-5 ~-1 ~2 stone");
		run.accept("execute at @p run fill ~-7 ~-1 ~-1 ~-6 ~-1 ~1 water");
		run.accept("execute at @p run fill ~-8 ~ ~-2 ~-5 ~3 ~2 air");
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
			var water = ap.memory.nearest("water");
			var lava = ap.memory.nearest("lava");
			System.out.println("[autopilot-test]    known water " + (water == null ? "none" : water.pos().toShortString())
					+ ", lava " + (lava == null ? "none" : lava.pos().toShortString()));
			for (String r : ap.recentResults()) System.out.println("[autopilot-test]    " + r);
		});
	}
}
