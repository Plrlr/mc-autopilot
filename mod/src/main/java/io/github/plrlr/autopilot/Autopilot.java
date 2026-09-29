package io.github.plrlr.autopilot;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.plrlr.autopilot.brains.Brain;
import io.github.plrlr.autopilot.brains.Learned;
import io.github.plrlr.autopilot.log.Checkpoints;
import io.github.plrlr.autopilot.log.Lessons;
import io.github.plrlr.autopilot.log.RunLog;
import io.github.plrlr.autopilot.plan.Goal;
import io.github.plrlr.autopilot.plan.GoalLadder;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.plan.Planner;
import io.github.plrlr.autopilot.skills.Bari;
import io.github.plrlr.autopilot.skills.PortalSkills;
import io.github.plrlr.autopilot.skills.Skill;
import io.github.plrlr.autopilot.skills.Skills;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The autopilot's main loop, run once per client tick (20/s) on the game thread.
 * Reflexes react instantly in code. The brain (rules, re-ranked by the learned model) picks the
 * next skill on events: a skill ended, a mob came close, we got hurt, the goal changed, stuck, or
 * a heartbeat. The goal is the lowest unfinished rung of the ladder.
 */
public final class Autopilot {
	public final RunLog log;
	/** What worked and what kept failing, across all runs (lessons.json). */
	public final Lessons lessons;
	public final WorldMemory memory = new WorldMemory();
	public final Planner planner = new Planner(memory);
	private final Lighting lighting = new Lighting();
	private final Guard guard = new Guard();
	final StuckWatch stuckWatch = new StuckWatch();
	public final Progress progress;
	public final Learned learned = new Learned();
	public final Brain brain = new Brain(learned);

	private boolean enabled;
	long tick;
	long enableTick;
	private String worldName;
	private Boolean savedPauseOnLostFocus;
	private net.minecraft.client.InactivityFpsLimit savedInactivityFps;

	// Goal
	Goal goal;
	private long goalSetTick;
	private String lastDim = "";

	// Skill and decisions
	Skill skill;
	Option skillOption;
	long skillStartTick;
	/** The last strategist route written to the log. */
	private String loggedPlan = "";

	/** The skill stats contexts for the state right now. */
	static java.util.List<String> skillContextsNow() {
		return io.github.plrlr.autopilot.brains.SkillStats.contexts(Mc.dimension(), Mc.isNight(),
				!Mc.player().level().canSeeSky(Mc.player().blockPosition().above()));
	}

	/** The skill stats contexts (dimension, night, underground) when the running skill started. */
	java.util.List<String> skillContexts = java.util.List.of();
	boolean skillIsReflex;
	private long lastDecisionTick;
	private float healthAtDecision = 20;
	private boolean hostileWasNear;
	long reflexCooldownUntil;
	long dangerReflexUntil;
	long lavaMarginTick = -1000;
	final Map<String, long[]> failures = new HashMap<>(); // label -> {count, blockedUntilTick}
	final LivelockWatch livelock = new LivelockWatch();
	String lastEndedKey = "";
	String lastNoopKey = "";
	int noopStreak;
	int skillInventoryHash;
	net.minecraft.core.BlockPos skillStartPos;

	// Test harness: one skill run on its own, with no decisions around it
	Option testTask;
	Skill.Result testTaskResult;

	// Stuck and death
	private long deathTick = -1;
	/** When the items dropped at the last death despawn (5 minutes of world time after death). */
	private long deathItemsUntilTick = -1;
	long lavaKeysUntil = -1;

	// For the UI
	final Deque<String> recent = new ArrayDeque<>();
	private final Deque<String> decisions = new ArrayDeque<>();
	private final ConcurrentLinkedQueue<String> notices = new ConcurrentLinkedQueue<>();
	private final Map<String, Long> lastNotice = new HashMap<>();
	/** "m7@412s": when each milestone was first reached in this session, for speed comparisons. */
	private final List<String> milestoneTimes = new ArrayList<>();
	Perception seen = new Perception();
	private String status = "off";

