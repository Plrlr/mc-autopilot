package io.github.plrlr.autopilot;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.plrlr.autopilot.brains.Tactician;
import io.github.plrlr.autopilot.plan.Goal;
import io.github.plrlr.autopilot.ui.Hud;
import io.github.plrlr.autopilot.ui.PanelScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point: registers the K key, the tick loop, the HUD line and the "!" chat commands. */
public class AutopilotMod implements ClientModInitializer {
	public static final String MOD_ID = "autopilot";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Autopilot autopilot;
	private static KeyMapping panelKey;

	/** For the in-game tests. */
	public static Autopilot instance() {
		return autopilot;
	}

	@Override
	public void onInitializeClient() {
		Minecraft mc = Minecraft.getInstance();
		autopilot = new Autopilot(Config.load(mc.gameDirectory.toPath()), mc.gameDirectory.toPath());

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		panelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.autopilot.panel", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (panelKey.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) client.gui.setScreen(new PanelScreen(autopilot, panelKey));
			}
			try {
				autopilot.tick(client);
			} catch (Exception e) {
				// Fail safe: never crash the game, stand still instead.
				LOGGER.error("Autopilot tick failed; turning it off", e);
				autopilot.disable("internal error: " + e.getClass().getSimpleName());
			}
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "status"), (g, delta) -> Hud.draw(autopilot, g, delta));

		ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
			if (!message.startsWith("!")) return true;
			handleCommand(message.substring(1).trim());
			return false; // our commands never go to the world chat
		});
		LOGGER.info("MC Autopilot loaded. Settings: {}", autopilot.config.file);
	}

	private static void handleCommand(String cmd) {
		String[] p = cmd.split("\\s+", 2);
		String arg = p.length > 1 ? p[1].trim().toLowerCase() : "";
		switch (p[0].toLowerCase()) {
			case "stop" -> autopilot.disable("!stop");
			case "start" -> autopilot.enable();
			case "status" -> Mc.say(autopilot.statusLine());
			case "brain" -> {
				if (Tactician.NAMES.contains(arg)) {
					autopilot.tactician.select(arg);
					Mc.say("Action brain: " + arg);
				} else Mc.say("Brains: " + String.join(", ", Tactician.NAMES));
			}
			case "goal" -> {
				Goal g = Goal.byKey(arg);
				if (g != null) {
					autopilot.forceGoal(g);
					Mc.say("Goal set: " + g.description);
				} else {
					StringBuilder sb = new StringBuilder("Goals:");
					for (Goal x : Goal.values()) sb.append(' ').append(x.key());
					Mc.say(sb.toString());
				}
			}
			case "opus" -> {
				autopilot.strategist.setOpusEnabled(!arg.equals("off"));
				Mc.say("Goals by " + (autopilot.strategist.opusEnabled() ? "Opus" : "rules"));
			}
			default -> Mc.say("Commands: !start !stop !status !brain <opus|mock|groq|gemini> !goal <name> !opus on|off");
		}
	}
}
