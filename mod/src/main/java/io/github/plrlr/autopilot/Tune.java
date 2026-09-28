package io.github.plrlr.autopilot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The bot's tunable numbers ("genes"): every hand-picked threshold the learning loop may change.
 * Defaults are exactly the values the code used before the loop, so no params file means the
 * old behavior. The loop (scripts/loop) writes a params.json; the game test passes its path in
 * the system property autopilot.params, and the mod also reads mc-autopilot/params.json.
 *
 * Only numbers and on/off switches live here, with hard limits, so a mutation can never ask for
 * something nonsensical (health 40, a negative distance). Skill mechanics stay code.
 */
public final class Tune {
	private Tune() {}

	/** One gene: default, limits, and whether it's a whole number or a switch (0/1). */
	public record Gene(String name, double def, double min, double max, Kind kind, String help) {}

	public enum Kind { INT, REAL, BOOL }

	private static final Map<String, Gene> GENES = new LinkedHashMap<>();
	private static final Map<String, Double> values = new TreeMap<>();
	private static volatile String source = "defaults";

	private static void gene(String name, double def, double min, double max, Kind kind, String help) {
		GENES.put(name, new Gene(name, def, min, max, kind, help));
	}

	static {
		// Reflexes (Autopilot.reflexes)
		gene("reflex.creeper_dist", 7, 4, 12, Kind.REAL, "back off from a creeper closer than this");
		gene("reflex.melee_dist", 3.5, 2, 5, Kind.REAL, "a hostile this close triggers the fight/flee reflex");
		gene("combat.flee_hp", 8, 3, 14, Kind.INT, "at or below this health, run when outnumbered (reflex and planner share it)");
		gene("combat.outnumbered", 2, 1, 4, Kind.INT, "hostiles within 6 blocks that count as outnumbered");
		gene("reflex.starving_food", 6, 2, 12, Kind.INT, "eat at once at or below this hunger when safe");
		gene("combat.crits", 0, 0, 1, Kind.BOOL, "jump before a swing so it lands as a critical hit (1.5x)");
		gene("combat.backstep", 0, 0, 1, Kind.BOOL, "step back out of reach while the sword recharges");
		gene("combat.shield_guard", 0, 0, 1, Kind.BOOL, "raise the shield toward a skeleton drawing its bow or a creeper about to blow");
		gene("dragon.max_center_dist", 30, 10, 60, Kind.REAL, "in the End fight, walk back if farther than this from the fountain");
		gene("dragon.wait_dist", 10, 5, 20, Kind.REAL, "where to wait while the dragon flies: this far from the fountain");
		gene("dragon.eat_hp", 12, 4, 18, Kind.INT, "in the End fight, eat at or below this health while it flies");
		gene("reflex.burning_hp", 12, 4, 20, Kind.INT, "eat while burning at or below this health");
		gene("reflex.clutch_fall", 4, 2, 12, Kind.REAL, "falling farther than this with a water bucket: pour it before landing");
		// Planner: danger
		gene("plan.hostile_range", 10, 5, 18, Kind.REAL, "hostiles within this range get an urgent option");
		gene("plan.hide_hp", 12, 6, 18, Kind.INT, "underground, wall in at or below this health with 2+ monsters near");
		gene("plan.upkeep_calm_radius", 12, 6, 24, Kind.INT, "no upkeep while a hostile is within this radius");
		// Planner: food
		gene("food.eat_at", 14, 8, 18, Kind.INT, "eat at or below this hunger");
		gene("food.eat_hurt_at", 17, 14, 19, Kind.INT, "eat at or below this hunger when health isn't full");
		gene("food.stock", 8, 0, 20, Kind.INT, "cooked food to keep in stock");
		gene("food.search_hunger", 8, 3, 16, Kind.INT, "explore far for animals only at or below this hunger");
		gene("food.hunt_dist", 20, 6, 40, Kind.INT, "hunt animals in view within this distance for the stock");
		// Planner: night, death items
		gene("night.shelter_armor", 10, 0, 20, Kind.INT, "at night on the surface, shelter if armor is below this");
		gene("night.shelter_radius", 16, 6, 32, Kind.INT, "shelter only with a hostile within this radius");
		gene("night.mine_instead", 0, 0, 1, Kind.BOOL, "at night, skip the shelter when the next step is mining underground anyway");
		gene("night.shelter_max_s", 720, 60, 720, Kind.INT, "longest wait in a night shelter before going back to work");
		gene("death.recover_night_armor", 10, 0, 20, Kind.INT, "go back for dropped items at night only with this much armor");
		// Planner: gathering amounts
		gene("gather.first_logs", 2, 1, 6, Kind.INT, "extra logs on the very first wood trip");
		gene("gather.log_stock", 8, 3, 20, Kind.INT, "keep this many logs once there is a pickaxe");
		gene("gather.stone_extra", 10, 0, 32, Kind.INT, "extra stone per trip while short of the stock");
		gene("gather.stone_stock", 24, 8, 48, Kind.INT, "stone stock the extra fills toward");
		gene("gather.coal_extra", 4, 0, 16, Kind.INT, "extra coal per coal trip");
		gene("gather.coal_upkeep_below", 4, 0, 16, Kind.INT, "grab coal in view while carrying fewer than this");
		gene("gather.coal_view_dist", 12, 4, 24, Kind.INT, "coal (and iron) ore this close counts as in view");
		gene("gather.iron_in_view", 0, 0, 1, Kind.BOOL, "take iron ore in view while the route still needs iron");
		gene("gather.iron_y", 16, -16, 64, Kind.INT, "height to branch-mine iron at (16: most ore, most caves)");
		gene("gather.coal_y", 45, 0, 96, Kind.INT, "height to branch-mine coal at");
		// Route choices (switches)
		gene("route.armor_before_portal", 1, 0, 1, Kind.BOOL, "chestplate and helmet before the portal");
		gene("route.boots_before_portal", 1, 0, 1, Kind.BOOL, "boots too when 4 ingots are spare");
		gene("route.deep_for_lava", 1, 0, 1, Kind.BOOL, "mine down to lava depth instead of exploring for a pool");
		gene("route.diamond_portal", 0, 0, 1, Kind.BOOL, "portal the classic way (diamond pickaxe, mine 10 obsidian) instead of casting it");
		gene("route.bed", 1, 0, 1, Kind.BOOL, "hunt sheep and make a bed early");
		// Pearls and the stronghold
		gene("route.barter", 0, 0, 1, Kind.BOOL, "get pearls by trading gold with piglins before hunting endermen");
		gene("pearls.barter_ingots", 24, 0, 96, Kind.INT, "gold ingots to gather and trade before hunting endermen for the rest");
		gene("pearls.target", 14, 12, 20, Kind.INT, "ender pearls to collect (14 eyes: 2-3 to triangulate, 12 for the frames)");
		gene("stronghold.sidestep", 80, 30, 200, Kind.REAL, "blocks to walk sideways between the two triangulation throws");
		gene("pearls.boat_trap", 0, 0, 1, Kind.BOOL, "trap endermen in a boat before killing them (they can't teleport or hit back)");
		gene("pearls.provoke_s", 25, 8, 60, Kind.INT, "seconds to wait for a stared-at enderman to come into the boat");
		// Focus: finish the running task unless an emergency comes up
		gene("focus.commit", 0, 0, 1, Kind.BOOL, "finish the running task before switching, unless the top option is an emergency");
		// Main loop timing
		gene("loop.lost_underground_s", 60, 20, 180, Kind.INT, "seconds underground with nothing gained before heading up");
		// Survival: caves, night, lava, ghasts
		gene("cave.torches", 0, 0, 1, Kind.BOOL, "place a torch wherever it's dark underground (mobs spawn only in the dark)");
		gene("cave.torch_light", 3, 0, 7, Kind.INT, "place a torch when block light at our feet is at or below this");
		gene("night.wall_in", 0, 0, 1, Kind.BOOL, "at night, wall in on the spot (fast) instead of digging a shelter hole");
		gene("reflex.lava_margin", 0, 0, 1, Kind.BOOL, "step away from lava right beside us");
		gene("combat.deflect", 0, 0, 1, Kind.BOOL, "hit a ghast's fireball back when it comes close");
		gene("gear.sword_early", 0, 0, 1, Kind.BOOL, "craft a sword matching the pickaxe's tier as soon as possible");
		gene("nav.mob_avoid_coef", 1.5, 1, 5, Kind.REAL, "how much Baritone's paths avoid monsters (1 = not at all)");
		gene("nav.mob_avoid_radius", 8, 4, 16, Kind.INT, "radius around monsters that paths avoid");
		gene("combat.wall_in_anywhere", 0, 0, 1, Kind.BOOL, "hurt and outnumbered: wall in and heal on the surface too, instead of running");
		gene("combat.no_close_retreat", 0, 0, 1, Kind.BOOL, "fight or wall in within four blocks; keep retreating from nearby creepers");
		gene("tools.stone_axe", 1, 0, 1, Kind.BOOL, "craft a stone axe before chopping more wood (logs break ~3x faster)");
		gene("nether.pie_chart", 1, 0, 1, Kind.BOOL, "look for a fortress where the F3 pie chart shows spawners, like speedrunners (pie-ray)");
		gene("loop.stuck_s", 10, 5, 30, Kind.INT, "seconds without moving that mark the state as stuck for the brains");
		gene("stuck.window_s", 12, 6, 40, Kind.INT, "a moving skill kept inside the stuck box this long starts the unstuck reflex");
		gene("stuck.box", 2, 1, 4, Kind.REAL, "the square (blocks) the bot must leave to count as moving");
		// The learned brain (brains/Learned): how far it may overrule the rules' order.
		gene("learned.weight", 0, 0, 3, Kind.REAL, "0 = rules order only; higher trusts the learned model more");
		gene("learned.explore", 0, 0, 0.3, Kind.REAL, "chance to try a non-first option (data for learning)");
		reset();
	}

