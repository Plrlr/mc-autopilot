package io.github.plrlr.autopilot;

import com.google.gson.JsonArray;
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
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.skills.PortalSkills;
import io.github.plrlr.autopilot.skills.Skill;
import io.github.plrlr.autopilot.skills.Skills;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.Danger;
import io.github.plrlr.autopilot.state.DangerSense;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

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
	public final Progress progress;
	public final Learned learned = new Learned();
	public final Brain brain = new Brain(learned);

	private boolean enabled;
	private long tick;
	private long enableTick;
	private String worldName;
	private Boolean savedPauseOnLostFocus;

	// Goal
	private Goal goal;
	private long goalSetTick;
	private String lastDim = "";

	// Skill and decisions
	private Skill skill;
	private Option skillOption;
	private long skillStartTick;
	private boolean skillIsReflex;
	private long lastDecisionTick;
	private float healthAtDecision = 20;
	private boolean hostileWasNear;
	private long reflexCooldownUntil;
	private long dangerReflexUntil;
	private long lavaMarginTick = -1000;
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
		// Alt-tabbing would pause the world and freeze the AI mid-fight.
		mc.options.pauseOnLostFocus = false;
		goal = null;
		failures.clear();
		lastPos = null;
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

		checkGoal();
		cave();
		reflexes(pl);
		guard(pl);
		lighting(pl);

		if (skill != null) {
			skill.update();
			if (skill.result() != null) onSkillEnd(true);
		}
		trackStuck(pl);
		triggers(pl);
		status = skill != null ? skillOption.label() : "idle";
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

	private void reflexes(LocalPlayer pl) {
		// Falling far with a water bucket: pour it just before landing (before any cooldown: a fall
		// lasts a second). Fall deaths showed up in several batches.
		if (!pl.onGround() && !pl.isInWater() && pl.fallDistance > Tune.get("reflex.clutch_fall")
				&& Mc.count("water_bucket") > 0 && !Mc.dimension().equals("the_nether")
				&& (skill == null || !skill.name().equals("clutch"))) {
			int ground = io.github.plrlr.autopilot.skills.Clutch.groundBelow(pl);
			if (ground >= 0 && pl.fallDistance + ground > 4) {
				startReflex(new Option("clutch", null, "falling " + Math.round(pl.fallDistance + ground) + " blocks"), "reflex_fall");
				return;
			}
		}
		Danger.Verdict danger = Tune.on("survival.danger_v2") ? DangerSense.assess(seen) : null;
		boolean lavaWork = skill != null && java.util.Set.of("build_portal", "fill_bucket", "make_obsidian", "clutch").contains(skill.name());
		if (danger != null && pl.isOnFire() && !lavaWork && !Mc.dimension().equals("the_nether")
				&& Mc.count("water_bucket") > 0 && Mc.holdItem(s -> Items2.id(s).equals("water_bucket"))) {
			abortSkill("put out fire", false);
			Mc.useOn(pl.blockPosition().below(), net.minecraft.core.Direction.UP);
		}
		if (danger != null && pl.isOnFire() && skill != null && skill.name().equals("collect"))
			abortSkill("burning while mining", false);
		if (danger != null && danger.kind() == Danger.Kind.AVOID_HAZARD && !pl.isInLava()
				&& (!lavaWork || pl.isOnFire())) {
			// Fire underfoot must interrupt mining immediately; water is the fastest extinguish.
			if (danger.dx() != 0 || danger.dz() != 0) {
				abortSkill("move off fire or lava", false);
				pl.setYRot((float) Math.toDegrees(Math.atan2(-danger.dx(), danger.dz())));
				Mc.mc().options.keyUp.setDown(true);
				lavaKeysUntil = tick + 4;
			} else abortSkill("unsafe footing", false);
			return;
		}
		if (tick < reflexCooldownUntil) return;
		// hiding: sealed in and healing (review R1) - the only time reflexes stand down, except the
		// creeper reflex, which always runs (its blast breaks the wall either way).
		boolean hiding = skill instanceof io.github.plrlr.autopilot.skills.NightSkills.Shelter sh && sh.sealed();
		// walling: still building the wall (not yet sealed) with a mob already on us - restarting
		// the escape would only restart the wall, so fight instead.
		boolean walling = !hiding && skill != null && skill.name().equals("shelter") && skillOption != null && "heal".equals(skillOption.arg());
		// Recheck combat with the gene: a pursuer can catch up and a creeper can approach mid-fight.
		boolean reconsiderCombat = (Tune.on("combat.no_close_retreat") || Tune.on("survival.danger_v2")) && skill != null
				&& (skill.name().equals("retreat") || skill.name().equals("attack"));
		if (skill != null && skillIsReflex && !hiding && !reconsiderCombat) return;
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
		// Lava beside us or one step down (gene reflex.lava_margin): step straight away from it,
		// except in the skills that work next to lava on purpose.
		if (Tune.on("reflex.lava_margin") && tick - lavaMarginTick > 20 * 10
				&& (skill == null || !java.util.Set.of("build_portal", "fill_bucket", "make_obsidian", "clutch").contains(skill.name()))) {
			BlockPos feet = pl.blockPosition();
			double ax = 0, az = 0;
			for (int dx = -1; dx <= 1; dx++)
				for (int dz = -1; dz <= 1; dz++)
					for (int dy = -1; dy <= 0; dy++)
						if ((dx != 0 || dz != 0) && Mc.state(feet.offset(dx, dy, dz)).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
							ax -= dx;
							az -= dz;
						}
			if (ax != 0 || az != 0) {
				abortSkill("lava right beside us", false);
				pl.setYRot((float) Math.toDegrees(Math.atan2(-ax, az)));
				Mc.mc().options.keyUp.setDown(true);
				lavaKeysUntil = tick + 6;
				reflexCooldownUntil = lavaKeysUntil;
				lavaMarginTick = tick;
				log.event("reflex", "lava beside us");
				return;
			}
		}
		if (skill != null && skill.ownsSafety()) return;
		Perception.Seen h = seen.nearestHostile();
		if (Tune.on("combat.no_close_retreat")) {
			Perception.Seen creeper = Planner.escapeCreeper(seen);
			if (creeper != null) h = creeper;
		}
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
		if (danger != null && !fortressFight && danger.kind() != Danger.Kind.NONE) {
			Option action = Planner.dangerOption(danger, "visible danger");
			boolean blastThreat = Planner.escapeCreeper(seen) != null;
			boolean urgentSwitch = danger.kind() == Danger.Kind.AVOID_HAZARD
					|| blastThreat && danger.kind() == Danger.Kind.RETREAT;
			if (action != null && (!hiding || blastThreat) && (!walling || blastThreat)
					&& (!skillIsReflex || tick >= dangerReflexUntil || urgentSwitch)
					&& (skillOption == null || !action.label().equals(skillOption.label()))) {
				startReflex(action, "reflex_danger");
				return;
			}
		}
		if (danger == null && h != null && !h.type().equals("enderman")) {
			// A creeper blows the wall open: that reflex stays on even while hiding.
			// From 7 blocks, not 5: a creeper's fuse is 1.5 s, and 4 of batch 10's 25 deaths were
			// blasts that caught the bot already running from 5.
			if (h.type().equals("creeper") && h.dist() < Tune.get("reflex.creeper_dist")) {
				if (!reconsiderCombat || !skill.name().equals("retreat"))
					startReflex(new Option("retreat", null, "creeper close"), "reflex_creeper");
				return;
			}
			if (!hiding && (h.dist() < Tune.get("reflex.melee_dist") || Tune.on("combat.no_close_retreat") && h.dist() <= 4)) {
				// Run only when outnumbered: one mob at arm's length follows and hits our back (batch
				// 10: 14 retreats ended in death), and fighting it behind the shield wins. Not while
				// walling in either, which would only restart the wall. Health 8 is the planner's line
				// too: with 6 here, health 7-8 flipped between fighting and fleeing on every decision.
				if (hp <= Tune.i("combat.flee_hp") && !walling && seen.hostilesWithin(6) >= Tune.i("combat.outnumbered")) {
					List<Option> choices = Planner.escapeChoices(seen, Planner.escape(seen, "low health"));
					double[] x = Tune.on("safety.hazard") ? features() : null;
					Brain.Choice c = brain.decideReflex(choices, x);
					// Keep swinging at the same target instead of resetting the attack every reflex.
					if (!Tune.on("combat.no_close_retreat") || skill == null || skillOption == null
							|| !c.option().label().equals(skillOption.label())) startReflex(choices, c, x, "reflex_low_hp");
				}
				else {
					List<Option> choices = Planner.escapeChoices(seen, new Option("attack", h.type(), "it's attacking"));
					double[] x = Tune.on("safety.hazard") ? features() : null;
					Brain.Choice c = brain.decideReflex(choices, x);
					boolean already = skill != null && (c.option().skill().equals("attack") ? skill.name().equals("attack")
							: skillOption != null && c.option().label().equals(skillOption.label()));
					if (!already) startReflex(choices, c, x, "reflex_fight");
				}
				return;
			}
		}
		// Burning after lava or a fireball: eat. At full hunger health comes back faster than fire
		// takes it; walking on burning killed the bot mid-explore (freebuff nether run 0455). The
		// fortress fight eats on its own.
		boolean eating = skill != null && (skill.name().equals("eat") || skill.name().equals("fortress"));
		if (pl.isOnFire() && hp <= Tune.i("reflex.burning_hp") && pl.getFoodData().getFoodLevel() < 20 && Mc.count(Items2::isAnyFood) > 0 && !eating) {
			startReflex(new Option("eat", null, "burning"), "reflex_burning");
			return;
		}
		boolean safe = seen.hostilesWithin(6) == 0;
		if (pl.getFoodData().getFoodLevel() <= Tune.i("reflex.starving_food") && safe && Mc.count(Items2::isAnyFood) > 0
				&& (skill == null || !skill.name().equals("eat"))) {
			startReflex(new Option("eat", null, "starving"), "reflex_hunger");
		}
	}

	private long lastTorchTick = -1000;
	/** Skills that need the hand or stand still on purpose: no torch in the middle of them. */
	private static final java.util.Set<String> TORCH_BUSY = java.util.Set.of("eat", "craft", "smelt", "build_portal", "fill_bucket",
			"place", "clutch", "shelter", "sleep", "barter", "enderman_boat", "attack", "retreat", "make_obsidian");

	/**
	 * Torches in the dark (gene cave.torches): 129 of 165 deaths in generations 6-9 were
	 * underground, most to mobs, arrows and creepers in dark caves while mining. Monsters spawn only
	 * in darkness (block light 0), so a torch on the floor wherever block light at our feet is at
	 * or below cave.torch_light keeps the tunnels we work in empty, as players do.
	 */
	private void lighting(LocalPlayer pl) {
		if (!Tune.on("cave.torches") || tick % 10 != 0 || tick - lastTorchTick < 30) return;
		if (!Mc.dimension().equals("overworld") || pl.level().canSeeSky(pl.blockPosition().above())) return;
		if (skill != null && TORCH_BUSY.contains(skill.name())) return;
		if (Mc.count("torch") == 0 || !pl.onGround()) return;
		BlockPos feet = pl.blockPosition();
		if (pl.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, feet) > Tune.i("cave.torch_light")) return;
		if (!Mc.state(feet).isAir() || !Mc.solid(feet.below())) return;
		if (!Mc.holdItem(s -> Items2.id(s).equals("torch"))) return;
		Mc.useOn(feet.below(), net.minecraft.core.Direction.UP);
		lastTorchTick = tick;
		log.event("torch", feet.toShortString());
	}

	/** True while guard() holds the shield up (so it lets go of the key itself afterwards). */
	private boolean guarding;

	/**
	 * Shield guard (gene combat.shield_guard): skeleton arrows and creeper blasts caused 67 of 181
	 * deaths in generations 6-9, and a raised shield facing them stops both. A creeper about to
	 * blow within 5 blocks: everything pauses, face it, block. A skeleton drawing its bow at us in
	 * sight within 20: face it and block, but only while we're not walking a path (looking at the
	 * skeleton would steer Baritone off it) or fighting.
	 */
	private void guard(LocalPlayer pl) {
		var o = Mc.mc().options;
		// A ghast's fireball coming at us: a hit sends it back (gene combat.deflect).
		if (Tune.on("combat.deflect") && tick % 4 == 0) {
			for (var e : Mc.mc().level.entitiesForRendering()) {
				if (e instanceof net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball fb && fb.distanceTo(pl) < 4.5
						&& fb.getDeltaMovement().dot(pl.position().subtract(fb.position())) > 0) {
					Mc.lookAt(fb.getBoundingBox().getCenter());
					Mc.mc().gameMode.attack(pl, fb);
					Mc.swing();
					log.event("guard", "fireball hit back");
					return;
				}
			}
		}
		net.minecraft.world.entity.Entity threat = null;
		boolean creeper = false;
		if (Tune.on("combat.shield_guard") && Items2.id(pl.getOffhandItem()).equals("shield")
				&& !Items2.isAnyFood(pl.getMainHandItem()) && !Items2.id(pl.getMainHandItem()).contains("bucket")
				&& (skill == null || !java.util.Set.of("eat", "clutch", "build_portal", "fill_bucket", "place", "barter",
				"enderman_boat", "unstuck", "shelter", "sleep", "craft", "smelt").contains(skill.name()))) {
			boolean still = skill == null || !Bari.pathing() || skill.name().equals("attack");
			for (var e : Mc.mc().level.entitiesForRendering()) {
				double d = e.distanceTo(pl);
				if (e instanceof net.minecraft.world.entity.monster.Creeper c && d < 5 && (c.isIgnited() || c.getSwellDir() > 0)) {
					threat = c;
					creeper = true;
					break;
				}
				if (still && threat == null && e instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton sk && d < 20
						&& sk.isUsingItem() && Mc.canSee(sk)) threat = sk;
			}
		}
		if (threat == null) {
			if (guarding) {
				o.keyUse.setDown(false);
				guarding = false;
			}
			return;
		}
		if (creeper && Bari.pathing()) Bari.stop();
		if (!guarding) log.event("guard", (creeper ? "creeper" : "skeleton") + " at " + Math.round(threat.distanceTo(pl)));
		guarding = true;
		Mc.lookAt(threat.getBoundingBox().getCenter());
		o.keyUse.setDown(true);
	}

	private void startReflex(Option o, String trigger) {
		startReflex(List.of(o), new Brain.Choice(o, 0, 1, "rules", o.why()), null, trigger);
	}

	/**
	 * Reflexes are logged like decisions (layer "reflex"): most fights and escapes start here, and
	 * the danger model can only learn which of them get the bot killed if it sees them.
	 */
	private void startReflex(List<Option> choices, Brain.Choice c, double[] x, String trigger) {
		logDecision("reflex", trigger, choices, x == null ? features() : x, c, choices);
		abortSkill("interrupted by " + trigger, false);
		startSkill(c.option(), trigger, true);
		// The normal reflex cooldown is two seconds; one more second avoids toggling at its edge.
		dangerReflexUntil = trigger.equals("reflex_danger") ? tick + 60 : 0;
		reflexCooldownUntil = tick + 40;
	}

	private double[] features() {
		return Learned.features(memory, seen, progress.deaths(), progress.furthest(), goal);
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
			failures.remove(actionKey(skillOption));
		} else if (instantRepeat) {
			long[] f = failures.computeIfAbsent(actionKey(skillOption), k -> new long[2]);
			if (++f[0] >= 2) {
				f[0] = 0;
				f[1] = tick + 20 * 60;
			}
		} else if (r.code() != Fail.INTERRUPTED && r.code() != Fail.DIED) {
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

	/** Where the bot was, once a second, while a moving skill ran (the stuck box check). */
	private final Deque<Vec3> recentPos = new ArrayDeque<>();
	/** Skills that are supposed to move the bot. Crafting, smelting, eating, hiding or fighting in place are not stuck. */
	private static final java.util.Set<String> MOVING = java.util.Set.of("collect", "explore", "shore", "goto", "retreat", "pickup",
			"fill_bucket", "locate_stronghold");

	/**
	 * Stuck = a moving skill has kept the bot inside a small square (gene stuck.box, 2 blocks) for
	 * stuck.window_s seconds, whatever Baritone says it's doing. The old check only counted while
	 * Baritone reported walking, so an idle Baritone (a mine search in open water) left the bot
	 * standing for minutes (laptop run 2026-09-27: 150 s per try, the same collect again after).
	 * Mining a block is standing still on purpose. Then the unstuck reflex swims, walks, tunnels or
	 * climbs out, and the action that got stuck counts as a failure (paused after repeats).
	 */
	private void trackStuck(LocalPlayer pl) {
		Vec3 p = pl.position();
		if (lastPos == null || p.distanceToSqr(lastPos) > 0.25) {
			lastPos = p;
			lastMoveTick = tick;
		}
		if (skill == null || skillIsReflex || !MOVING.contains(skill.name()) || Mc.mc().gameMode.isDestroying() || skill.workingInPlace()) {
			recentPos.clear();
			return;
		}
		if (tick % 20 != 0) return;
		recentPos.addLast(p);
		int window = Tune.i("stuck.window_s");
		while (recentPos.size() > window) recentPos.removeFirst();
		if (recentPos.size() < window) return;
		double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9, minZ = 1e9, maxZ = -1e9;
		for (Vec3 v : recentPos) {
			minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
			minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
			minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z);
		}
		double box = Tune.get("stuck.box");
		if (maxX - minX >= box || maxZ - minZ >= box || maxY - minY >= 1.5) return;
		recentPos.clear();
		String what = skillOption.label();
		log.event("stuck", what + " at " + pl.blockPosition().toShortString() + (pl.isInWater() ? " in water" : ""));
		abortSkill("stuck: stayed inside " + box + " blocks for " + window + " s", false);
		startSkill(new Option("unstuck", null, "not moving while " + what), "reflex_stuck", true);
		reflexCooldownUntil = tick + 40;
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

	private void requestDecision(String trigger) {
		if (testTask != null) return;
		if (!enabled || goal == null) return;
		if (Mc.player() == null || Mc.player().isDeadOrDying()) return;
		List<Option> options = new ArrayList<>();
		for (Option o : planner.options(goal, seen)) {
			long[] f = failures.get(actionKey(o));
			if (f == null || tick >= f[1]) options.add(o);
		}
		if (options.isEmpty()) options.add(new Option("explore", "any", "everything else failed recently"));
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
	private void logDecision(String layer, String trigger, List<Option> options, double[] x, Brain.Choice c, List<Option> urgent) {
		JsonObject o = new JsonObject();
		o.addProperty("layer", layer);
		o.addProperty("gs", (tick - enableTick) / 20);
		o.addProperty("brain", c.by());
		o.addProperty("trigger", trigger);
		o.addProperty("goal", goal == null ? "" : goal.key());
		JsonArray opts = new JsonArray();
		options.forEach(op -> opts.add(op.label()));
		o.add("options", opts);
		o.addProperty("choice", c.option().label());
		if (!c.by().equals("rules")) o.addProperty("why", c.why());
		JsonArray xs = new JsonArray();
		for (double v : x) xs.add(Math.round(v * 1000) / 1000.0);
		o.add("x", xs);
		o.addProperty("idx", c.index());
		o.addProperty("prop", Math.round(c.propensity() * 1000) / 1000.0);
		JsonArray urg = new JsonArray();
		for (Option op : urgent) urg.add(op.label());
		if (!urg.isEmpty()) o.add("urgent", urg);
		log.write(o);
	}

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

	private int lastItemTotal = -1;
	private long lastGainTick;

	/**
	 * "Lost in a cave": underground over a minute and nothing gained (no item picked up, no
	 * skill finished well). The planner then puts goto surface first. Cobblestone, stone, dirt
	 * and the rest of THROWAWAY don't count as a gain (W6): Baritone picks them up by the
	 * dozen while it digs or branch-mines through anything, which kept resetting the clock and
	 * meant "lost" never fired even while genuinely wandering with nothing useful found.
	 */
	private void cave() {
		if (tick % 20 != 0) return;
		int total = 0;
		for (var st : Mc.player().getInventory().getNonEquipmentItems()) {
			if (Items2.THROWAWAY.contains(Items2.id(st))) continue;
			total += st.getCount();
		}
		if (total > lastItemTotal) lastGainTick = tick;
		lastItemTotal = total;
		long lost = 20L * Tune.i("loop.lost_underground_s");
		planner.lostUnderground = memory.undergroundTicks(tick) > lost && tick - lastGainTick > lost;
	}

	/** Inventory and memory checkpoints on the way to the portal (skills mark the others). */
	private void checkpoints() {
		if (Goal.have("bucket") >= 2 && Checkpoints.mark("two_buckets")) log.event("checkpoint", "two_buckets");
		if (Mc.count("flint_and_steel") > 0 && Checkpoints.mark("flint_and_steel")) log.event("checkpoint", "flint_and_steel");
		if (memory.nearest("lava") != null && Checkpoints.mark("lava_seen")) log.event("checkpoint", "lava_seen");
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
