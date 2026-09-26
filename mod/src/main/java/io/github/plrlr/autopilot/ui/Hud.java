package io.github.plrlr.autopilot.ui;

import io.github.plrlr.autopilot.Autopilot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** One line in the top-left corner while the autopilot is on: brain, goal, current action. */
public final class Hud {
	private Hud() {}

	public static void draw(Autopilot ap, GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!ap.enabled() || mc.player == null) return;
		String goal = ap.goal() == null ? "..." : ap.goal().key();
		String text = "Autopilot [" + ap.tactician.selected() + "]  goal: " + goal + "  |  " + ap.status();
		int w = mc.font.width(text);
		g.fill(2, 2, 8 + w, 15, 0x90000000);
		g.text(mc.font, text, 5, 5, 0xFFFFD166);
	}
}
