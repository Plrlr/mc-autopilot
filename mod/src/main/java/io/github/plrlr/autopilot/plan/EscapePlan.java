package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.Danger;
import io.github.plrlr.autopilot.state.DangerSense;
import java.util.ArrayList;
import java.util.List;
final class EscapePlan {
	/**
	 * Getting away from monsters: underground, block up the gaps around us and heal (running
	 * through tunnels got the bot shot and cornered in trials); on the surface, run.
	 */
	public static Option escape(Perception seen, String why) {
		if (Tune.on("survival.danger_v2")) {
			Option verdict = dangerOption(DangerSense.assess(seen), why);
			if (verdict != null) return verdict;
		}
		Option pillar = SurvivalPlan.pillar(seen, why);
		return pillar != null ? pillar : SurvivalPlan.retreat(seen, escape(seen, canHide(), why));
	}

	/**
	 * The rules' pick first, then the other ways to deal with the nearest monster that are possible
	 * here: fight it, run, or wall in and heal. The learned danger model (gene safety.hazard) picks
	 * among them; without it the first is taken, exactly as before.
	 */
	public static List<Option> escapeChoices(Perception seen, Option rulesPick) {
		List<Option> out = new ArrayList<>();
		out.add(rulesPick);
		Option pillar = SurvivalPlan.pillar(seen, rulesPick.why());
		if (pillar != null) out.add(pillar);
		if (Tune.on("skill.panic_box") && io.github.plrlr.autopilot.skills.PanicBox.suits(seen)) out.add(new Option("panic_box", null, rulesPick.why() + ": box in and heal"));
		Perception.Seen h = seen.nearestHostile();
		String why = rulesPick.why();
		if (h != null && !h.type().equals("creeper") && h.dist() <= 6) out.add(new Option("attack", h.type(), why + ": fight it"));
		out.add(SurvivalPlan.retreat(seen, new Option("retreat", null, why + ": run")));
		if (canHide()) out.add(new Option("shelter", "heal", why + ": wall in and heal"));
		List<Option> unique = new ArrayList<>();
		for (Option o : out)
			if (unique.stream().noneMatch(u -> u.label().equals(o.label()))) unique.add(o);
		return unique;
	}

	/** Whether walling in to heal can work here (the same test the rules' escape uses). */
	private static boolean canHide() {
		// Hiding only heals with 18+ hunger or food to eat; otherwise shelter heal fails at once and
		// this would pick it again.
		boolean canHeal = Mc.player().getFoodData().getFoodLevel() >= 18 || Mc.count(Items2.matcher("food")) > 0;
		// Not in the Nether (no sky, so it always looks "underground") or while burning: walled in
		// on fire against blazes, the bot burned to death in the first blaze test.
		boolean hideOk = !Mc.dimension().equals("the_nether") && !Mc.player().isOnFire();
		// Underground always; on the surface too with combat.wall_in_anywhere: 56 of 138 mob deaths in
		// generations 6-9 came while retreating (back turned, ~5 health), running is what got it killed.
		boolean wallOk = !Planner.onSurface() || Tune.on("combat.wall_in_anywhere");
		return wallOk && hideOk && canHeal && Mc.count("throwaway") >= 6;
	}


	/** Translate the one verdict to existing skill names, preserving the decision log schema. */
	public static Option dangerOption(Danger.Verdict verdict, String why) {
		return switch (verdict.kind()) {
			case FIGHT -> new Option("attack", verdict.target(), why + ": fight");
			case RETREAT -> new Option("retreat", null, why + ": safe retreat");
			case WALL_IN -> new Option("shelter", "heal", why + ": wall in");
			case AVOID_HAZARD, NONE -> null;
		};
	}

	/** Same perceived threats for the planner and reflex; no extra scan or hidden information. */
	public static Perception.Seen escapeCreeper(Perception seen) {
		for (Perception.Seen mob : seen.mobs)
			if (mob.hostile() && mob.type().equals("creeper") && mob.dist() < Tune.get("reflex.creeper_dist")) return mob;
		return null;
	}

	static Option escape(Perception seen, boolean canShelter, String why) {
		// Fled twice in 30 s (gene reflex.escalate): running isn't working, so stand our ground,
		// walled in if we can. Not from a creeper: its blast breaks walls and hurts fighters.
		if (Tune.on("reflex.escalate") && Escalation.ranTwice() && escapeCreeper(seen) == null) {
			Perception.Seen h = seen.nearestHostile();
			if (canShelter) return new Option("shelter", "heal", why + ": fled twice already, wall in");
			if (h != null) return new Option("attack", h.type(), why + ": fled twice already, stand and fight");
		}
		boolean noCloseRetreat = Tune.on("combat.no_close_retreat");
		// A second mob can be a creeper: don't wall in or fight with a blast about to happen.
		if (noCloseRetreat && escapeCreeper(seen) != null) return new Option("retreat", null, why + ": creeper close");
		Perception.Seen hostile = seen.nearestHostile();
		// Gene combat.finish_heal_wall: no walling in with a melee mob already on us (it stands in the gap).
		if (canShelter && Tune.on("combat.finish_heal_wall") && hostile != null && hostile.dist() <= 2.0
				&& !hostile.type().equals("creeper"))
			return new Option("attack", hostile.type(), why + ": too close to wall in");
		if (canShelter) return new Option("shelter", "heal", why + ": wall in and heal");
		// Keep the four-block danger zone even if the fight reflex's distance gene is lower.
		if (noCloseRetreat && hostile != null && hostile.dist() <= 4 && !hostile.type().equals("creeper"))
			return new Option("attack", hostile.type(), why + ": too close to turn our back");
		return new Option("retreat", null, why);
	}

}
