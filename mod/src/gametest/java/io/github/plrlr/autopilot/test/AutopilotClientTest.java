package io.github.plrlr.autopilot.test;

import io.github.plrlr.autopilot.Autopilot;
import io.github.plrlr.autopilot.AutopilotMod;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;

/**
 * Plays a fresh survival world with the rules brain for a few minutes (free), then, if
 * -Dautopilot.test.opus=true, with Opus for two minutes (uses a few calls of the Claude plan).
 * It doesn't assert game progress (that depends on the world); it checks that nothing crashes
 * and leaves logs and screenshots in mod/run for a human to read.
 */
public class AutopilotClientTest implements FabricClientGameTest {
	private static final int MINUTE = 20 * 60;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		int mockMinutes = Integer.getInteger("autopilot.test.minutes", 5);
		// Consistent test settings make a superflat world (no trees); we need a normal one.
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> {
			s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
			s.setDifficulty(Difficulty.EASY);
			s.setSeed(System.getProperty("autopilot.test.seed", "autopilot"));
		}).create()) {
			ctx.waitTicks(100);
			ctx.runOnClient(mc -> {
				Autopilot ap = AutopilotMod.instance();
				ap.tactician.select("mock");
				ap.strategist.setOpusEnabled(false);
				ap.enable();
				if (!ap.enabled()) throw new AssertionError("autopilot didn't turn on");
			});
			for (int i = 1; i <= mockMinutes * 2; i++) {
				ctx.waitTicks(MINUTE / 2);
				report(ctx, "mock " + i * 30 + "s");
				if (i % 2 == 0) ctx.takeScreenshot("autopilot-mock-" + i / 2 + "min");
			}
			if (Boolean.getBoolean("autopilot.test.opus")) {
				ctx.runOnClient(mc -> {
					Autopilot ap = AutopilotMod.instance();
					ap.tactician.select("opus");
					ap.strategist.setOpusEnabled(true);
					ap.askOpusNow();
				});
				for (int i = 1; i <= 4; i++) {
					ctx.waitTicks(MINUTE / 2);
					report(ctx, "opus " + i * 30 + "s");
				}
				ctx.takeScreenshot("autopilot-opus");
			}
			ctx.runOnClient(mc -> {
				Autopilot ap = AutopilotMod.instance();
				if (!ap.enabled()) System.out.println("[autopilot-test] NOTE: autopilot turned itself off during the run");
				ap.disable("test finished");
			});
		}
	}

	private static void report(ClientGameTestContext ctx, String when) {
		ctx.runOnClient(mc -> {
			Autopilot ap = AutopilotMod.instance();
			System.out.println("[autopilot-test] " + when + ": " + ap.statusLine());
			for (String r : ap.recentResults()) System.out.println("[autopilot-test]    " + r);
		});
	}
}