	public static Map<String, Gene> genes() {
		return GENES;
	}

	public static double get(String name) {
		Double v = values.get(name);
		if (v == null) throw new IllegalArgumentException("unknown gene " + name);
		return v;
	}

	public static int i(String name) {
		return (int) Math.round(get(name));
	}

	public static boolean on(String name) {
		return get(name) >= 0.5;
	}

	/** Back to the defaults (the pre-loop behavior). */
	public static synchronized void reset() {
		values.clear();
		for (Gene g : GENES.values()) values.put(g.name(), g.def());
		source = "defaults";
	}

	/**
	 * Applies a params object ({"gene": value, ...}); unknown names are reported and ignored,
	 * values are clamped to the gene's limits and rounded for whole numbers and switches.
	 */
	public static synchronized String apply(JsonObject params, String from) {
		StringBuilder unknown = new StringBuilder();
		for (Map.Entry<String, JsonElement> e : params.entrySet()) {
			Gene g = GENES.get(e.getKey());
			if (g == null || !e.getValue().isJsonPrimitive()) {
				unknown.append(' ').append(e.getKey());
				continue;
			}
			var p = e.getValue().getAsJsonPrimitive();
			double v = p.isBoolean() ? (p.getAsBoolean() ? 1 : 0) : p.getAsDouble();
			values.put(g.name(), clamp(g, v));
		}
		source = from;
		return unknown.isEmpty() ? "" : "unknown genes ignored:" + unknown;
	}

