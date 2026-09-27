package io.github.plrlr.autopilot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.plrlr.autopilot.brains.Backends;
import io.github.plrlr.autopilot.brains.ClaudeCli;
import io.github.plrlr.autopilot.brains.Decision;
import io.github.plrlr.autopilot.brains.LlmBackend;
import io.github.plrlr.autopilot.brains.RateLimiter;
import io.github.plrlr.autopilot.brains.Strategist;
import io.github.plrlr.autopilot.brains.Tactician;
import io.github.plrlr.autopilot.log.Checkpoints;
import io.github.plrlr.autopilot.log.Lessons;
import io.github.plrlr.autopilot.log.RunLog;
import io.github.plrlr.autopilot.plan.Goal;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.plan.Planner;
import io.github.plrlr.autopilot.skills.Bari;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.skills.PortalSkills;
import io.github.plrlr.autopilot.skills.Skill;
import io.github.plrlr.autopilot.skills.Skills;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.StateBuilder;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The autopilot's main loop, run once per client tick (20/s) on the game thread.
 * Reflexes react instantly in code; the tactician is asked only on events (skill ended, mob
 * close, hurt, goal changed, stuck, heartbeat); the strategist only on bigger events.
 * AI calls run in the background; the loop just checks whether an answer has arrived.
 */
public final class Autopilot {
	public final Config config;
	public final RunLog log;
	/** What worked and what kept failing, across all runs (lessons.json). */
	public final Lessons lessons;
	public final WorldMemory memory = new WorldMemory();
	public final Planner planner = new Planner(memory);
	public final Progress progress;
	public final Tactician tactician;
	public final Strategist strategist;

	private boolean enabled;
	private long tick;
	private long enableTick;
	private String worldName;
	private Boolean savedPauseOnLostFocus;

	// Goal (strategist)
	private Goal goal;
	private String goalReason = "";
	private List<String> goalSteps = List.of();
	private String goalBrain = "";
	private CompletableFuture<Strategist.Plan> pendingPlan;
	private long lastPlanTick = -1_000_000;
	private long goalSetTick;
	private String lastDim = "";

	// Skill and tactician
	private Skill skill;
	private Option skillOption;
	private long skillStartTick;
	private boolean skillIsReflex;
	private CompletableFuture<Decision> pendingDecision;
	private List<Option> pendingOptions;
	private String pendingTrigger;
	private long lastDecisionTick;
	private float healthAtDecision = 20;
	private boolean hostileWasNear;
	private int consecutiveFails;
	private long reflexCooldownUntil;
	private final Map<String, long[]> failures = new HashMap<>(); // label -> {count, blockedUntilTick}
	private String lastEndedKey = "";

	// Test harness: one skill run on its own, with no decisions around it
	private Option testTask;
	private Skill.Result testTaskResult;

	// Stuck and death
	private Vec3 lastPos;
	private long lastMoveTick;
	private long deathTick = -1;
	/** When the items dropped at the last death despawn (5 minutes of world time after death). */
	private long deathItemsUntilTick = -1;
	private long lavaKeysUntil = -1;

	// For the UI
	private final Deque<String> recent = new ArrayDeque<>();
	private final Deque<String> decisions = new ArrayDeque<>();
	private final ConcurrentLinkedQueue<String> notices = new ConcurrentLinkedQueue<>();
	private final Map<String, Long> lastNotice = new HashMap<>();
	/** "m7@412s": when each milestone was first reached in this session, for speed comparisons. */
	private final List<String> milestoneTimes = new ArrayList<>();
	private Perception seen = new Perception();
	private String status = "off";

