package io.github.plrlr.autopilot.ui;

import io.github.plrlr.autopilot.Autopilot;
import io.github.plrlr.autopilot.brains.Tactician;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The K panel: turn the autopilot on/off, pick the brain, see what it's thinking.
 * It doesn't pause the game, so you can watch the AI play behind it.
 */
public final class PanelScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF, GREY = 0xFFB0B0B0, GOLD = 0xFFFFD166, GREEN = 0xFF7BE07B, RED = 0xFFFF7B7B;

	private final Autopilot ap;
	private final KeyMapping panelKey;

	public PanelScreen(Autopilot ap, KeyMapping panelKey) {
		super(Component.literal("MC Autopilot"));
		this.ap = ap;
		this.panelKey = panelKey;
	}

	@Override
	protected void init() {
		int w = 150, h = 20, gap = 4;
		int x = width / 2 - w - gap / 2, y = 28;
		addRenderableWidget(Button.builder(Component.literal("Autopilot: " + (ap.enabled() ? "ON" : "OFF")), b -> {
			ap.toggle();
			rebuildWidgets();
		}).bounds(x, y, w, h).build());
		addRenderableWidget(Button.builder(Component.literal("Action brain: " + ap.tactician.selected()), b -> {
			List<String> names = Tactician.NAMES;
			int i = names.indexOf(ap.tactician.selected());
			ap.tactician.select(names.get((i + 1) % names.size()));
			rebuildWidgets();
		}).bounds(x + w + gap, y, w, h).build());
		y += h + gap;
		addRenderableWidget(Button.builder(Component.literal("Goals: " + (ap.strategist.opusEnabled() ? "Opus" : "rules")), b -> {
			ap.strategist.setOpusEnabled(!ap.strategist.opusEnabled());
			rebuildWidgets();
		}).bounds(x, y, w, h).build());
		Button ask = Button.builder(Component.literal("Ask Opus for a new goal"), b -> ap.askOpusNow()).bounds(x + w + gap, y, w, h).build();
		ask.active = ap.enabled() && ap.strategist.opusEnabled();
		addRenderableWidget(ask);
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, h).build());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (panelKey.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.centeredText(font, "MC Autopilot", width / 2, 12, GOLD);
		int x = width / 2 - 152, y = 82, line = 11, maxW = 304;
		g.text(font, "Status: " + (ap.enabled() ? ap.status() : "off"), x, y, ap.enabled() ? GREEN : RED);
		y += line;
		String goal = ap.goal() == null ? "none yet" : ap.goal().key() + " - " + ap.goal().description;
		g.text(font, clip("Goal: " + goal, maxW), x, y, WHITE);
		y += line;
		if (!ap.goalReason().isEmpty()) {
			g.text(font, clip("Why (" + ap.goalBrain() + "): " + ap.goalReason(), maxW), x, y, GREY);
			y += line;
		}
		for (String s : ap.goalSteps()) {
			g.text(font, clip("  - " + s, maxW), x, y, GREY);
			y += line;
		}
		g.text(font, "Milestone " + ap.progress.furthest() + "/13   Deaths " + ap.progress.deaths()
				+ "   Opus calls this hour " + ap.opusCallsThisHour() + "/" + ap.opusCapPerHour(), x, y, GOLD);
		y += line + 4;
		g.text(font, "Recent decisions:", x, y, WHITE);
		y += line;
		List<String> ds = ap.recentDecisions();
		for (int i = Math.max(0, ds.size() - 6); i < ds.size(); i++) {
			g.text(font, clip("  " + ds.get(i), maxW), x, y, GREY);
			y += line;
		}
		y += 4;
		g.text(font, "Recent results:", x, y, WHITE);
		y += line;
		List<String> rs = ap.recentResults();
		for (int i = Math.max(0, rs.size() - 4); i < rs.size(); i++) {
			if (y > height - 40) break;
			g.text(font, clip("  " + rs.get(i), maxW), x, y, GREY);
			y += line;
		}
		g.centeredText(font, "Any movement key takes control back.  Chat: !stop !status !brain <name> !goal <name>", width / 2, height - 42, GREY);
	}

	private String clip(String s, int maxWidth) {
		if (font.width(s) <= maxWidth) return s;
		while (s.length() > 3 && font.width(s + "...") > maxWidth) s = s.substring(0, s.length() - 1);
		return s + "...";
	}
}
