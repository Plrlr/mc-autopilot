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
 * -PtestParams=<file> plays with the learning loop's genes; -PtestLearned=<file> loads a learned model.
 * -PtestLean=true draws less: fast graphics, no clouds, particles,
 * shadows or sound. The game still ticks at 20 per second; only the drawing is cheaper, so a
 * software renderer has CPU left for the game itself. What the bot perceives doesn't change.
 */
public class AutopilotClientTest implements FabricClientGameTest {

	private static final List<String> GEAR = List.of(
			"diamond_pickaxe", "diamond_sword", "shield", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots",
			"cooked_beef 32", "cobblestone 64", "dirt 64", "oak_planks 32", "crafting_table", "furnace");

	@Override
	public void runTest(ClientGameTestContext ctx) {
		int minutes = Integer.getInteger("autopilot.test.minutes", 5);
		String scenario = System.getProperty("autopilot.test.scenario", "natural");
		int tickRate = Integer.getInteger("autopilot.test.tickRate", 20);
		// Small view and simulation distances: far fewer chunks to generate and tick, so the
		// test world keeps up on a laptop (and a faster tick rate becomes possible).
		ctx.runOnClient(mc -> {
			mc.options.renderDistance().set(6);
			mc.options.simulationDistance().set(5);
			if (Boolean.getBoolean("autopilot.test.lean")) lean(mc.options);
		});
		// Consistent test settings make a superflat world (no trees); we need a normal one.
		TestSingleplayerContext created = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> {
			s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
			s.setDifficulty(Difficulty.EASY);
			s.setSeed(System.getProperty("autopilot.test.seed", "autopilot"));
		}).create();
		// -PtestStart=<checkpoint.zip>: continue from a world a run really reached (the loop's bank).
		String startZip = System.getProperty("autopilot.test.start", "").trim();
		try (TestSingleplayerContext sp = startZip.isEmpty() ? created : Bank.restore(ctx, created, java.nio.file.Path.of(startZip))) {
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
			long wallStart = System.nanoTime();
			ctx.runOnClient(mc -> {
				Autopilot ap = AutopilotMod.instance();
				ap.enable();
				if (!ap.enabled()) throw new AssertionError("autopilot didn't turn on");
				if (goal != null) ap.forceGoal(goal);
				if (!task.isEmpty()) {
					int sp1 = task.indexOf(' ');
					ap.runTask(new Option(sp1 < 0 ? task : task.substring(0, sp1), sp1 < 0 ? null : task.substring(sp1 + 1), "test task"));
				}
			});
				// Checkpoints: the stage we start at, and each further stage the first time it's reached.
			int startStage = ctx.computeOnClient(Bank::stage);
			// Self-test of the bank: -PtestSaveAt=<s> saves a "kit" checkpoint at that second, whatever the stage.
			int saveAt = Integer.getInteger("autopilot.test.saveAt", 0);
			int bankedStage = startStage;
			System.out.println("[autopilot-test] STAGE start " + Bank.STAGES.get(startStage));
			// Free run (default): client and server in parallel at normal speed; the test only looks
			// in once a second. -PtestLockstep=true keeps the framework's slow lockstep (and its
			// milestone and death screenshots).
			boolean free = !Boolean.getBoolean("autopilot.test.lockstep") && FreeRun.start();
			if (free) {
				try {
					playFree(minutes * 60L, scenario, task, saveAt, bankedStage, sp);
					// The whole report before rejoining the framework: if rejoining ever hangs, the
					// run's results are already in the log (the watchdog then stops the game).
					String t = task;
					FreeRun.onClient(() -> {
						if (!t.isEmpty() && AutopilotMod.instance().taskResult() == null)
							System.out.println("[autopilot-test] TASK " + t + " -> failed TIMEOUT: still running when the test ended");
					});
					double wallF = (System.nanoTime() - wallStart) / 1e9;
					FreeRun.onClient(() -> finalReport(net.minecraft.client.Minecraft.getInstance(), scenario, wallF));
				} finally {
					FreeRun.stop();
					ctx.waitTicks(5);
				}
				ctx.takeScreenshot("final");
			} else {
			// Screenshots only where they explain something: each milestone, each death, a failed
			// task, and the end.
			int shotMilestones = 0, shotDeaths = 0;
			for (int sec = 1; sec <= minutes * 60; sec++) {
				ctx.waitTicks(20);
				if (sec % 30 == 0) report(ctx, scenario + " " + sec + "s");
				if (saveAt > 0 && sec == saveAt) Bank.save(ctx, sp, 1, sec);
				if (task.isEmpty()) {
					int now = ctx.computeOnClient(Bank::stage);
					if (now > bankedStage) {
						bankedStage = now;
						Bank.save(ctx, sp, now, ctx.computeOnClient(mc -> AutopilotMod.instance().gameSeconds()));
					}
				}
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
			}
			if (!free) {
				if (!task.isEmpty() && ctx.computeOnClient(mc -> AutopilotMod.instance().taskResult() == null))
					System.out.println("[autopilot-test] TASK " + task + " -> failed TIMEOUT: still running when the test ended");
				ctx.takeScreenshot("final");
				double wall = (System.nanoTime() - wallStart) / 1e9;
				ctx.runOnClient(mc -> finalReport(mc, scenario, wall));
			}
		}
	}