	/** The mod's folder in the Minecraft directory (logs, lessons, params.json, the learned model). */
	private final Path home;

	public Autopilot(Path gameDir) {
		this.home = gameDir.resolve("mc-autopilot");
		this.log = new RunLog(home.resolve("logs"));
		this.lessons = new Lessons(home.resolve("lessons.json"));
		this.progress = new Progress(home);
	}

	// ------------------------------------------------------------------ on / off

	public boolean enabled() {
		return enabled;
	}

	public void toggle() {
		if (enabled) disable("turned off");
		else enable();
	}

	public void enable() {
		Minecraft mc = Mc.mc();
		if (mc.player == null || mc.level == null) return;
		if (!mc.hasSingleplayerServer()) {
			Mc.say("Autopilot only runs in single-player worlds.");
			return;
		}
		enabled = true;
		enableTick = tick;
		milestoneTimes.clear();
		Checkpoints.start(tick);
		// The learning loop's genes: a params file from the test harness, else the user's own
		// mc-autopilot/params.json (e.g. the loop's current champion), else the defaults.
		String params = System.getProperty("autopilot.params", "");
		String paramsLine = Tune.load(params.isBlank() ? home.resolve("params.json") : Path.of(params));
		String model = System.getProperty("autopilot.learned", "");
		learned.loadAsync(model.isBlank() ? home.resolve("learned.json") : Path.of(model));
		Bari.applyFairPlay();
		savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
		// The autopilot's key presses aren't input to the game's AFK check: after ~10 minutes it capped
		// the frame rate at 10, and at 10 fps the game plays at about half speed. Every loop game dropped
		// from ~30 to 10 fps at ~570 s (gen 58's logs): 0.58-0.70x over 30 minutes against ~0.9x in
		// 10-minute runs. Limit only when minimized while we play; the player's own choice comes back after.
		savedInactivityFps = mc.options.inactivityFpsLimit().get();
		mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
		// Alt-tabbing would pause the world and freeze the AI mid-fight.
		mc.options.pauseOnLostFocus = false;
		goal = null;
		failures.clear();
		stuckWatch.resetPosition();
		status = "starting";
		Mc.say("ON (" + brain.label() + "). Any movement key takes control back; K opens the panel.");
		log.event("autopilot_on", "brain=" + brain.label());
		log.event("params", paramsLine + " changed=" + Tune.changed());
		chooseGoal("start");
	}

	public void disable(String why) {
		if (!enabled) return;
		enabled = false;
		abortSkill(why, false);
		Bari.stop();
		Bari.restoreUserSettings();
		Skill.releaseKeys();
		if (savedPauseOnLostFocus != null && Mc.mc().options != null) Mc.mc().options.pauseOnLostFocus = savedPauseOnLostFocus;
		savedPauseOnLostFocus = null;
		if (savedInactivityFps != null && Mc.mc().options != null) Mc.mc().options.inactivityFpsLimit().set(savedInactivityFps);
		savedInactivityFps = null;
		status = "off";
		lessons.save();
		Mc.say("OFF (" + why + ").");
		log.event("autopilot_off", why);
	}

	// ------------------------------------------------------------------ main loop

	/** Time our own code takes per game tick (ms, since the autopilot turned on): is the mod what slows the game? */
	private long tickNanos, tickCount;

	public double msPerTick() {
		return tickCount == 0 ? 0 : tickNanos / 1e6 / tickCount;
	}

	public void tick(Minecraft mc) {
		long t0 = System.nanoTime();
		try {
			tickInner(mc);
		} finally {
			if (enabled) {
				tickNanos += System.nanoTime() - t0;
				tickCount++;
			}
		}
	}