	static double clamp(Gene g, double v) {
		if (Double.isNaN(v)) return g.def();
		v = Math.max(g.min(), Math.min(g.max(), v));
		return g.kind() == Kind.REAL ? v : Math.round(v);
	}

	/** Loads the params file if it exists; returns a line for the log (never throws). */
	public static String load(Path file) {
		if (file == null || !Files.exists(file)) return "params: defaults";
		try {
			JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			// The loop's files wrap the genes with an id: {"id": "g12", "genes": {...}}.
			String id = o.has("id") ? o.get("id").getAsString() : file.getFileName().toString();
			JsonObject genes = o.has("genes") ? o.getAsJsonObject("genes") : o;
			String warn = apply(genes, id);
			return "params: " + id + " (" + genes.size() + " genes)" + (warn.isEmpty() ? "" : "; " + warn);
		} catch (IOException | RuntimeException e) {
			reset();
			return "params: defaults (couldn't read " + file + ": " + e + ")";
		}
	}

	public static String source() {
		return source;
	}

	/** Genes that differ from their defaults, for the run log. */
	public static JsonObject changed() {
		JsonObject o = new JsonObject();
		for (Gene g : GENES.values()) {
			double v = values.get(g.name());
			if (v != g.def()) o.addProperty(g.name(), v);
		}
		return o;
	}
}