	public Autopilot(Config config, Path gameDir) {
		this.config = config;
		Path home = gameDir.resolve("mc-autopilot");
		this.log = new RunLog(home.resolve("logs"));
		this.lessons = new Lessons(home.resolve("lessons.json"));
		this.progress = new Progress(home);
		Path usage = home.resolve("logs");
		ClaudeCli cli = new ClaudeCli(config.str("CLAUDE_CMD"), config.str("OPUS_MODEL"), config.integer("OPUS_TIMEOUT_S", 90), home.resolve("claude-cwd"));
		Map<String, LlmBackend> backends = new LinkedHashMap<>();
		backends.put("opus", new Backends.Opus(cli, new RateLimiter("opus_tactician", 0, 0, 0, config.integer("OPUS_TACTICIAN_MAX_CALLS_PER_HOUR", 120), usage)));
		backends.put("groq", new Backends.OpenAiCompat("groq", "https://api.groq.com/openai/v1/chat/completions", "GROQ_API_KEY",
				config.str("GROQ_API_KEY"), config.str("GROQ_MODEL"),
				new RateLimiter("groq", config.integer("GROQ_MAX_RPM", 30), config.integer("GROQ_MAX_TPM", 8000), config.integer("GROQ_MAX_PER_DAY", 1000), 0, usage)));
		backends.put("cerebras", new Backends.OpenAiCompat("cerebras", "https://api.cerebras.ai/v1/chat/completions", "CEREBRAS_API_KEY",
				config.str("CEREBRAS_API_KEY"), config.str("CEREBRAS_MODEL"),
				new RateLimiter("cerebras", config.integer("CEREBRAS_MAX_RPM", 5), config.integer("CEREBRAS_MAX_TPM", 30000), config.integer("CEREBRAS_MAX_PER_DAY", 2000), 0, usage)));
		backends.put("gemini", new Backends.Gemini(config.str("GEMINI_API_KEY"), config.str("GEMINI_MODEL"),
				new RateLimiter("gemini", config.integer("GEMINI_MAX_RPM", 5), config.integer("GEMINI_MAX_TPM", 100000), config.integer("GEMINI_MAX_PER_DAY", 900), 0, usage)));
		this.tactician = new Tactician(backends, config.str("TACTICIAN").toLowerCase());
		this.strategist = new Strategist(cli, new RateLimiter("opus_strategist", 0, 0, 0, config.integer("OPUS_MAX_CALLS_PER_HOUR", 10), usage),
				config.bool("OPUS_STRATEGIST", true));
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
		Bari.applyFairPlay();
		savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
		// Alt-tabbing would pause the world and freeze the AI mid-fight.
		mc.options.pauseOnLostFocus = false;
		goal = null;
		consecutiveFails = 0;
		failures.clear();
		lastPos = null;
		status = "starting";
		Mc.say("ON. Actions: " + brainLabel() + (strategist.opusEnabled() ? ", goals by Opus" : ", goals by rules")
				+ ". Any movement key takes control back; K opens the panel.");
		log.event("autopilot_on", "tactician=" + tactician.selected() + " strategist_opus=" + strategist.opusEnabled());
		requestPlan("start", true);
	}

	public void disable(String why) {
		if (!enabled) return;
		enabled = false;
		abortSkill(why, false);
		pendingDecision = null;
		pendingPlan = null;
		Bari.stop();
		Bari.restoreUserSettings();
		Skill.releaseKeys();
		if (savedPauseOnLostFocus != null && Mc.mc().options != null) Mc.mc().options.pauseOnLostFocus = savedPauseOnLostFocus;
		savedPauseOnLostFocus = null;
		status = "off";
		lessons.save();
		Mc.say("OFF (" + why + ").");
		log.event("autopilot_off", why);
	}

	// ------------------------------------------------------------------ main loop

	public void tick(Minecraft mc) {
		tick++;
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
		if (tick % 20 == 0) Bari.updateThrowaway();
		if (deathItemsUntilTick >= 0 && tick > deathItemsUntilTick) {
			deathItemsUntilTick = -1;
			for (WorldMemory.Seen d : memory.all("death")) memory.forget("death", d.pos());
		}
		Checkpoints.tick(tick);
		if (tick % 20 == 0) checkpoints();
		if (tick % 20 == 0) {
			int m = progress.update(memory);
			if (m > 0) {
				long secs = (tick - enableTick) / 20;
				milestoneTimes.add("m" + m + "@" + secs + "s");
				Mc.say("Milestone " + m + " reached after " + secs / 60 + " min " + secs % 60 + " s!");
				log.event("milestone", m + " at " + secs + " s");
			}
		}

		checkStrategy();
		cave();
		reflexes(pl);

		if (skill != null) {
			skill.update();
			if (skill.result() != null) onSkillEnd(true);
		}
		trackStuck(pl);
		collectDecision();
		triggers(pl);
		status = skill != null ? skillOption.label() : pendingDecision != null ? "thinking (" + tactician.effective() + ")" : "idle";
	}