	private void tickInner(Minecraft mc) {
		tick++;
		io.github.plrlr.autopilot.plan.Escalation.tick(tick);
		while (!notices.isEmpty()) {
			String n = notices.poll();
			// The same warning (e.g. "Using rules: ...") at most once a minute, not on every decision.
			Long last = n == null ? null : lastNotice.get(n);
			if (enabled && n != null && (last == null || tick - last > 20 * 60)) {
				lastNotice.put(n, tick);
				Mc.say(n);
			}
		}
		if (mc.player == null || mc.level == null) {
			if (enabled) disable("left the world");
			return;
		}
		checkWorld(mc);
		if (!enabled) return;
		String learnedLine = learned.takeLoadedLine();
		if (learnedLine != null) log.event("learned", learnedLine);
		LocalPlayer pl = mc.player;
		// Death first: the death screen must never block the respawn.
		if (handleDeath(pl)) return;
		// Paused (Esc menu): the world is frozen, so freeze our timers too.
		if (mc.isPaused()) return;
		if (tick == lavaKeysUntil) Skill.releaseKeys();
		if (tick - enableTick > 10 && mc.gui.screen() == null && userIsMoving(mc)) {
			disable("you pressed a movement key");
			return;
		}

		memory.scan(tick);
		if (tick % 5 == 0) seen = Perception.look(32);
		if (tick % 5 == 0 && Tune.on("safety.spawner_room")) io.github.plrlr.autopilot.skills.SpawnerRoom.observe(memory, seen, tick);
		if (tick % 20 == 0) Bari.updateThrowaway();
		if (deathItemsUntilTick >= 0 && tick > deathItemsUntilTick) {
			deathItemsUntilTick = -1;
			for (WorldMemory.Seen d : memory.all("death")) memory.forget("death", d.pos());
		}
		Checkpoints.tick(tick);
		if (tick % 20 == 0) checkpoints();
		// Room in the bag (Tidy): not while a skill is moving items around itself.
		if (tick % 20 == 10 && Tune.on("inv.tidy")
				&& (skill == null || !java.util.Set.of("craft", "smelt", "barter", "stash", "restock", "eat").contains(skill.name()))
				&& Tidy.tick())
			log.event("tidy", "threw out a stack of junk");
		if (tick % 20 == 0) {
			int m = progress.update(memory);
			if (m > 0) {
				long secs = (tick - enableTick) / 20;
				milestoneTimes.add("m" + m + "@" + secs + "s");
				Mc.say("Milestone " + m + " reached after " + secs / 60 + " min " + secs % 60 + " s!");
				log.event("milestone", m + " at " + secs + " s");
			}
		}

		checkGoal();
		lighting.cave(this);
		Reflexes.run(this, pl);
		guard.guard(this, pl);
		lighting.lighting(this, pl);

		if (skill != null) {
			skill.update();
			if (skill.result() != null) onSkillEnd(true);
		}
		stuckWatch.trackStuck(this, pl);
		triggers(pl);
		status = skill != null ? skillOption.label() : "idle";
	}

	private void checkWorld(Minecraft mc) {
		String name = mc.hasSingleplayerServer() ? mc.getSingleplayerServer().getWorldData().getLevelName() : "multiplayer";
		if (!name.equals(worldName)) {
			worldName = name;
			memory.clear();
			PortalSkills.resetThrows();
			io.github.plrlr.autopilot.plan.PortalPlan.reset();
			io.github.plrlr.autopilot.skills.Station.forgetPlaced();
			io.github.plrlr.autopilot.skills.ChestSkills.Stash.forget();
			io.github.plrlr.autopilot.skills.SpawnerRoom.reset();
			io.github.plrlr.autopilot.plan.SurvivalPlan.resetDeath();
			io.github.plrlr.autopilot.skills.SmeltSkill.forgetJobs();
			io.github.plrlr.autopilot.plan.Facts.clear();
			io.github.plrlr.autopilot.brains.SkillStats.shared().clearLocal();
			io.github.plrlr.autopilot.plan.DepthPlan.reset();
			io.github.plrlr.autopilot.brains.Thompson.clear();
			io.github.plrlr.autopilot.plan.Escalation.clear();
			progress.load(name);
			goal = null;
		}
	}