	/** Cheapest drawing that still shows the world in screenshots (cloud runs). */
	private static void lean(net.minecraft.client.Options o) {
		o.applyGraphicsPreset(net.minecraft.client.GraphicsPreset.FAST);
		// No frame cap: in 26.3 a 10 fps cap also held the game to 10 ticks a second (0.50x game
		// speed on the cloud, against 0.98x uncapped; benchmark 36323172968).
		o.enableVsync().set(false);
		o.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
		o.particles().set(net.minecraft.server.level.ParticleStatus.MINIMAL);
		o.entityShadows().set(false);
		o.ambientOcclusion().set(false);
		o.improvedTransparency().set(false);
		o.biomeBlendRadius().set(0);
		o.mipmapLevels().set(0);
		o.chunkSectionFadeInTime().set(0.0);
		o.menuBackgroundBlurriness().set(0);
		o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
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
		if (scenario.equals("deep")) {
			// The deep-lava route: full kit with water already scooped, dropped into a small pocket
			// at y -50. The bot has to find lava down here by itself and cast the portal.
			for (String g : List.of("iron_pickaxe", "iron_sword", "shield", "bucket", "water_bucket", "flint_and_steel", "cobblestone 64", "cooked_beef 16"))
				server.runCommand("give @a " + g);
			server.runCommand("item replace entity @a armor.chest with iron_chestplate");
			server.runCommand("item replace entity @a armor.head with iron_helmet");
			server.runCommand("execute at @p run fill ~-2 -50 ~-2 ~2 -47 ~2 air");
			server.runCommand("execute at @p run tp @p ~ -50 ~");
			return Goal.NETHER_PORTAL;
		}
		if (scenario.equals("enderman")) {
			// Pearls from endermen: a boat, planks for another, a sword; two endermen 8-10 blocks out.
			for (String g : List.of("iron_sword", "oak_boat", "oak_planks 10", "cooked_beef 16", "crafting_table"))
				server.runCommand("give @a " + g);
			server.runCommand("item replace entity @a armor.chest with iron_chestplate");
			server.runCommand("time set 14000");
			server.runCommand("execute at @p run summon enderman ~10 ~ ~");
			server.runCommand("execute at @p run summon enderman ~-8 ~ ~6");
			return Goal.ENDER_PEARLS;
		}
		if (scenario.equals("barter")) {
			// Trading alone: in the Nether with gold and a gold helmet on; piglins are added in arrive().
			for (String g : List.of("iron_pickaxe", "iron_sword", "cooked_beef 16", "cobblestone 64", "gold_ingot 32"))
				server.runCommand("give @a " + g);
			server.runCommand("item replace entity @a armor.head with golden_helmet");
			server.runCommand("item replace entity @a armor.chest with iron_chestplate");
			server.runCommand("execute in minecraft:the_nether run tp @a 0 70 0");
			return Goal.ENDER_PEARLS;
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
				server.runCommand("give @a ender_eye 14");
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
		if (!scenario.equals("nether") && !scenario.equals("blaze") && !scenario.equals("barter")) return;
		// A pocket of air on a netherrack floor, so the arrival spot isn't inside rock or over lava.
		server.runCommand("execute in minecraft:the_nether run fill -3 70 -3 3 74 3 air");
		server.runCommand("execute in minecraft:the_nether run fill -3 69 -3 3 69 3 netherrack");
		if (scenario.equals("barter")) {
			// A walled room with three adult piglins that won't turn into zombies here.
			server.runCommand("execute in minecraft:the_nether run fill -8 69 -8 8 75 8 netherrack hollow");
			server.runCommand("execute in minecraft:the_nether run fill -7 70 -7 7 74 7 air");
			for (int i = 0; i < 3; i++)
				server.runCommand("execute in minecraft:the_nether run summon piglin " + (3 + i) + " 70 " + (2 - 2 * i)
						+ " {IsImmuneToZombification:1b}");
		}
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

	/**
	 * The play loop in a free run: once a wall second, a few short looks through the client's task
	 * queue (game time, progress report, stage for the bank, the task's result). Ends at the game
	 * time asked for, or when a task finishes.
	 */
	private static void playFree(long gameSeconds, String scenario, String task, int saveAt, int bankedStage,
								 TestSingleplayerContext sp) {
		long lastReport = 0;
		boolean savedTest = false;
		long lastGs = -1, stalledSince = System.nanoTime();
		// A restored world comes without the bot's memory of what it saw: a "lava" start looks like
		// a kit until the lava is seen again. So the start stage is settled 10 s in, then banking begins.
		boolean settled = false;
		while (true) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			long gs = FreeRun.onClient(() -> AutopilotMod.instance().gameSeconds());
			if (gs >= gameSeconds) return;
			// The game stopped ticking (a hang): give up after 3 minutes rather than wait forever.
			if (gs != lastGs) {
				lastGs = gs;
				stalledSince = System.nanoTime();
			} else if (System.nanoTime() - stalledSince > 180e9) {
				System.out.println("[autopilot-test] NOTE: the game stopped advancing at " + gs + " s");
				return;
			}
			if (gs / 30 > lastReport) {
				lastReport = gs / 30;
				String when = scenario + " " + (lastReport * 30) + "s";
				FreeRun.onClient(() -> reportNow(net.minecraft.client.Minecraft.getInstance(), when));
			}
			if (saveAt > 0 && gs >= saveAt && !savedTest) {
				savedTest = true;
				Bank.save(null, sp, 1, gs);
			}
			if (task.isEmpty() && gs >= 10) {
				int now = FreeRun.onClient(() -> Bank.stage(net.minecraft.client.Minecraft.getInstance()));
				if (!settled) {
					settled = true;
					if (now > bankedStage) {
						bankedStage = now;
						System.out.println("[autopilot-test] STAGE start " + Bank.STAGES.get(now));
					}
				} else if (now > bankedStage) {
					bankedStage = now;
					Bank.save(null, sp, now, gs);
				}
			} else {
				String done = FreeRun.onClient(() -> {
					var r = AutopilotMod.instance().taskResult();
					return r == null ? null : (r.ok() ? "ok" : "failed " + r.code()) + ": " + r.detail();
				});
				if (done != null) {
					System.out.println("[autopilot-test] TASK " + task + " -> " + done + " after " + gs + " s");
					return;
				}
			}
		}
	}

	/** Real speed (game seconds per wall second: 1.0 keeps up with the game), then the FINAL line. */
	private static void finalReport(net.minecraft.client.Minecraft mc, String scenario, double wall) {
		Autopilot ap = AutopilotMod.instance();
		System.out.printf(java.util.Locale.ROOT, "[autopilot-test] SPEED %.3f game s per wall s (%d game s in %.0f wall s), mod %.2f ms per tick%n",
				ap.gameSeconds() / wall, ap.gameSeconds(), wall, ap.msPerTick());
		if (!ap.enabled()) System.out.println("[autopilot-test] NOTE: autopilot turned itself off during the run");
		System.out.println("[autopilot-test] FINAL " + scenario + ": " + ap.statusLine() + ", dimension "
				+ mc.player.level().dimension().identifier().getPath()
				+ ", milestone times " + (ap.milestoneTimes().isEmpty() ? "none" : String.join(" ", ap.milestoneTimes()))
				+ ", checkpoints " + (io.github.plrlr.autopilot.log.Checkpoints.summary().isEmpty() ? "none"
				: String.join(" ", io.github.plrlr.autopilot.log.Checkpoints.summary())));
		for (String l : ap.lessons.worst(6)) System.out.println("[autopilot-test] LESSON " + l);
		ap.disable("test finished");
	}

	private static void report(ClientGameTestContext ctx, String when) {
		ctx.runOnClient(mc -> reportNow(mc, when));
	}

	private static void reportNow(net.minecraft.client.Minecraft mc, String when) {
		{
			Autopilot ap = AutopilotMod.instance();
			System.out.println("[autopilot-test] " + when + ": " + ap.statusLine());
			// Where the time goes: drawn frames (the game ticks at most once per frame), the built-in
			// server's time per tick (50 ms is the budget), and our mod's own time per tick.
			var server = mc.getSingleplayerServer();
			System.out.printf(java.util.Locale.ROOT, "[autopilot-test]    perf fps %d, server %.1f ms/tick, mod %.2f ms/tick%n",
					mc.getFps(), server == null ? -1 : server.getAverageTickTimeNanos() / 1e6, ap.msPerTick());
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
		}
	}
}