	private void checkWorld(Minecraft mc) {
		String name = mc.hasSingleplayerServer() ? mc.getSingleplayerServer().getWorldData().getLevelName() : "multiplayer";
		if (!name.equals(worldName)) {
			worldName = name;
			memory.clear();
			PortalSkills.resetThrows();
			io.github.plrlr.autopilot.skills.Station.forgetPlaced();
			io.github.plrlr.autopilot.skills.SmeltSkill.forgetJobs();
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
				abortSkill("died", false);
				pendingDecision = null;
				Bari.stop();
				progress.died();
				String cause = pl.getLastDamageSource() == null ? "unknown" : pl.getLastDamageSource().type().msgId();
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
			requestPlan("respawned", true);
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
		// Opus may send us exploring; two minutes of it is enough before looking again.
		if (g == Goal.EXPLORE) return g == goal && tick - goalSetTick > 20 * 120;
		return planner.goalDone(g);
	}

	/** One line for Opus about what the code sees: the next step and what keeps failing. */
	private String situation() {
		StringBuilder sb = new StringBuilder();
		sb.append("furthest milestone ").append(progress.furthest()).append("/13");
		if (goal != null) {
			Option main = planner.mainStep(goal, seen);
			if (main != null) sb.append("; next step for ").append(goal.key()).append(": ").append(main.label());
		}
		List<String> blocked = new ArrayList<>();
		for (var e : failures.entrySet()) if (tick < e.getValue()[1]) blocked.add(e.getKey());
		if (!blocked.isEmpty()) sb.append("; keeps failing (paused): ").append(String.join(", ", blocked));
		if (consecutiveFails > 0) sb.append("; failures in a row: ").append(consecutiveFails);
		return sb.toString();
	}

	private void requestPlan(String trigger, boolean force) {
		if (pendingPlan != null) return;
		if (!force && tick - lastPlanTick < 20 * 60) return;
		lastPlanTick = tick;
		Set<Goal> done = doneGoals();
		if (goal == null || done.contains(goal)) {
			// Don't stand around while Opus thinks: start on the rules' pick right away.
			setGoal(Strategist.rules(done::contains, null), "interim");
		}
		JsonObject state = buildState();
		String t = trigger;
		pendingPlan = strategist.plan(state, goal, done::contains, List.copyOf(recent), situation())
				.exceptionally(e -> {
					// Never let a background failure reach the tick loop; fall back to the rules.
					AutopilotMod.LOGGER.warn("Strategist failed ({})", t, e);
					return Strategist.rules(done::contains, "error: " + e.getClass().getSimpleName());
				});
		log.event("strategist_request", trigger);
	}

	private void checkStrategy() {
		if (pendingPlan != null && pendingPlan.isDone()) {
			Strategist.Plan p = pendingPlan.getNow(null);
			pendingPlan = null;
			if (p != null) {
				JsonObject o = new JsonObject();
				o.addProperty("layer", "strategist");
				o.addProperty("brain", p.brain());
				o.addProperty("choice", p.goal().key());
				o.addProperty("reason", p.reason());
				o.addProperty("valid", p.valid());
				o.addProperty("latency_ms", p.ms());
				o.addProperty("tokens_in", p.tokensIn());
				o.addProperty("tokens_out", p.tokensOut());
				if (p.note() != null) o.addProperty("note", p.note());
				log.write(o);
				if (p.note() != null && p.brain().equals("mock") && strategist.opusEnabled()) notices.add("Goals by rules for now: " + p.note());
				setGoal(p, p.brain());
			}
		}
		if (tick % 20 != 0) return;
		String dim = Mc.dimension();
		if (!dim.equals(lastDim)) {
			boolean first = lastDim.isEmpty();
			lastDim = dim;
			if (!first) requestPlan("dimension_change", true);
		}
		if (goal != null && goalFinished(goal)) {
			// Ask Opus at most once a minute; in between, the rules pick the next rung.
			if (tick - lastPlanTick >= 20 * 60) requestPlan("goal_done", true);
			else if (pendingPlan == null) setGoal(Strategist.rules(doneGoals()::contains, null), "interim");
		}
		else if (consecutiveFails >= 3) {
			consecutiveFails = 0;
			requestPlan("failing", false);
		// Every 10 minutes at most: with the default cap of 10 Opus calls an hour, event calls
		// (goal done, failing, death) need most of the budget.
		} else if (tick - lastPlanTick > 20 * 600) requestPlan("periodic", false);
	}

	private void setGoal(Strategist.Plan p, String by) {
		boolean changed = p.goal() != goal;
		goal = p.goal();
		goalReason = p.reason();
		goalSteps = p.steps();
		goalBrain = by;
		if (changed) {
			goalSetTick = tick;
			if (!"interim".equals(by)) Mc.say("Goal: " + goal.description + (goalReason.isEmpty() ? "" : " (" + goalReason + ")"));
			if (skill != null && skill.interruptible()) requestDecision("goal_changed");
		}
	}

	// ------------------------------------------------------------------ reflexes

	private void reflexes(LocalPlayer pl) {
		if (tick < reflexCooldownUntil) return;
		// hiding: sealed in and healing (review R1) - the only time reflexes stand down, except the
		// creeper reflex, which always runs (its blast breaks the wall either way).
		boolean hiding = skill instanceof io.github.plrlr.autopilot.skills.NightSkills.Shelter sh && sh.sealed();
		// walling: still building the wall (not yet sealed) with a mob already on us - restarting
		// the escape would only restart the wall, so fight instead.
		boolean walling = !hiding && skill != null && skill.name().equals("shelter") && skillOption != null && "heal".equals(skillOption.arg());
		// A reflex runs to its end, except hiding: a creeper or a mob at arm's length still counts.
		if (skill != null && skillIsReflex && !hiding) return;
		if (pl.isInLava()) {
			// Stop everything, then jump and push forward for a moment (aborting releases keys,
			// so press them after). A new decision is asked once the keys are let go.
			abortSkill("in lava", false);
			Mc.mc().options.keyJump.setDown(true);
			Mc.mc().options.keyUp.setDown(true);
			lavaKeysUntil = tick + 15;
			reflexCooldownUntil = lavaKeysUntil;
			log.event("reflex", "lava");
			return;
		}
		if (pl.isUnderWater() && pl.getAirSupply() < 120) {
			// Out of breath: stop and swim straight up for a moment (holding jump rises in water).
			abortSkill("running out of air", false);
			Mc.mc().options.keyJump.setDown(true);
			lavaKeysUntil = tick + 30;
			reflexCooldownUntil = lavaKeysUntil;
			log.event("reflex", "drowning");
			return;
		}
		Perception.Seen h = seen.nearestHostile();
		float hp = pl.getHealth();
		// The fortress fight owns every mob in the fortress: it chases blazes, hits whatever is in
		// reach, and backs off to eat on its own. The generic reflexes took it over for blazes (burned
		// to death twice) and wither skeletons (restarted it, loop 0355). Lava, fire and drowning
		// still get their reflexes.
		// (fortress find doesn't fight, so there only blazes are left alone).
		boolean fortressFight = skill != null && skill.name().equals("fortress") && Mc.dimension().equals("the_nether")
				&& skillOption != null && skillOption.arg() != null && skillOption.arg().startsWith("blazes");
		if (h != null && (fortressFight || h.type().equals("blaze") && skill != null && skill.name().equals("fortress"))) h = null;
		// Walled in on every side and healing: a monster beyond the blocks is no reason to break out.
		// With a gap left (a mob standing in it) the reflexes still act; a creeper's blast breaks
		// the wall either way (review R1: hiding used to turn off every reflex).
		if (h != null && !h.type().equals("enderman")) {
			// A creeper blows the wall open: that reflex stays on even while hiding.
			// From 7 blocks, not 5: a creeper's fuse is 1.5 s, and 4 of batch 10's 25 deaths were
			// blasts that caught the bot already running from 5.
			if (h.type().equals("creeper") && h.dist() < 7) {
				startReflex(new Option("retreat", null, "creeper close"), "reflex_creeper");
				return;
			}
			if (!hiding && h.dist() < 3.5) {
				// Run only when outnumbered: one mob at arm's length follows and hits our back (batch
				// 10: 14 retreats ended in death), and fighting it behind the shield wins. Not while
				// walling in either, which would only restart the wall. Health 8 is the planner's line
				// too: with 6 here, health 7-8 flipped between fighting and fleeing on every decision.
				if (hp <= 8 && !walling && seen.hostilesWithin(6) >= 2) startReflex(Planner.escape("low health"), "reflex_low_hp");
				else if (skill == null || !skill.name().equals("attack")) startReflex(new Option("attack", h.type(), "it's attacking"), "reflex_fight");
				return;
			}
		}
		boolean safe = seen.hostilesWithin(6) == 0;
		if (pl.getFoodData().getFoodLevel() <= 6 && safe && Mc.count(Items2::isAnyFood) > 0
				&& (skill == null || !skill.name().equals("eat"))) {
			startReflex(new Option("eat", null, "starving"), "reflex_hunger");
		}
	}

	private void startReflex(Option o, String trigger) {
		abortSkill("interrupted by " + trigger, false);
		startSkill(o, trigger, true);
		reflexCooldownUntil = tick + 40;
	}

	// ------------------------------------------------------------------ skills

	private void startSkill(Option o, String trigger, boolean reflex) {
		Skill s = Skills.create(o.skill());
		if (s == null) {
			log.event("bad_skill", o.label());
			return;
		}
		Skill.releaseKeys();
		skill = s;
		skillOption = o;
		skillIsReflex = reflex;
		skillStartTick = tick;
		lastPos = null;
		s.begin(memory, o.arg());
		JsonObject j = new JsonObject();
		j.addProperty("event", "skill_start");
		j.addProperty("skill", o.label());
		j.addProperty("trigger", trigger);
		log.write(j);
	}

	/** askNext: whether to ask the tactician what to do next (false when we already know). */
	private void abortSkill(String why, boolean askNext) {
		if (skill == null) return;
		Fail code = why.equals("died") ? Fail.DIED : why.startsWith("stuck") ? Fail.STUCK : Fail.INTERRUPTED;
		skill.abort(code, why);
		onSkillEnd(askNext);
	}

	private void onSkillEnd(boolean askNext) {
		Skill.Result r = skill.result();
		String label = skillOption.label();
		String line = label + " -> " + (r.ok() ? "ok" : "failed " + r.code()) + ": " + r.detail();
		recent.addLast(line);
		while (recent.size() > 6) recent.removeFirst();
		JsonObject j = new JsonObject();
		j.addProperty("event", "skill_end");
		j.addProperty("skill", label);
		j.addProperty("ok", r.ok());
		if (r.code() != null) j.addProperty("code", r.code().name());
		j.addProperty("detail", r.detail());
		j.addProperty("seconds", (tick - skillStartTick) / 20.0);
		log.write(j);
		// The same action "succeeding" instantly again and again does nothing (e.g. pickup with
		// nothing reachable): treat the repeat as a failure so it gets paused instead of looping
		// every tick.
		boolean instantRepeat = r.ok() && tick - skillStartTick < 10 && actionKey(skillOption).equals(lastEndedKey);
		lastEndedKey = actionKey(skillOption);
		lessons.record(actionKey(skillOption), r.ok() && !instantRepeat, instantRepeat ? "NO_PROGRESS" : r.code() == null ? null : r.code().name(),
				instantRepeat ? "did nothing" : r.detail(), (tick - skillStartTick) / 20.0);
		// An action that has failed most of the time in past runs gets paused after two fails, not three.
		int pauseAfter = lessons.failRate(actionKey(skillOption)) >= 0.7 ? 2 : 3;
		if (r.ok() && !instantRepeat) {
			lastGainTick = tick;
			consecutiveFails = 0;
			failures.remove(actionKey(skillOption));
		} else if (instantRepeat) {
			long[] f = failures.computeIfAbsent(actionKey(skillOption), k -> new long[2]);
			if (++f[0] >= 2) {
				f[0] = 0;
				f[1] = tick + 20 * 60;
			}
		} else if (r.code() != Fail.INTERRUPTED && r.code() != Fail.DIED) {
			consecutiveFails++;
			long[] f = failures.computeIfAbsent(actionKey(skillOption), k -> new long[2]);
			// Three failures in a row: hide this option for a minute so we don't loop on it.
			if (++f[0] >= pauseAfter) {
				f[0] = 0;
				f[1] = tick + 20 * 60;
			}
		}
		if (testTask != null && skillOption == testTask) testTaskResult = r;
		skill = null;
		skillOption = null;
		skillIsReflex = false;
		if (askNext) requestDecision(r.ok() ? "skill_done" : "skill_failed");
	}

	/** "collect log:3" and "collect log:2" are the same action; counts shrink as progress is made. */
	private static String actionKey(Option o) {
		String a = o.arg() == null ? "" : o.arg();
		int c = a.indexOf(':');
		return o.skill() + " " + (c < 0 ? a : a.substring(0, c));
	}

	private static boolean sameAction(Option a, Option b) {
		return actionKey(a).equals(actionKey(b));
	}

	private void trackStuck(LocalPlayer pl) {
		if (skill == null) return;
		Vec3 p = pl.position();
		if (lastPos == null || p.distanceToSqr(lastPos) > 0.25) {
			lastPos = p;
			lastMoveTick = tick;
			return;
		}
		// Only "stuck" if Baritone is trying to walk and we're not moving. Breaking a block
		// (obsidian takes 9 s) is standing still on purpose.
		if (!Bari.pathing() || Mc.mc().gameMode.isDestroying()) {
			lastMoveTick = tick;
			return;
		}
		long still = tick - lastMoveTick;
		// A fresh start makes Baritone plan a new path from where we are; waiting rarely helps.
		if (still == 20 * 12) {
			abortSkill("stuck for 12 s", true);
			if (consecutiveFails >= 2) requestPlan("stuck", false);
		}
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
		if (skillIsReflex) return;
		// The blaze fight handles getting hurt itself (it backs off out of sight to eat). A "hurt"
		// decision picked eat and stopped it in the open while burning: the laptop's blaze run
		// after e2e2ffe died that way 30 s in.
		if (skill.name().equals("fortress") && skillOption != null && skillOption.arg() != null
				&& skillOption.arg().startsWith("blazes")) return;
		if (newHostile && !skill.name().equals("attack")) requestDecision("mob_near");
		else if (pl.getHealth() < healthAtDecision - 3) requestDecision("hurt");
		else if (skill.interruptible()) {
			String b = tactician.effective();
			long heartbeat = b.equals("opus") ? 20 * 60 : b.equals("mock") ? 20 * 20 : 20 * 30;
			if (tick - lastDecisionTick > heartbeat) requestDecision("heartbeat");
		}
	}

	private void requestDecision(String trigger) {
		if (testTask != null) return;
		if (pendingDecision != null || !enabled || goal == null) return;
		if (Mc.player() == null || Mc.player().isDeadOrDying()) return;
		List<Option> options = new ArrayList<>();
		for (Option o : planner.options(goal, seen)) {
			long[] f = failures.get(actionKey(o));
			if (f == null || tick >= f[1]) options.add(o);
		}
		if (options.isEmpty()) options.add(new Option("explore", "any", "everything else failed recently"));
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
		JsonObject state = buildState();
		pendingOptions = options;
		pendingTrigger = trigger;
		lastDecisionTick = tick;
		healthAtDecision = Mc.player().getHealth();
		pendingDecision = tactician.decide(state, goal, goalSteps, List.copyOf(recent), options, notices::add)
				.exceptionally(e -> {
					AutopilotMod.LOGGER.warn("Tactician failed", e);
					return Decision.mock(options.get(0), "error: " + e.getClass().getSimpleName(), false);
				});
	}

	private void collectDecision() {
		if (pendingDecision == null || !pendingDecision.isDone()) return;
		Decision d = pendingDecision.getNow(null);
		pendingDecision = null;
		if (d == null) return;
		JsonObject o = new JsonObject();
		o.addProperty("layer", "tactician");
		o.addProperty("brain", d.brain());
		o.addProperty("selected", tactician.selected());
		o.addProperty("trigger", pendingTrigger);
		o.addProperty("goal", goal == null ? "" : goal.key());
		JsonArray opts = new JsonArray();
		pendingOptions.forEach(op -> opts.add(op.label()));
		o.add("options", opts);
		o.addProperty("choice", d.choice().label());
		o.addProperty("valid", d.valid());
		o.addProperty("latency_ms", d.latencyMs());
		o.addProperty("tokens_in", d.tokensIn());
		o.addProperty("tokens_out", d.tokensOut());
		if (d.note() != null) o.addProperty("note", d.note());
		log.write(o);

		decisions.addLast(d.brain() + ": " + d.choice().label() + (d.why() == null || d.why().isEmpty() ? "" : " - " + d.why()));
		while (decisions.size() > 8) decisions.removeFirst();

		if (skill != null) {
			if (skillIsReflex || sameAction(d.choice(), skillOption)) return; // keep going
			abortSkill("interrupted: brain chose " + d.choice().label(), false);
		}
		startSkill(d.choice(), pendingTrigger == null ? "decision" : pendingTrigger, false);
	}

	private JsonObject buildState() {
		boolean stuck = skill != null && Bari.pathing() && tick - lastMoveTick > 20 * 10;
		JsonObject state = StateBuilder.build(memory, seen, goal == null ? "none" : goal.key(), recent.isEmpty() ? null : recent.peekLast(),
				!recent.isEmpty() && recent.peekLast().contains("-> ok"), recent.isEmpty() ? "" : recent.peekLast(), stuck,
				progress.deaths(), progress.furthest());
		// Past runs' lessons, so the brains avoid what usually fails.
		List<String> worst = lessons.worst(3);
		if (!worst.isEmpty()) {
			JsonArray l = new JsonArray();
			worst.forEach(l::add);
			state.add("often_fails", l);
		}
		return state;
	}

	// ------------------------------------------------------------------ for the UI and chat

	public String status() {
		return status;
	}

	public Goal goal() {
		return goal;
	}

	public String goalReason() {
		return goalReason;
	}

	public String goalBrain() {
		return goalBrain;
	}

	public List<String> goalSteps() {
		return goalSteps;
	}

	public List<String> recentDecisions() {
		return List.copyOf(decisions);
	}

	private int lastItemTotal = -1;
	private long lastGainTick;

	/**
	 * "Lost in a cave": underground over a minute and nothing gained (no item picked up, no
	 * skill finished well). The planner then puts goto surface first.
	 */
	private void cave() {
		if (tick % 20 != 0) return;
		int total = 0;
		for (var st : Mc.player().getInventory().getNonEquipmentItems()) total += st.getCount();
		if (total > lastItemTotal) lastGainTick = tick;
		lastItemTotal = total;
		planner.lostUnderground = memory.undergroundTicks(tick) > 20 * 60 && tick - lastGainTick > 20 * 60;
	}

	/** Inventory and memory checkpoints on the way to the portal (skills mark the others). */
	private void checkpoints() {
		if (Goal.have("bucket") >= 2 && Checkpoints.mark("two_buckets")) log.event("checkpoint", "two_buckets");
		if (Mc.count("flint_and_steel") > 0 && Checkpoints.mark("flint_and_steel")) log.event("checkpoint", "flint_and_steel");
		if (memory.nearest("lava") != null && Checkpoints.mark("lava_seen")) log.event("checkpoint", "lava_seen");
		if (PortalSkills.placedFrameObsidian() > 0 && Checkpoints.mark("obsidian_placed")) log.event("checkpoint", "obsidian_placed");
		if (memory.nearest("nether_portal") != null && Checkpoints.mark("portal_lit")) log.event("checkpoint", "portal_lit");
	}

	public List<String> milestoneTimes() {
		return List.copyOf(milestoneTimes);
	}

	public List<String> recentResults() {
		return List.copyOf(recent);
	}

	public int opusCallsThisHour() {
		RateLimiter t = ((Backends.Opus) tactician.backend("opus")).limiter();
		return t.callsThisHour() + strategist.limiter().callsThisHour();
	}

	public int opusCapPerHour() {
		RateLimiter t = ((Backends.Opus) tactician.backend("opus")).limiter();
		return t.perHour() + strategist.limiter().perHour();
	}

	/** Force a goal from chat (!goal name). */
	public void forceGoal(Goal g) {
		// Drop a goal decision still on its way; it would overwrite this one a tick later.
		pendingPlan = null;
		setGoal(new Strategist.Plan(g, "set by you", List.of(), "user", true, 0, 0, 0, null), "user");
		lastPlanTick = tick;
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

	public void askOpusNow() {
		requestPlan("user_asked", true);
	}

	/** "auto (groq)" when auto picked a free brain, else the selected name. */
	public String brainLabel() {
		String sel = tactician.selected(), eff = tactician.effective();
		return sel.equals(eff) ? sel : sel + " (" + eff + ")";
	}

	public String statusLine() {
		return "actions " + brainLabel() + ", goal " + (goal == null ? "none" : goal.key()) + ", doing " + status
				+ ", milestone " + progress.furthest() + "/13, deaths " + progress.deaths()
				+ ", Opus calls this hour " + opusCallsThisHour() + "/" + opusCapPerHour();
	}
}