	private boolean handleDeath(LocalPlayer pl) {
		if (pl.isDeadOrDying()) {
			if (deathTick < 0) {
				deathTick = tick;
				// Died again on the way back for our items: they aren't worth a third trip.
				boolean onTheWayBack = skill != null && skill.name().equals("goto") && skillOption != null
						&& "death".equals(skillOption.arg());
				// What we drop now decides whether walking back is worth it (gene death.recover_value).
				io.github.plrlr.autopilot.plan.SurvivalPlan.noteDeath(io.github.plrlr.autopilot.plan.SurvivalPlan.kitValue(Mc::count));
				abortSkill("died", false);
				Bari.stop();
				progress.died();
				io.github.plrlr.autopilot.plan.DepthPlan.noteDeath(pl.getBlockY(), !pl.level().canSeeSky(pl.blockPosition().above()));
				io.github.plrlr.autopilot.plan.Escalation.clear();
				var src = pl.getLastDamageSource();
				String cause = src == null ? "unknown" : src.type().msgId();
				// Name the killer ("mob:enderman"): "mob" alone lumped zombies, endermen and spiders together
				// in every death table (the local trial night2's enderman death read "mob").
				if (src != null && src.getEntity() != null && !(src.getEntity() instanceof LocalPlayer)) cause += ":" + Mc.id(src.getEntity());
				log.event("death", cause);
				// Drops survive 5 minutes unless lava or the void took them; go back for them.
				for (WorldMemory.Seen d : memory.all("death")) memory.forget("death", d.pos());
				if (!onTheWayBack && !cause.equals("lava") && !cause.equals("outOfWorld") && pl.getY() > pl.level().getMinY()) {
					memory.remember("death", pl.blockPosition(), "death");
					deathItemsUntilTick = tick + 20 * 60 * 4 + 20 * 30;
				}
				Mc.say("Died. Respawning and carrying on.");
			}
			if (tick - deathTick == 60) {
				if (Mc.mc().level.getLevelData().isHardcore()) disable("died in hardcore");
				else pl.respawn();
			}
			return true;
		}
		if (deathTick >= 0) {
			deathTick = -1;
			io.github.plrlr.autopilot.plan.SurvivalPlan.respawned();
			chooseGoal("respawned");
		}
		return false;
	}

