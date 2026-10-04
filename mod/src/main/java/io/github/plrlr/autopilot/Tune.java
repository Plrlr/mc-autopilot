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
		gene("death.trip_commit", 0, 0, 1, Kind.BOOL, "keep going for dropped items past routine fights; respond to close or multiple mobs");
		gene("safety.enderman_gaze", 0, 0, 1, Kind.BOOL, "never meet an enderman's eyes (any dimension); fight back when one is angry");
		gene("nether.piglin_threat", 0, 0, 1, Kind.BOOL, "a piglin in attack pose is a threat to answer, a brute always (they were all neutral)");
		gene("death.recover_value", 0, 0, 1, Kind.BOOL, "go back for dropped items only when they're worth it (iron-tier+) and health 12+ or food");
		gene("combat.finish_heal_wall", 0, 0, 1, Kind.BOOL, "let shelter finish placing its healing wall before routine melee reflexes");
		gene("combat.flee_hp", 8, 3, 14, Kind.INT, "at or below this health, run when outnumbered (reflex and planner share it)");
		gene("combat.outnumbered", 2, 1, 4, Kind.INT, "hostiles within 6 blocks that count as outnumbered");
		gene("reflex.starving_food", 6, 2, 12, Kind.INT, "eat at once at or below this hunger when safe");
		gene("combat.crits", 0, 0, 1, Kind.BOOL, "jump before a swing so it lands as a critical hit (1.5x)");
		gene("combat.backstep", 0, 0, 1, Kind.BOOL, "step back out of reach while the sword recharges");
		gene("combat.zombie_pack", 0, 0, 1, Kind.BOOL, "keep space and answer the closest zombie in a pack; leave drops until the pack is gone");
		gene("combat.swing_at", 0.95, 0.6, 1.0, Kind.REAL, "attack cooldown fraction required before a melee swing");
		gene("combat.keep_dist", 2.4, 1.5, 3.5, Kind.REAL, "backstep inside this distance while a melee weapon recharges");
		gene("combat.strafe", 0, 0, 30, Kind.INT, "ticks between side-step direction changes while recharging; zero disables");
		gene("combat.sprint_hit", 0, 0, 1, Kind.BOOL, "sprint into the first melee hit for knockback");
		gene("combat.sprint_hit_dist", 4, 2.5, 6, Kind.REAL, "distance at which the first sprint hit begins");
		gene("combat.creeper_hit", 0, 0, 1, Kind.BOOL, "hit a visible creeper once, then back off before its fuse completes");
		gene("combat.creeper_gap", 6, 4, 8, Kind.REAL, "distance to regain after hitting a creeper");
		gene("combat.skeleton_approach", 0, 0, 1, Kind.BOOL, "zig-zag toward a visible skeleton, guarding with an off-hand shield");
		gene("combat.skeleton_zigzag", 12, 4, 30, Kind.INT, "ticks between direction changes during skeleton approach");
		gene("combat.target_rule", 0, 0, 2, Kind.INT, "target nearest, most previously hit, or a nearby creeper first");
		gene("combat.creeper_priority_range", 5, 3, 8, Kind.REAL, "range for preferring a creeper among several visible hostiles");
		gene("combat.shield_guard", 0, 0, 1, Kind.BOOL, "raise the shield toward a skeleton drawing its bow or a creeper about to blow");
		gene("dragon.max_center_dist", 30, 10, 60, Kind.REAL, "in the End fight, walk back if farther than this from the fountain");
		gene("dragon.wait_dist", 10, 5, 20, Kind.REAL, "where to wait while the dragon flies: this far from the fountain");
		gene("dragon.eat_hp", 12, 4, 18, Kind.INT, "in the End fight, eat at or below this health while it flies");
		gene("reflex.burning_hp", 12, 4, 20, Kind.INT, "eat while burning at or below this health");
		gene("reflex.clutch_fall", 4, 2, 12, Kind.REAL, "falling farther than this with a water bucket: pour it before landing");
		// Planner: danger
		gene("plan.hostile_range", 10, 5, 18, Kind.REAL, "hostiles within this range get an urgent option");
		// Baked in 2026-09-29 (a gene that wins its race becomes the default): hide_hp 15,
		// recover_night_armor 12, armor_before_portal 0, focus.commit 1 and cave.torches 1 are what
		// every champion since gen 49 plays (crowned in g6, g10 and g24).
		gene("plan.hide_hp", 15, 6, 18, Kind.INT, "underground, wall in at or below this health with 2+ monsters near");
		gene("plan.upkeep_calm_radius", 12, 6, 24, Kind.INT, "no upkeep while a hostile is within this radius");
		// Planner: food
		gene("food.eat_at", 14, 8, 18, Kind.INT, "eat at or below this hunger");
		gene("food.eat_hurt_at", 17, 14, 19, Kind.INT, "eat at or below this hunger when health isn't full");
		gene("food.stock", 8, 0, 20, Kind.INT, "cooked food to keep in stock");
		gene("food.keep_full", 0, 0, 1, Kind.BOOL, "eat carried food below 18 hunger so health can regenerate");
		gene("food.early_stock", 0, 0, 1, Kind.BOOL, "stock eight cooked meals before the first iron trip");
		gene("food.search_hunger", 8, 3, 16, Kind.INT, "explore far for animals only at or below this hunger");
		gene("food.hunt_dist", 20, 6, 40, Kind.INT, "hunt animals in view within this distance for the stock");
		// Planner: night, death items
		gene("night.shelter_armor", 10, 0, 20, Kind.INT, "at night on the surface, shelter if armor is below this");
		gene("night.shelter_radius", 16, 6, 32, Kind.INT, "shelter only with a hostile within this radius");
		gene("night.mine_instead", 0, 0, 1, Kind.BOOL, "at night, skip the shelter when the next step is mining underground anyway");
		gene("night.shelter_max_s", 720, 60, 720, Kind.INT, "longest wait in a night shelter before going back to work");
		gene("death.recover_night_armor", 12, 0, 20, Kind.INT, "go back for dropped items at night only with this much armor");
		gene("night.no_shaft_wall", 0, 0, 1, Kind.BOOL, "wall in on safe footing when no nearby shelter shaft can be dug");
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
		gene("route.armor_before_portal", 0, 0, 1, Kind.BOOL, "chestplate and helmet before the portal");
		gene("route.boots_before_portal", 1, 0, 1, Kind.BOOL, "boots too when 4 ingots are spare");
		gene("route.deep_for_lava", 1, 0, 1, Kind.BOOL, "mine down to lava depth instead of exploring for a pool");
		// 2026-09-29 (the user's call): the diamond route is the default; the cast route races back as a control.
		gene("route.diamond_portal", 1, 0, 1, Kind.BOOL, "portal the classic way (diamond pickaxe, mine 10 obsidian) instead of casting it");
		gene("route.deep_portal", 1, 0, 1, Kind.BOOL, "the diamond route as one plan: kit up top, diamonds and a lava pool at depth, frame built in a dug room");
		gene("deep.mold", 1, 0, 1, Kind.BOOL, "obsidian_pool: nothing safe to mine, so make obsidian a block at a time in a dug pit (two buckets)");
		gene("deep.stairs", 0, 0, 1, Kind.BOOL, "diamond_hunt goes down by safe 1-wide stairs first (slow: 12 of 24 min in a trial) instead of collect's own descent");
		gene("deep.branch", 0, 0, 1, Kind.BOOL, "diamond_hunt uses spaced 1x2 tunnels at -58, taking visible veins and backing away from fluids and caves");
		gene("inv.tidy", 1, 0, 1, Kind.BOOL, "toss junk stacks (andesite, spare stone past 128) when fewer than 4 slots are free");
		gene("deep.food", 4, 0, 12, Kind.INT, "cooked food to carry before going down for diamonds");
		gene("deep.sticks", 4, 2, 8, Kind.INT, "sticks to carry down for the diamond pickaxe (torches use them too)");
		gene("route.bed", 1, 0, 1, Kind.BOOL, "hunt sheep and make a bed early");
		// Pearls and the stronghold
		gene("route.barter", 0, 0, 1, Kind.BOOL, "get pearls by trading gold with piglins before hunting endermen");
		gene("pearls.barter_ingots", 24, 0, 96, Kind.INT, "gold ingots to gather and trade before hunting endermen for the rest");
		gene("pearls.target", 14, 12, 20, Kind.INT, "ender pearls to collect (14 eyes: 2-3 to triangulate, 12 for the frames)");
		gene("stronghold.sidestep", 80, 30, 200, Kind.REAL, "blocks to walk sideways between the two triangulation throws");
		gene("pearls.boat_trap", 0, 0, 1, Kind.BOOL, "trap endermen in a boat before killing them (they can't teleport or hit back)");
		gene("pearls.provoke_s", 25, 8, 60, Kind.INT, "seconds to wait for a stared-at enderman to come into the boat");
		// Focus: finish the running task unless an emergency comes up
		gene("focus.commit", 1, 0, 1, Kind.BOOL, "finish the running task before switching, unless the top option is an emergency");
		// Main loop timing
		gene("loop.lost_underground_s", 60, 20, 180, Kind.INT, "seconds underground with nothing gained before heading up");
		// Survival: caves, night, lava, ghasts
		gene("cave.torches", 1, 0, 1, Kind.BOOL, "place a torch wherever it's dark underground (mobs spawn only in the dark)");
		gene("cave.torch_light", 3, 0, 7, Kind.INT, "place a torch when block light at our feet is at or below this");
		gene("cave.seal_openings", 0, 0, 1, Kind.BOOL, "close newly mined cave mouths during iron and coal trips before walking into exposed mobs");
		gene("cave.torch_recipe", 0, 0, 1, Kind.BOOL, "hand craft torches when the recipe book has not unlocked them before a mine trip");
		gene("night.wall_in", 0, 0, 1, Kind.BOOL, "at night, wall in on the spot (fast) instead of digging a shelter hole");
		gene("reflex.lava_margin", 0, 0, 1, Kind.BOOL, "step away from lava right beside us");
		gene("combat.deflect", 0, 0, 1, Kind.BOOL, "hit a ghast's fireball back when it comes close");
		gene("gear.sword_early", 0, 0, 1, Kind.BOOL, "craft a sword matching the pickaxe's tier as soon as possible");
		gene("gear.armor_first", 0, 0, 1, Kind.BOOL, "after the iron pickaxe, make chestplate, sword, helmet and boots before portal work");
		gene("gear.shield_early", 0, 0, 1, Kind.BOOL, "craft and equip a shield after the first iron ingot");
		gene("nav.mob_avoid_coef", 1.5, 1, 5, Kind.REAL, "how much Baritone's paths avoid monsters (1 = not at all)");
		gene("nav.mob_avoid_radius", 8, 4, 16, Kind.INT, "radius around monsters that paths avoid");
		gene("move.shore_first", 0, 0, 1, Kind.BOOL, "reach seen dry ground before ground work or exploring from water");
		gene("combat.wall_in_anywhere", 0, 0, 1, Kind.BOOL, "hurt and outnumbered: wall in and heal on the surface too, instead of running");
		gene("combat.no_close_retreat", 0, 0, 1, Kind.BOOL, "fight or wall in within four blocks; keep retreating from nearby creepers");
		gene("survival.danger_v2", 0, 0, 1, Kind.BOOL, "use one terrain-aware survival verdict in planner, reflexes and retreat");
		gene("survival.melee_lock", 3, 2, 4, Kind.REAL, "within this distance, fight a melee mob instead of turning to run");
		gene("tools.diamond_pick_if_found", 1, 0, 1, Kind.BOOL, "3+ diamonds in the bag: make a diamond pickaxe and portal the classic way");
		gene("tools.stone_axe", 1, 0, 1, Kind.BOOL, "craft a stone axe before chopping more wood (logs break ~3x faster)");
		gene("nether.pie_chart", 1, 0, 1, Kind.BOOL, "look for a fortress where the F3 pie chart shows spawners, like speedrunners (pie-ray)");
		gene("nether.recover_bounded", 0, 0, 1, Kind.BOOL, "fortress recovery answers a mob at arm's length, leaves when healing can't come (no food, hunger < 18) or 45 s pass, and goes home to eat");
		gene("portal.prepare_work_area", 0, 0, 1, Kind.BOOL, "prepare a dry floor for the portal frame when no natural site fits");
		gene("portal.reserve_lava_bucket", 0, 0, 1, Kind.BOOL, "retired 2026-09-29 (always on: two water buckets deadlocked the cast); kept so old genomes load");
		gene("brain.death_avoid", 0, 0, 1, Kind.BOOL, "options that clearly kill us in this context (every game's deaths) go behind the safe ones");
		gene("brain.utility", 0, 0, 1, Kind.BOOL, "every goal's items go through the strategist (cheapest route by measured cost), not only the late game's");
		gene("brain.thompson", 0, 0, 1, Kind.BOOL, "the strategist costs routes with a success chance drawn from its uncertainty (explores thinly tried routes)");
		gene("portal.carve_search", 0, 0, 1, Kind.BOOL, "underground, look for a carvable portal room within 6 blocks, not only at our feet");
		gene("portal.retry_cast_view", 0, 0, 1, Kind.BOOL, "after losing the bucket view, try a different stand for the same frame block");
		gene("portal.site_maker", 0, 0, 1, Kind.BOOL, "dig a visible portal room and repair its floor when natural flat ground is absent");
		gene("skill.kite", 0, 0, 1, Kind.BOOL, "face close melee pursuers and back onto safe ground between charged hits");
		gene("skill.pillar", 0, 0, 1, Kind.BOOL, "rise above clustered melee threats with blocks and descend when safe");
		gene("skill.block_arrows", 0, 0, 1, Kind.BOOL, "close in on skeletons behind a raised shield, lowering it only to strike");
		gene("skill.creeper_defuse", 0, 0, 1, Kind.BOOL, "sprint clear of a lit creeper, else knock it back with a sprint hit and back off");
		gene("skill.panic_box", 0, 0, 1, Kind.BOOL, "box in with 9 blocks on any ground to heal when hurt and cornered (not near creepers)");
		gene("skill.respawn_reset", 0, 0, 1, Kind.BOOL, "after respawning at night, dig or box in until morning before anything else");
		gene("skill.recover_items", 0, 0, 1, Kind.BOOL, "go back for dropped items carefully: stop short, clear the spot, then pick up");
		gene("skill.make_bed", 0, 0, 1, Kind.BOOL, "hunt sheep and craft a bed when sheep are in sight and we have none");
		gene("combat.ignore_walled_mobs", 0, 0, 1, Kind.BOOL, "a close mob we failed to reach, can't see and that isn't hurting us is left alone for a minute");
		gene("safety.spawner_break", 0, 0, 1, Kind.BOOL, "at a live overworld spawner in reach, break it (pickaxe, 12+ hp) instead of walling off and leaving");
		gene("night.shelter_exit", 0, 0, 1, Kind.BOOL, "at morning, climb out of the covered shelter shaft by stairs before moving on");
		gene("nav.surface_v2", 0, 0, 1, Kind.BOOL, "goto surface: judge Baritone by height gained, fall back to digging a staircase up");
		gene("nav.unstuck_v2", 0, 0, 1, Kind.BOOL, "unstuck: climb a staircase first in a pit or under a roof, and before tunnelling");
		gene("combat.commit_target", 0, 0, 1, Kind.BOOL, "while fighting a mob in reach, reflexes may pull us out but not switch us to another fight");
		gene("plan.livelock_break", 0, 0, 1, Kind.BOOL, "a minute of actions with no movement, item or kill is a loop: leave those mobs, pause those actions");
		gene("gather.vein_follow", 0, 0, 1, Kind.BOOL, "after breaking a seen block, take a visible block of the same kind touching it first (follow the vein)");
		gene("gather.cautious_depth", 0, 0, 1, Kind.BOOL, "without armor mine iron no deeper than y 40 (y 48 after a deep death); armor 10+ uses gather.iron_y");
		gene("stuck.item_progress", 0, 0, 1, Kind.BOOL, "an item gained resets the stuck timer (mining in place is not stuck)");
		gene("stuck.ignore_water_bob", 0, 0, 1, Kind.BOOL, "detect a collect trip stuck in water using horizontal movement despite bobbing");
		gene("gather.dry_stone", 0, 0, 1, Kind.BOOL, "prefer only seen stone with no water touching it");
		gene("fluid.enclosed_tunnel", 0, 0, 1, Kind.BOOL, "drain only flooded passages with a solid roof and walls, not open lakes");
		gene("gather.stair_for_stone", 0, 0, 1, Kind.BOOL, "when no dry seen stone is near, reach stone by safe stairs from dry ground");
		gene("restock.stash_only", 0, 0, 1, Kind.BOOL, "restock only after death from a chest we actually stashed spares into");
		gene("plan.noop_success_pause", 0, 0, 1, Kind.BOOL, "pause an action after three successes that changed neither inventory nor position");
		gene("bed.table_first", 0, 0, 1, Kind.BOOL, "make and place a crafting table before crafting a bed");
		gene("safety.spawner_room", 0, 0, 1, Kind.BOOL, "wall off an active overworld spawner and avoid death drops and chests near it");
		gene("skill.food_secure", 0, 0, 1, Kind.BOOL, "get food by a ladder of sources (cook, hunt, remembered animals, fish, explore)");
		gene("skill.fish", 0, 0, 1, Kind.BOOL, "fish with a rod at known water");
		gene("skill.secure_camp", 0, 0, 1, Kind.BOOL, "light the dark ground around us before smelting or casting in one spot");
		gene("skill.stair_down", 0, 0, 1, Kind.BOOL, "dig down by safe 1-wide stairs, checking for lava and drops each step");
		gene("skill.lava_scout", 0, 0, 1, Kind.BOOL, "look for a lava pool with 10+ seen sources before casting");
		gene("skill.cast_portal", 0, 0, 1, Kind.BOOL, "cast the portal on a site we dig out and floor ourselves instead of searching for flat ground");
		gene("skill.ruined_portal", 0, 0, 1, Kind.BOOL, "loot a seen ruined portal's chest and mine its obsidian");
		gene("skill.obsidian_pool", 0, 0, 1, Kind.BOOL, "diamond route: harden a pool and mine 10 obsidian, never one with lava under it");
		gene("skill.diamond_hunt", 0, 0, 1, Kind.BOOL, "safe stairs to diamond depth, then branch-mine, instead of plain collect diamond");
		gene("skill.portal_repair", 0, 0, 1, Kind.BOOL, "light a complete frame in sight or finish ours with carried obsidian");
		gene("skill.stash", 0, 0, 1, Kind.BOOL, "keep spares in a chest at base so a death doesn't cost them");
		gene("skill.restock", 0, 0, 1, Kind.BOOL, "after losing our tools near our chest, take the spares back first");
		gene("skill.nether_arrival", 0, 0, 1, Kind.BOOL, "on entering the Nether, look around and lay a floor at the portal's exit");
		gene("skill.nether_bridge", 0, 0, 1, Kind.BOOL, "reach a far fortress in a straight line, sneak-bridging over lava with a rail");
		gene("skill.fortress_scout", 0, 0, 1, Kind.BOOL, "look for fortresses from an 8-block vantage before walking long legs");
		gene("skill.blaze_farm", 0, 0, 1, Kind.BOOL, "hold a 3-sided nook at the spawner, hit blazes at the opening, seal to heal");
		gene("skill.ghast_defense", 0, 0, 1, Kind.BOOL, "hit fireballs back, shoot the ghast, or wall off its line of sight");
		gene("skill.gold_armor", 0, 0, 1, Kind.BOOL, "wear one gold piece (nether gold ore -> ingots -> helmet or boots) near piglins");
		gene("skill.barter_loop", 0, 0, 1, Kind.BOOL, "gold on, then barter round after round until the pearls are in");
		gene("skill.portal_return", 0, 0, 1, Kind.BOOL, "go home through our portal, rebuilding it from carried obsidian if lost");
		gene("skill.enderman_warped", 0, 0, 1, Kind.BOOL, "farm endermen from a hut with a 2-high roof they can't enter");
		gene("skill.bow_kit", 0, 0, 1, Kind.BOOL, "before the End: a bow and arrows from spiders, chickens and gravel");
		gene("skill.eye_triangulate", 0, 0, 1, Kind.BOOL, "triangulate the stronghold with bearings 5+ degrees apart");
		gene("skill.dig_to_stronghold", 0, 0, 1, Kind.BOOL, "at the stronghold point, safe stairs down (not a straight Baritone dig)");
		gene("skill.stronghold_navigate", 0, 0, 1, Kind.BOOL, "search the stronghold's corridors round after round until the portal room");
		gene("skill.silverfish_control", 0, 0, 1, Kind.BOOL, "break the portal room's silverfish spawner before filling the frames");
		gene("skill.boat_cross", 0, 0, 1, Kind.BOOL, "cross water on the way by boat instead of swimming");
		gene("skill.end_landing", 0, 0, 1, Kind.BOOL, "off the End's spawn platform onto the island, eyes on the ground");
		gene("skill.crystal_hunt", 0, 0, 1, Kind.BOOL, "shoot every crystal, climbing to caged ones to break their bars");
		gene("skill.bed_bomb", 0, 0, 1, Kind.BOOL, "blow up beds at the perched dragon's head (beds explode in the End)");
		gene("skill.dragon_strike", 0, 0, 1, Kind.BOOL, "arrows while the dragon circles, the head fight while it perches");
		gene("skill.end_guard", 0, 0, 1, Kind.BOOL, "End reflex: look away from enderman eyes, block or pearl out of a void fall");
		gene("skill.fluid_seal", 0, 0, 1, Kind.BOOL, "block water or lava sources near us with fireproof blocks");
		gene("skill.lava_guard", 0, 0, 1, Kind.BOOL, "reflex: lava showing beside feet, head or the block being dug gets covered at once");
		gene("skill.fluid_cross", 0, 0, 1, Kind.BOOL, "water or lava in the way: bridge it, boat it or swim it instead of turning back");
		gene("skill.drain_tunnel", 0, 0, 1, Kind.BOOL, "stuck in a flooded tunnel: seal the water's sources and fill the flow");
		gene("nav.fluid_place", 0, 0, 1, Kind.BOOL, "let Baritone lay blocks across water and lava, and sprint-swim");
		gene("mine.tread_water", 0, 0, 1, Kind.BOOL, "breaking a block while floating in water: hold jump to stay level instead of sinking away from it");
		gene("loop.stuck_s", 10, 5, 30, Kind.INT, "seconds without moving that mark the state as stuck for the brains");
		gene("stuck.window_s", 12, 6, 40, Kind.INT, "a moving skill kept inside the stuck box this long starts the unstuck reflex");
		gene("stuck.box", 2, 1, 4, Kind.REAL, "the square (blocks) the bot must leave to count as moving");
		// The learned brain (brains/Learned): how far it may overrule the rules' order.
		gene("learned.weight", 0, 0, 3, Kind.REAL, "0 = rules order only; higher trusts the learned model more");
		gene("learned.explore", 0, 0, 0.3, Kind.REAL, "chance to try a non-first option (data for learning)");
		gene("safety.hazard", 0, 0, 1, Kind.BOOL, "the learned danger model may swap a pick (emergencies too) for a clearly safer option");
		gene("safety.hazard_margin", 0.1, 0.03, 0.4, Kind.REAL, "how much lower the death risk must be to swap (0.1 = 10 points)");
		// Brain v2 (docs/brain-v2.md): routes by learned cost, skill stats, escalation, readiness.
		gene("brain.strategist", 0, 0, 1, Kind.BOOL, "pick late-game routes (portal, rods, pearls, stronghold, End) by learned expected time");
		gene("brain.death_s", 600, 120, 1800, Kind.INT, "what a death costs in the strategist's sums, in seconds (the kit is lost)");
		gene("brain.skill_stats", 0, 0, 1, Kind.BOOL, "options that clearly fail here (learned skill stats) go behind the ones that work");
		gene("reflex.escalate", 0, 0, 1, Kind.BOOL, "fled the same way twice in 30 s: stand and fight or wall in instead of running again");
		gene("plan.readiness", 0, 0, 1, Kind.BOOL, "check the kit before a one-way door (Nether, End) and get what's missing first");
		gene("ready.nether_blocks", 32, 0, 128, Kind.INT, "throwaway blocks to carry into the Nether");
		gene("ready.nether_food", 6, 0, 32, Kind.INT, "food to carry into the Nether");
		gene("ready.end_blocks", 64, 0, 192, Kind.INT, "throwaway blocks to carry into the End");
		gene("ready.end_food", 10, 0, 32, Kind.INT, "food to carry into the End");
		// 2026-10-04 (Claude, the Oct 4 audit): readiness on the path the rules actually take.
		gene("ready.portal_entry", 0, 0, 1, Kind.BOOL, "at a known nether portal, get the route's kit and readiness's blocks, food and flint and steel before walking in");
		gene("ready.nether_gold", 0, 0, 1, Kind.BOOL, "carry a gold helmet into the Nether and wear it there (piglins leave gold alone)");
		EvolvedGenes.load(GENES.keySet()).forEach(e -> gene(e.name(), 0, 0, 1, Kind.BOOL, e.why()));
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
