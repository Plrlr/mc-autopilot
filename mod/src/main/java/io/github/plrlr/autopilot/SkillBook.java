package io.github.plrlr.autopilot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.plrlr.autopilot.brains.Brain;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.skills.Skill;
import io.github.plrlr.autopilot.skills.Skills;
import java.util.List;
final class SkillBook {
	static void startSkill(Autopilot a, Option o, String trigger, boolean reflex) {
		Skill s = Skills.create(o.skill());
		if (s == null) {
			a.log.event("bad_skill", o.label());
			return;
		}
		Skill.releaseKeys();
		a.skill = s;
		a.skillOption = o;
		a.skillIsReflex = reflex;
		a.skillStartTick = a.tick;
		if (o.skill().equals("retreat")) io.github.plrlr.autopilot.plan.Escalation.fled(a.tick);
		a.skillContexts = a.skillContextsNow();
		a.stuckWatch.resetPosition();
		a.skillInventoryHash = a.inventoryHash();
		a.skillStartPos = Mc.player().blockPosition().immutable();
		s.begin(a.memory, o.arg());
		JsonObject j = new JsonObject();
		j.addProperty("event", "skill_start");
		j.addProperty("skill", o.label());
		j.addProperty("trigger", trigger);
		a.log.write(j);
	}

	/** askNext: whether to ask the tactician what to do next (false when we already know). */
	static void abortSkill(Autopilot a, String why, boolean askNext) {
		if (a.skill == null) return;
		Fail code = why.equals("died") ? Fail.DIED : why.startsWith("stuck") ? Fail.STUCK : Fail.INTERRUPTED;
		a.skill.abort(code, why);
		onSkillEnd(a, askNext);
	}

