package io.github.plrlr.autopilot;

import io.github.plrlr.autopilot.brains.Brain;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.plan.Planner;
import io.github.plrlr.autopilot.skills.Skill;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.Danger;
import io.github.plrlr.autopilot.state.DangerSense;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import java.util.List;
final class Reflexes {
	static void run(Autopilot a, LocalPlayer pl) {
		// Falling far with a water bucket: pour it just before landing (before any cooldown: a fall
		// lasts a second). Fall deaths showed up in several batches.
		if (!pl.onGround() && !pl.isInWater() && pl.fallDistance > Tune.get("reflex.clutch_fall")
				&& Mc.count("water_bucket") > 0 && !Mc.dimension().equals("the_nether")
				&& (a.skill == null || !a.skill.name().equals("clutch"))) {
			int ground = io.github.plrlr.autopilot.skills.Clutch.groundBelow(pl);
			if (ground >= 0 && pl.fallDistance + ground > 4) {
				a.startReflex(new Option("clutch", null, "falling " + Math.round(pl.fallDistance + ground) + " blocks"), "reflex_fall");
				return;
			}
		}
		// Lava guard (gene skill.lava_guard): lava showing up beside us while we work gets covered at once.
		if (Tune.on("skill.lava_guard") && a.tick % 3 == 0 && a.skill != null
				&& !java.util.Set.of("build_portal", "cast_portal", "fill_bucket", "make_obsidian", "obsidian_pool", "clutch").contains(a.skill.name())
				&& io.github.plrlr.autopilot.skills.Fluids.LavaGuard.guard()) {
			a.log.event("reflex", "lava covered");
			return;
		}
		// The End's guard (gene skill.end_guard): never meet an enderman's eyes, never fall into the void.
		if (Tune.on("skill.end_guard") && io.github.plrlr.autopilot.skills.EndRoutes.EndGuard.guard()) return;
		// Gene safety.enderman_gaze: look away before an enderman's eyes meet ours (skills/EndermanGaze).
		if (Tune.on("safety.enderman_gaze") && !(a.skill instanceof io.github.plrlr.autopilot.skills.EndermanGaze)
				&& io.github.plrlr.autopilot.skills.EndermanGaze.threat()) {
			a.startReflex(new io.github.plrlr.autopilot.plan.Option("gaze_avoid", null, "an enderman is in view: don't meet its eyes"), "reflex_gaze");
			return;
		}
		Danger.Verdict danger = Tune.on("survival.danger_v2") ? DangerSense.assess(a.seen) : null;
		boolean lavaWork = a.skill != null && java.util.Set.of("build_portal", "fill_bucket", "make_obsidian", "clutch").contains(a.skill.name());
		if (danger != null && pl.isOnFire() && !lavaWork && !Mc.dimension().equals("the_nether")
				&& Mc.count("water_bucket") > 0 && Mc.holdItem(s -> Items2.id(s).equals("water_bucket"))) {
			a.abortSkill("put out fire", false);
			Mc.useOn(pl.blockPosition().below(), net.minecraft.core.Direction.UP);
		}
		if (danger != null && pl.isOnFire() && a.skill != null && a.skill.name().equals("collect"))
			a.abortSkill("burning while mining", false);
		if (danger != null && danger.kind() == Danger.Kind.AVOID_HAZARD && !pl.isInLava()
				&& (!lavaWork || pl.isOnFire())) {
			// Fire underfoot must interrupt mining immediately; water is the fastest extinguish.
			if (danger.dx() != 0 || danger.dz() != 0) {
				a.abortSkill("move off fire or lava", false);
				pl.setYRot((float) Math.toDegrees(Math.atan2(-danger.dx(), danger.dz())));
				Mc.mc().options.keyUp.setDown(true);
				a.lavaKeysUntil = a.tick + 4;
			} else a.abortSkill("unsafe footing", false);
			return;
		}
		if (a.tick < a.reflexCooldownUntil) return;
		// hiding: sealed in and healing (review R1) - the only time reflexes stand down, except the
		// creeper reflex, which always runs (its blast breaks the wall either way).
		boolean hiding = a.skill instanceof io.github.plrlr.autopilot.skills.NightSkills.Shelter sh && sh.sealed()
				|| a.skill instanceof io.github.plrlr.autopilot.skills.PanicBox pb && pb.sealed();
		// walling: still building the wall (not yet sealed) with a mob already on us - restarting
		// the escape would only restart the wall, so fight instead.
		boolean walling = !hiding && a.skill != null && a.skill.name().equals("shelter") && a.skillOption != null && "heal".equals(a.skillOption.arg());
		// Recheck combat with the gene: a pursuer can catch up and a creeper can approach mid-fight.
		boolean reconsiderCombat = a.skill != null && ((Tune.on("combat.no_close_retreat") || Tune.on("survival.danger_v2"))
				&& (a.skill.name().equals("retreat") || a.skill.name().equals("attack")) || java.util.Set.of("kite", "block_arrows", "creeper_defuse").contains(a.skill.name()));
		if (a.skill != null && a.skillIsReflex && !hiding && !reconsiderCombat) return;
		if (pl.isInLava()) {
			// Stop everything, then jump and push forward for a moment (aborting releases keys,
			// so press them after). A new decision is asked once the keys are let go.
			a.abortSkill("in lava", false);
			Mc.mc().options.keyJump.setDown(true);
			Mc.mc().options.keyUp.setDown(true);
			a.lavaKeysUntil = a.tick + 15;
			a.reflexCooldownUntil = a.lavaKeysUntil;
			a.log.event("reflex", "lava");
			return;
		}
		if (pl.isUnderWater() && pl.getAirSupply() < 120) {
			// Out of breath: stop and swim straight up for a moment (holding jump rises in water).
			a.abortSkill("running out of air", false);
			Mc.mc().options.keyJump.setDown(true);
			a.lavaKeysUntil = a.tick + 30;
			a.reflexCooldownUntil = a.lavaKeysUntil;
			a.log.event("reflex", "drowning");
			return;
		}
		// Lava beside us or one step down (gene reflex.lava_margin): step straight away from it,
		// except in the skills that work next to lava on purpose.
		if (Tune.on("reflex.lava_margin") && a.tick - a.lavaMarginTick > 20 * 10
				&& (a.skill == null || !java.util.Set.of("build_portal", "fill_bucket", "make_obsidian", "clutch").contains(a.skill.name()))) {
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
				a.abortSkill("lava right beside us", false);
				pl.setYRot((float) Math.toDegrees(Math.atan2(-ax, az)));
				Mc.mc().options.keyUp.setDown(true);
				a.lavaKeysUntil = a.tick + 6;
				a.reflexCooldownUntil = a.lavaKeysUntil;
				a.lavaMarginTick = a.tick;
				a.log.event("reflex", "lava beside us");
				return;
			}
		}
		if (a.skill != null && a.skill.ownsSafety()) return;
		Perception.Seen h = a.seen.nearestHostile();
		if (Tune.on("combat.no_close_retreat")) {
			Perception.Seen creeper = Planner.escapeCreeper(a.seen);
			if (creeper != null) h = creeper;
		}
		float hp = pl.getHealth();
		// The fortress fight owns every mob in the fortress: it chases blazes, hits whatever is in
		// reach, and backs off to eat on its own. The generic reflexes took it over for blazes (burned
		// to death twice) and wither skeletons (restarted it, loop 0355). Lava, fire and drowning
		// still get their reflexes.
		// (fortress find doesn't fight, so there only blazes are left alone).
		boolean fortressFight = a.skill != null && a.skill.name().equals("fortress") && Mc.dimension().equals("the_nether")
				&& a.skillOption != null && a.skillOption.arg() != null && a.skillOption.arg().startsWith("blazes");
		if (h != null && (fortressFight || h.type().equals("blaze") && a.skill != null && a.skill.name().equals("fortress"))) h = null;
		// A lone mob still several blocks away need not reset the trip for dropped gear. The
		// planner resumes retrieval after a real close-range interruption when the way clears.
		boolean recoverySafe = Tune.on("death.trip_commit") && a.skill != null && (a.skill.name().equals("recover_items")
				|| a.skill.name().equals("goto") && a.skillOption != null && "death".equals(a.skillOption.arg()))
				&& !io.github.plrlr.autopilot.plan.SurvivalPlan.recoveryThreat(a.seen);
		if (recoverySafe) h = null;
		// Walled in on every side and healing: a monster beyond the blocks is no reason to break out.
		// With a gap left (a mob standing in it) the reflexes still act; a creeper's blast breaks
		// the wall either way (review R1: hiding used to turn off every reflex).
		if (danger != null && !fortressFight && danger.kind() != Danger.Kind.NONE) {
			Option action = Planner.dangerOption(danger, "visible danger");
			boolean blastThreat = Planner.escapeCreeper(a.seen) != null;
			boolean urgentSwitch = danger.kind() == Danger.Kind.AVOID_HAZARD
					|| blastThreat && danger.kind() == Danger.Kind.RETREAT;
			if (action != null && (!recoverySafe || urgentSwitch) && (!hiding || blastThreat) && (!walling || blastThreat)
					&& (!a.skillIsReflex || a.tick >= a.dangerReflexUntil || urgentSwitch)
					&& (a.skillOption == null || !action.label().equals(a.skillOption.label()))) {
				a.startReflex(action, "reflex_danger");
				return;
			}
		}
		if (danger == null && h != null && !h.type().equals("enderman")) {
			// A creeper blows the wall open: that reflex stays on even while hiding.
			// From 7 blocks, not 5: a creeper's fuse is 1.5 s, and 4 of batch 10's 25 deaths were
			// blasts that caught the bot already running from 5.
			if (h.type().equals("creeper") && h.dist() < Tune.get("reflex.creeper_dist")) {
				Option response = io.github.plrlr.autopilot.plan.SurvivalPlan.fight(h, io.github.plrlr.autopilot.skills.CombatSkills.canHitCreeper(h)
						? new Option("attack", "creeper", "hit and back off")
						: new Option("retreat", null, "creeper close"));
				if (!reconsiderCombat || a.skillOption == null || !response.label().equals(a.skillOption.label()))
					a.startReflex(response, "reflex_creeper");
				return;
			}
			if (!hiding && !(Tune.on("combat.finish_heal_wall") && io.github.plrlr.autopilot.skills.NightSkills.Shelter.wallHasTime(h.dist())
					&& a.skill instanceof io.github.plrlr.autopilot.skills.NightSkills.Shelter sh && sh.buildingHealWall())
					&& (h.dist() < Tune.get("reflex.melee_dist") || Tune.on("combat.no_close_retreat") && h.dist() <= 4)) {
				// Run only when outnumbered: one mob at arm's length follows and hits our back (batch
				// 10: 14 retreats ended in death), and fighting it behind the shield wins. Not while
				// walling in either, which would only restart the wall. Health 8 is the planner's line
				// too: with 6 here, health 7-8 flipped between fighting and fleeing on every decision.
				if (hp <= Tune.i("combat.flee_hp") && !walling && a.seen.hostilesWithin(6) >= Tune.i("combat.outnumbered")) {
					List<Option> choices = Planner.escapeChoices(a.seen, Planner.escape(a.seen, "low health"));
					double[] x = Tune.on("safety.hazard") ? a.features() : null;
					Brain.Choice c = a.brain.decideReflex(choices, x);
					// Keep swinging at the same target instead of resetting the attack every reflex.
					if (!Tune.on("combat.no_close_retreat") || a.skill == null || a.skillOption == null
							|| !c.option().label().equals(a.skillOption.label())) a.startReflex(choices, c, x, "reflex_low_hp");
				}
				else {
					List<Option> choices = Planner.escapeChoices(a.seen, io.github.plrlr.autopilot.plan.SurvivalPlan.outnumbered(a.seen,
							io.github.plrlr.autopilot.plan.SurvivalPlan.fight(h, new Option("attack", h.type(), "it's attacking"))));
					double[] x = Tune.on("safety.hazard") ? a.features() : null;
					Brain.Choice c = a.brain.decideReflex(choices, x);
					boolean already = a.skill != null && (c.option().skill().equals("attack") ? a.skill.name().equals("attack")
							: a.skillOption != null && c.option().label().equals(a.skillOption.label()));
					if (!already) a.startReflex(choices, c, x, "reflex_fight");
				}
				return;
			}
		}
		// Burning after lava or a fireball: eat. At full hunger health comes back faster than fire
		// takes it; walking on burning killed the bot mid-explore (freebuff nether run 0455). The
		// fortress fight eats on its own.
		boolean eating = a.skill != null && (a.skill.name().equals("eat") || a.skill.name().equals("fortress"));
		if (pl.isOnFire() && hp <= Tune.i("reflex.burning_hp") && pl.getFoodData().getFoodLevel() < 20 && Mc.count(Items2::isAnyFood) > 0 && !eating) {
			a.startReflex(new Option("eat", null, "burning"), "reflex_burning");
			return;
		}
		boolean safe = a.seen.hostilesWithin(6) == 0;
		if (pl.getFoodData().getFoodLevel() <= Tune.i("reflex.starving_food") && safe && Mc.count(Items2::isAnyFood) > 0
				&& (a.skill == null || !a.skill.name().equals("eat"))) {
			a.startReflex(new Option("eat", null, "starving"), "reflex_hunger");
		}
	}

}
