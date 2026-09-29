package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.skills.ChestSkills;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;

/**
 * Wave 2 of docs/skills-40.md (the portal): where each portal skill replaces the rules' step.
 * Every hook returns the old option when its gene is off.
 */
public final class PortalPlan {
	private PortalPlan() {}

	/** The cast step itself: make the site instead of searching for a flat one (gene skill.cast_portal). */
	public static Option cast(Option old) {
		if (old == null || !Tune.on("skill.cast_portal") || !old.skill().equals("build_portal") || old.arg() != null) return old;
		return new Option("cast_portal", null, "cast the portal on a site we dig out and floor ourselves");
	}

	/** Looking for lava to cast from: look for a pool big enough (gene skill.lava_scout). */
	public static Option lava(Option old) {
		if (old == null || !Tune.on("skill.lava_scout") || !old.label().equals("explore lava")) return old;
		return new Option("lava_scout", null, "find a lava pool with 10+ sources to cast from");
	}

	/** Before any portal work: a ruined portal in sight is a head start (gene skill.ruined_portal). */
	public static Option early(WorldMemory memory) {
		if (Tune.on("skill.ruined_portal") && memory.nearest("ruined_portal") != null && Mc.count("obsidian") < 10
				&& Mc.dimension().equals("overworld"))
			return new Option("ruined_portal", null, "a ruined portal in sight: loot its chest and take its obsidian");
		if (Tune.on("skill.portal_repair") && memory.nearest("nether_portal") == null && Mc.count("flint_and_steel") > 0
				&& memory.all("obsidian").size() >= 10 && Mc.dimension().equals("overworld"))
			return new Option("portal_repair", null, "a frame's worth of obsidian in sight: light it or finish it");
		return null;
	}

	/** The diamond route's obsidian: harden and mine safely (gene skill.obsidian_pool). */
	public static Option obsidian(WorldMemory memory) {
		if (!Tune.on("skill.obsidian_pool") || Items2.bestTier("pickaxe") < 3 || Goal.have("obsidian") >= 10) return null;
		if (memory.nearest("lava") == null && memory.nearest("obsidian") == null) return null;
		return new Option("obsidian_pool", null, "harden the lava pool and mine 10 obsidian, checking under each block");
	}

	/**
	 * A lava pool close enough to harden and mine: 6+ seen sources within 48 blocks, or obsidian
	 * we can mine within 24. A pool remembered from far away (a surface pool 300 blocks back)
	 * isn't one: walking back to it from diamond depth is the long way round.
	 */
	public static boolean poolNear(WorldMemory memory) {
		BlockPos me = Mc.player().blockPosition();
		int n = 0;
		for (WorldMemory.Seen s : memory.all("lava")) if (s.pos().distSqr(me) <= 48 * 48 && ++n >= 6) return true;
		for (WorldMemory.Seen s : memory.all("obsidian")) if (s.pos().distSqr(me) <= 24 * 24) return true;
		return false;
	}

	/** When "collect diamond:n:lava" (mining deep for lava) has found it. */
	public static boolean lavaFound(WorldMemory memory) {
		if (Tune.on("route.diamond_portal") && Tune.on("route.deep_portal")) return poolNear(memory);
		return memory.nearest("lava") != null;
	}

	/** Any "collect diamond" step (not the deep-for-lava one): safe stairs, then branch mining (gene skill.diamond_hunt; the deep route always). */
	public static Option diamonds(Option old) {
		boolean deep = Tune.on("route.diamond_portal") && Tune.on("route.deep_portal");
		if (old == null || !(Tune.on("skill.diamond_hunt") || deep) || !old.skill().equals("collect") || old.arg() == null
				|| !old.arg().startsWith("diamond") || old.arg().endsWith(":lava")) return old;
		String[] a = old.arg().split(":");
		return new Option("diamond_hunt", a.length > 1 ? a[1] : "3", "stairs to diamond depth, then branch-mine");
	}

	/** Spares into a chest at base while it's calm and daylight (gene skill.stash). */
	public static Option stash(WorldMemory memory) {
		if (!Tune.on("skill.stash") || Mc.isNight() || !ChestSkills.stashWorthIt()) return null;
		if (Mc.count("chest") == 0 && memory.nearestStation("chest") == null) {
			if (Mc.count(Items2.matcher("planks")) >= 8) return new Option("craft", "chest:1", "a chest for our spares");
			return null;
		}
		return new Option("stash", null, "put spares in a chest at base: a death won't cost them");
	}

	/** After a death with our chest near: take the spares back before rebuilding by hand (gene skill.restock). */
	public static Option restock(WorldMemory memory) {
		if (!Tune.on("skill.restock") || Items2.bestTier("pickaxe") >= 2) return null;
		if (Tune.on("restock.stash_only")) {
			if (!SurvivalPlan.diedHere() || ChestSkills.Stash.position() == null) return null;
			if (ChestSkills.Stash.position().distSqr(Mc.player().blockPosition()) > WorldMemory.STATION_RANGE * WorldMemory.STATION_RANGE) return null;
		} else if (memory.nearestStation("chest") == null) return null;
		return new Option("restock", null, "our chest is near and we lost our tools: take the spares back");
	}
}