	static void onSkillEnd(Autopilot a, boolean askNext) {
		Skill.Result r = a.skill.result();
		String label = a.skillOption.label();
		String line = label + " -> " + (r.ok() ? "ok" : "failed " + r.code()) + ": " + r.detail();
		a.recent.addLast(line);
		while (a.recent.size() > 6) a.recent.removeFirst();
		JsonObject j = new JsonObject();
		j.addProperty("event", "skill_end");
		j.addProperty("skill", label);
		j.addProperty("ok", r.ok());
		if (r.code() != null) j.addProperty("code", r.code().name());
		j.addProperty("detail", r.detail());
		j.addProperty("seconds", (a.tick - a.skillStartTick) / 20.0);
		a.log.write(j);
		// Gene plan.livelock_break: a minute of actions going nowhere is a loop (LivelockWatch).
		if (Tune.on("plan.livelock_break")) {
			var pl = Mc.player();
			boolean kill = r.ok() && a.skillOption.skill().equals("attack") && r.detail() != null && r.detail().startsWith("killed");
			var loop = a.livelock.record(a.gameSeconds(), a.actionKey(a.skillOption), pl.getX(), pl.getY(), pl.getZ(), a.inventoryHash(), kill);
			if (loop != null) {
				// Leave the monsters around us alone for a minute (the reflexes then only answer one
				// that comes within reach) and pause the looping actions so the planner picks another.
				for (var m : io.github.plrlr.autopilot.state.Perception.look(24).mobs)
					if (m.hostile() && m.dist() > 4) io.github.plrlr.autopilot.skills.CombatSkills.leaveAlone(m.entity());
				for (String k : loop) a.failures.put(k, new long[]{0, a.tick + 20 * 60});
				a.log.event("livelock", String.join(", ", loop) + " for 60 s without moving, gaining or killing");
			}
		}
		// The same action "succeeding" instantly again and again does nothing (e.g. pickup with
		// nothing reachable): treat the repeat as a failure so it gets paused instead of looping
		// every tick.
		boolean instantRepeat = r.ok() && a.tick - a.skillStartTick < 10 && a.actionKey(a.skillOption).equals(a.lastEndedKey);
		boolean noChange = r.ok() && Tune.on("plan.noop_success_pause")
				&& a.skillInventoryHash == a.inventoryHash() && a.skillStartPos.equals(Mc.player().blockPosition());
		if (noChange) a.noopStreak = a.actionKey(a.skillOption).equals(a.lastNoopKey) ? a.noopStreak + 1 : 1;
		else a.noopStreak = 0;
		a.lastNoopKey = noChange ? a.actionKey(a.skillOption) : "";
		boolean noopRepeat = noChange && a.noopStreak >= 3;
		a.lastEndedKey = a.actionKey(a.skillOption);
		a.lessons.record(a.actionKey(a.skillOption), r.ok() && !instantRepeat && !noopRepeat, instantRepeat || noopRepeat ? "NO_PROGRESS" : r.code() == null ? null : r.code().name(),
				instantRepeat || noopRepeat ? "did nothing" : r.detail(), (a.tick - a.skillStartTick) / 20.0);
		// Explore turned back at open water (gene skill.fluid_cross): next, cross it instead.
		if (!r.ok() && r.code() == Fail.HAZARD && a.skillOption.skill().equals("explore") && r.detail() != null
				&& r.detail().contains("water") && Tune.on("skill.fluid_cross"))
			io.github.plrlr.autopilot.plan.SurvivalPlan.crossAhead(io.github.plrlr.autopilot.skills.Fluids.aheadXZ());
		// Brain v2's skill stats learn from this try at once (an interruption says nothing about the skill).
		if (r.code() != Fail.INTERRUPTED)
			io.github.plrlr.autopilot.brains.SkillStats.shared().record(io.github.plrlr.autopilot.brains.Learned.key(a.skillOption), a.skillContexts,
					r.ok() && !instantRepeat && !noopRepeat, r.code() == Fail.DIED, (a.tick - a.skillStartTick) / 20.0);
		// An action that has failed most of the time in past runs gets paused after two fails, not three.
		int pauseAfter = a.lessons.failRate(a.actionKey(a.skillOption)) >= 0.7 ? 2 : 3;
		if (noopRepeat) {
			long[] f = a.failures.computeIfAbsent(a.actionKey(a.skillOption), k -> new long[2]);
			f[0] = 0;
			f[1] = a.tick + 20 * 60;
			a.noopStreak = 0;
		} else if (r.ok() && !instantRepeat) {
			a.lastGainTick = a.tick;
			a.failures.remove(a.actionKey(a.skillOption));
		} else if (instantRepeat) {
			long[] f = a.failures.computeIfAbsent(a.actionKey(a.skillOption), k -> new long[2]);
			if (++f[0] >= 2) {
				f[0] = 0;
				f[1] = a.tick + 20 * 60;
			}
		} else if (r.code() != Fail.INTERRUPTED && r.code() != Fail.DIED) {
			long[] f = a.failures.computeIfAbsent(a.actionKey(a.skillOption), k -> new long[2]);
			// Three failures in a row: hide this option for a minute so we don't loop on it.
			if (++f[0] >= pauseAfter) {
				f[0] = 0;
				f[1] = a.tick + 20 * 60;
			}
		}
		if (a.testTask != null && a.skillOption == a.testTask) a.testTaskResult = r;
		a.skill = null;
		a.skillOption = null;
		a.skillIsReflex = false;
		if (askNext) a.requestDecision(r.ok() ? "skill_done" : "skill_failed");
	}

	static void logDecision(Autopilot a, String layer, String trigger, List<Option> options, double[] x, Brain.Choice c, List<Option> urgent) {
		JsonObject o = new JsonObject();
		o.addProperty("layer", layer);
		o.addProperty("gs", (a.tick - a.enableTick) / 20);
		o.addProperty("brain", c.by());
		o.addProperty("trigger", trigger);
		o.addProperty("goal", a.goal == null ? "" : a.goal.key());
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
		a.log.write(o);
	}

}