	/** Physical movement keys (not ones our skills press) mean the user wants control back. */
	private boolean userIsMoving(Minecraft mc) {
		var o = mc.options;
		for (KeyMapping k : new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump}) {
			InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(k);
			if (key.getType() == InputConstants.Type.KEYBOARD && key.getValue() > 0 && InputConstants.isKeyDown(key.getValue())) return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ strategist

	/** Finished goals. Tool and armor rungs stay finished once reached (see Goal.sticky). */
	private Set<Goal> doneGoals() {
		Set<Goal> s = EnumSet.noneOf(Goal.class);
		for (Goal g : Goal.values()) if (goalFinished(g)) s.add(g);
		return s;
	}

	private boolean goalFinished(Goal g) {
		// Rungs below the furthest one reached are behind us: what they gave was used to get further.
		if (g.milestone > 0 && (g.milestone < progress.furthest() || g.sticky() && g.milestone <= progress.furthest())) return true;
		// A forced explore goal (chat !goal explore) lasts two minutes.
		if (g == Goal.EXPLORE) return g == goal && tick - goalSetTick > 20 * 120;
		return planner.goalDone(g);
	}

	/** The goal: the lowest unfinished rung. Logged so a run's goal changes can be read back. */
	private void chooseGoal(String trigger) {
		setGoal(GoalLadder.next(doneGoals()::contains), trigger);
	}

	private void checkGoal() {
		if (tick % 20 != 0) return;
		String dim = Mc.dimension();
		if (!dim.equals(lastDim)) {
			boolean first = lastDim.isEmpty();
			lastDim = dim;
			if (!first) chooseGoal("dimension_change");
		}
		if (goal != null && goalFinished(goal)) chooseGoal("goal_done");
	}

	private void setGoal(Goal g, String trigger) {
		if (g == goal) return;
		goal = g;
		goalSetTick = tick;
		JsonObject o = new JsonObject();
		o.addProperty("layer", "strategist");
		o.addProperty("brain", "rules");
		o.addProperty("choice", g.key());
		o.addProperty("reason", trigger);
		log.write(o);
		Mc.say("Goal: " + g.description);
		if (skill != null && skill.interruptible()) requestDecision("goal_changed");
	}

	// ------------------------------------------------------------------ reflexes




	void startReflex(Option o, String trigger) {
		startReflex(List.of(o), new Brain.Choice(o, 0, 1, "rules", o.why()), null, trigger);
	}

	/**
	* Reflexes are logged like decisions (layer "reflex"): most fights and escapes start here, and
	* the danger model can only learn which of them get the bot killed if it sees them.
	*/
	void startReflex(List<Option> choices, Brain.Choice c, double[] x, String trigger) {
		logDecision("reflex", trigger, choices, x == null ? features() : x, c, choices);
		abortSkill("interrupted by " + trigger, false);
		startSkill(c.option(), trigger, true);
		// The normal reflex cooldown is two seconds; one more second avoids toggling at its edge.
		dangerReflexUntil = trigger.equals("reflex_danger") ? tick + 60 : 0;
		reflexCooldownUntil = tick + 40;
	}

	double[] features() {
		return Learned.features(memory, seen, progress.deaths(), progress.furthest(), goal);
	}

	// ------------------------------------------------------------------ skills


	void startSkill(Option o, String trigger, boolean reflex) { SkillBook.startSkill(this, o, trigger, reflex); }
	void abortSkill(String why, boolean askNext) { SkillBook.abortSkill(this, why, askNext); }
	void onSkillEnd(boolean askNext) { SkillBook.onSkillEnd(this, askNext); }
	void logDecision(String layer, String trigger, List<Option> options, double[] x, Brain.Choice c, List<Option> urgent) {
		SkillBook.logDecision(this, layer, trigger, options, x, c, urgent);
	}

	static int inventoryHash() {
		int hash = 1;
		for (var stack : Mc.player().getInventory().getNonEquipmentItems())
			hash = 31 * hash + java.util.Objects.hash(io.github.plrlr.autopilot.Items2.id(stack), stack.getCount());
		return hash;
	}

	/** Blocks to the nearest hostile we perceive (infinite when none). */
	private double nearestHostileDist() {
		var h = seen == null ? null : seen.nearestHostile();
		return h == null ? Double.POSITIVE_INFINITY : h.dist();
	}

	/** "collect log:3" and "collect log:2" are the same action; counts shrink as progress is made. */
	static String actionKey(Option o) {
		String a = o.arg() == null ? "" : o.arg();
		int c = a.indexOf(':');
		return o.skill() + " " + (c < 0 ? a : a.substring(0, c));
	}

	private static boolean sameAction(Option a, Option b) {
		return actionKey(a).equals(actionKey(b));
	}


	// ------------------------------------------------------------------ tactician

	private void triggers(LocalPlayer pl) {
		if (tick < lavaKeysUntil) return; // escaping lava; decide once the keys are released
		boolean hostileNear = seen.hostilesWithin(8) > 0;
		boolean newHostile = hostileNear && !hostileWasNear;
		hostileWasNear = hostileNear;
		if (skill == null) {
			requestDecision("idle");
			return;
		}
		if (skillIsReflex || skill.ownsSafety()) return;
		// A mob arriving while the healing wall is being placed used to restart combat before
		// the shelter sealed (21 reflex interruptions in gens 57-58). The reflexes still handle
		// creepers and environmental emergencies; the shelter itself detects hits after sealing.
		if (Tune.on("combat.finish_heal_wall") && skill instanceof io.github.plrlr.autopilot.skills.NightSkills.Shelter sh
				&& (sh.buildingHealWall() && io.github.plrlr.autopilot.skills.NightSkills.Shelter.wallHasTime(nearestHostileDist()) || sh.sealed())) return;
		// The blaze fight handles getting hurt itself (it backs off out of sight to eat). A "hurt"
		// decision picked eat and stopped it in the open while burning: the laptop's blaze run
		// after e2e2ffe died that way 30 s in.
		if (skill.name().equals("fortress") && skillOption != null && skillOption.arg() != null
				&& skillOption.arg().startsWith("blazes")) return;
		if (newHostile && !skill.name().equals("attack")) requestDecision("mob_near");
		else if (pl.getHealth() < healthAtDecision - 3) requestDecision("hurt");
		else if (Tune.on("food.keep_full") && !hostileNear && skill.interruptible() && !skill.name().equals("eat")
				&& tick - lastDecisionTick > 5 * 20 && pl.getFoodData().getFoodLevel() < 18 && Planner.wantsToEat())
			requestDecision("hunger below regeneration");
		else if (skill.interruptible()) {
			if (tick - lastDecisionTick > 20 * 20) requestDecision("heartbeat");
		}
	}

	void requestDecision(String trigger) {
		if (testTask != null) return;
		if (!enabled || goal == null) return;
		if (Mc.player() == null || Mc.player().isDeadOrDying()) return;
		List<Option> options = new ArrayList<>();
		for (Option o : planner.options(goal, seen)) {
			long[] f = failures.get(actionKey(o));
			if (f == null || tick >= f[1]) options.add(o);
		}
		if (options.isEmpty()) options.add(new Option("explore", "any", "everything else failed recently"));
		// Brain v2: options that clearly fail here (learned skill stats) go behind the ones that work.
		if (Tune.on("brain.skill_stats")) options = Brain.demoteFailing(options, planner.lastUrgent, skillContextsNow());
		// Learn from deaths: what keeps killing us here goes behind what doesn't (brains/Brain.demoteDeadly).
		if (Tune.on("brain.death_avoid")) options = Brain.demoteDeadly(options, planner.lastUrgent, skillContextsNow());
		// Focus, like a player: a task that's running is finished before the next one, unless the
		// rules' top choice is an emergency (a mob on us, hunger, a creeper). Generation 1 split
		// every iron trip into ~9 pieces of ~20 s: each heartbeat let upkeep or a furnace check win.
		if (skill != null && !skillIsReflex && Tune.on("focus.commit") && !planner.lastUrgent.contains(options.get(0).label())) {
			lastDecisionTick = tick;
			return;
		}
		// Routine re-checks while the rules still want what we're doing: nothing to decide,
		// so don't spend an AI call (or restart the skill) on it.
		if (skill != null && (trigger.equals("heartbeat") || trigger.equals("goal_changed")) && sameAction(options.get(0), skillOption)) {
			lastDecisionTick = tick;
			return;
		}
		// A gathering trip isn't worth breaking off to craft planks or a pickaxe early: the
		// craft waits a few seconds, a second trip back for the rest costs far more.
		if (skill != null && trigger.equals("heartbeat") && skillOption.skill().equals("collect") && options.get(0).skill().equals("craft")) {
			lastDecisionTick = tick;
			return;
		}
		lastDecisionTick = tick;
		healthAtDecision = Mc.player().getHealth();
		double[] x = features();
		Brain.Choice c = brain.decide(options, x, planner.lastUrgent);
		List<Option> urgent = options.stream().filter(op -> planner.lastUrgent.contains(op.label())).toList();
		logDecision("tactician", trigger, options, x, c, urgent);
		// Brain v2: the strategist's route, logged when it changes (the loop's code step reads these).
		if (!planner.lastPlan.equals(loggedPlan)) {
			loggedPlan = planner.lastPlan;
			log.event("plan", loggedPlan);
		}
		decisions.addLast(c.by() + ": " + c.option().label() + (c.why() == null || c.why().isEmpty() ? "" : " - " + c.why()));
		while (decisions.size() > 8) decisions.removeFirst();
		if (skill != null) {
			if (skillIsReflex || sameAction(c.option(), skillOption)) return; // keep going
			abortSkill("interrupted: brain chose " + c.option().label(), false);
		}
		startSkill(c.option(), trigger, false);
	}

	/**
	* One line per decision. The loop's trainer reads layer "tactician" and "reflex" rows: the game
	* second, the state features, which option was taken and how likely it was, and the emergencies.
	*/

	// ------------------------------------------------------------------ for the UI and chat

	public String status() {
		return status;
	}

	public Goal goal() {
		return goal;
	}

	public List<String> recentDecisions() {
		return List.copyOf(decisions);
	}

	int lastItemTotal = -1;
	long lastGainTick;

	/** Inventory and memory checkpoints on the way to the portal (skills mark the others). */
	private void checkpoints() {
		if (Goal.have("bucket") >= 2 && Checkpoints.mark("two_buckets")) log.event("checkpoint", "two_buckets");
		if (Mc.count("flint_and_steel") > 0 && Checkpoints.mark("flint_and_steel")) log.event("checkpoint", "flint_and_steel");
		if (memory.nearest("lava") != null && Checkpoints.mark("lava_seen")) log.event("checkpoint", "lava_seen");
		// The diamond route's steps (the default since 2026-09-29): down to the lava caves, the
		// pickaxe, a frame's worth of obsidian. They give the loop's score a slope to climb.
		if (Mc.dimension().equals("overworld") && Mc.player().getBlockY() <= -40 && Checkpoints.mark("at_depth")) log.event("checkpoint", "at_depth");
		if (Items2.bestTier("pickaxe") >= 3 && Checkpoints.mark("diamond_pickaxe")) log.event("checkpoint", "diamond_pickaxe");
		if (Goal.have("obsidian") >= 10 && Checkpoints.mark("obsidian_10")) log.event("checkpoint", "obsidian_10");
		if (PortalSkills.placedFrameObsidian() > 0 && Checkpoints.mark("obsidian_placed")) log.event("checkpoint", "obsidian_placed");
		if (memory.nearest("nether_portal") != null && Checkpoints.mark("portal_lit")) log.event("checkpoint", "portal_lit");
	}

	/** Game seconds since the autopilot turned on (client ticks / 20). */
	public long gameSeconds() {
		return (tick - enableTick) / 20;
	}

	public List<String> milestoneTimes() {
		return List.copyOf(milestoneTimes);
	}

	public List<String> recentResults() {
		return List.copyOf(recent);
	}

	/** Force a goal from chat (!goal name). */
	public void forceGoal(Goal g) {
		setGoal(g, "user");
	}

	/**
	* Test harness only: run one skill by itself (no brain decisions while it runs) to check a
	* single fix quickly. taskResult() is set when it ends.
	*/
	public void runTask(Option o) {
		abortSkill("test task", false);
		testTask = o;
		testTaskResult = null;
		startSkill(o, "test_task", false);
	}

	public Skill.Result taskResult() {
		return testTaskResult;
	}

	public String statusLine() {
		return "brain " + brain.label() + ", goal " + (goal == null ? "none" : goal.key()) + ", doing " + status
				+ ", milestone " + progress.furthest() + "/13, deaths " + progress.deaths();
	}
}
