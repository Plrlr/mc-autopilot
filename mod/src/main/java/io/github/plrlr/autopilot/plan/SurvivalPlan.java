package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.skills.BlockArrows;
import io.github.plrlr.autopilot.skills.CreeperDefuse;
import io.github.plrlr.autopilot.skills.Kite;
import io.github.plrlr.autopilot.skills.MakeBed;
import io.github.plrlr.autopilot.skills.PanicBox;
import io.github.plrlr.autopilot.skills.Pillar;
import io.github.plrlr.autopilot.skills.SecureCamp;
import io.github.plrlr.autopilot.state.Perception;

import java.util.Set;

/**
 * Wave 1 of docs/skills-40.md (stay alive): where each survival skill replaces the rules' old
 * choice. Every hook returns the old option unchanged when its gene is off, so the planner plays
 * exactly as before until the loop has raced the skill.
 */
public final class SurvivalPlan {
	private SurvivalPlan() {}

	/** An escape the rules chose to run: pillar up, box in, or kite instead (in that order of preference). */
	public static Option retreat(Perception seen, Option old) {
		if (old == null || !old.skill().equals("retreat")) return old;
		if (Tune.on("skill.panic_box") && PanicBox.suits(seen) && seen.nearestHostile() != null
				&& !seen.nearestHostile().type().equals("creeper"))
			return new Option("panic_box", null, old.why() + ": box in and heal");
		if (Tune.on("skill.creeper_defuse") && CreeperDefuse.suits(seen.nearest("creeper")))
			return new Option("creeper_defuse", null, old.why() + ": handle the creeper");
		if (Tune.on("skill.kite") && Kite.suits(seen.nearestHostile())) return new Option("kite", null, old.why() + ": face the pursuer");
		return old;
	}

	/** Several melee mobs and nothing that shoots or blows up: rise out of reach (Codex, skill 2). */
	public static Option pillar(Perception seen, String why) {
		if (!Tune.on("skill.pillar") || !Mc.dimension().equals("overworld")) return null;
		// Room to rise: the pillar needs 4 free blocks overhead. Underground there rarely is, and
		// offering it there wasted the escape in the first all-skills trial (NO_ROOM at once, dead 8 s later).
		var feet = Mc.player().blockPosition();
		for (int i = 2; i <= 4; i++) if (!Mc.free(feet.above(i))) return null;
		int melee = 0;
		boolean ranged = false;
		for (Perception.Seen mob : seen.mobs) if (mob.hostile()) {
			// Spiders climb walls, pillars too: they make a pillar pointless, not safer.
			if (Set.of("spider", "cave_spider").contains(mob.type()) && mob.dist() < 12) return null;
			if (Kite.suits(mob)) melee++;
			if (Set.of("skeleton", "stray", "bogged", "creeper").contains(mob.type())) ranged = true;
		}
		return Pillar.canPillar(Mc.count("throwaway"), melee, ranged) ? new Option("pillar", null, why + ": rise above melee") : null;
	}

	/**
	 * Three or more monsters within 6 blocks and a box is possible: box in instead of trading hits
	 * (gene skill.panic_box). The first all-skills trial died this way: three spiders underground,
	 * full health to dead in 20 s, fighting while panic_box and shelter heal sat unused on the list.
	 */
	public static Option outnumbered(Perception seen, Option old) {
		if (old == null || !Tune.on("skill.panic_box") || seen.hostilesWithin(6) < 3 || !PanicBox.suits(seen)) return old;
		return new Option("panic_box", null, "outnumbered " + seen.hostilesWithin(6) + " to 1: box in and heal");
	}

	/** A fight the rules chose (attack or the creeper response): the skill made for that mob instead. */
	public static Option fight(Perception.Seen mob, Option old) {
		if (mob == null || old == null) return old;
		if (Tune.on("skill.creeper_defuse") && CreeperDefuse.suits(mob)) return new Option("creeper_defuse", null, old.why());
		if (Tune.on("skill.block_arrows") && BlockArrows.suits(mob) && old.skill().equals("attack"))
			return new Option("block_arrows", null, old.why() + ": shield up and close in");
		return old;
	}

	// ---- water and lava in the way (wave 6)

	private static volatile String crossTo;
	private static volatile long crossMs;

	/** Explore turned back at water heading this way: offer fluid_cross for the next half minute. */
	public static void crossAhead(String xz) {
		crossTo = xz;
		crossMs = System.currentTimeMillis();
	}

	/** fluid_cross toward where explore was heading, once, soon after it turned back. */
	public static Option cross() {
		String to = crossTo;
		if (to == null || !Tune.on("skill.fluid_cross")) return null;
		crossTo = null;
		if (System.currentTimeMillis() - crossMs > 30_000) return null;
		return new Option("fluid_cross", to, "water ahead: bridge, boat or swim across instead of turning back");
	}

	// ---- after a death

	private static volatile boolean respawnPending;
	private static volatile boolean diedHere;
	public static boolean diedHere() { return diedHere; }
	public static void resetDeath() { diedHere = false; respawnPending = false; }
	private static volatile long respawnMs;

	/** Autopilot calls this when the player respawns. */
	public static void respawned() {
		respawnPending = true;
		diedHere = true;
		respawnMs = System.currentTimeMillis();
	}

	/** respawn_reset started (or isn't wanted): the respawn is handled. */
	public static void respawnHandled() {
		respawnPending = false;
	}

	/** The first thing after a respawn, for a minute (gene skill.respawn_reset). */
	public static Option respawn() {
		if (!respawnPending || !Tune.on("skill.respawn_reset")) return null;
		if (System.currentTimeMillis() - respawnMs > 60_000) {
			respawnPending = false;
			return null;
		}
		return new Option("respawn_reset", null, "just respawned: get safe before anything else");
	}

	/** Going back for dropped items: carefully (gene skill.recover_items). */
	public static Option recover(Option old) {
		if (old == null || !Tune.on("skill.recover_items")) return old;
		return new Option("recover_items", null, old.why() + ", carefully");
	}

	/** A lone distant pursuer does not justify losing a five-minute kit retrieval window. */
	public static boolean recoveryThreat(double hostileDistance, int nearby, double creeperDistance, float health) {
		return health <= 8 || nearby >= 2 || hostileDistance <= 4 || creeperDistance < 7;
	}

	public static boolean recoveryThreat(Perception seen) {
		Perception.Seen hostile = seen.nearestHostile();
		Perception.Seen creeper = seen.nearest("creeper");
		return recoveryThreat(hostile == null ? Double.POSITIVE_INFINITY : hostile.dist(), seen.hostilesWithin(6),
				creeper == null ? Double.POSITIVE_INFINITY : creeper.dist(), Mc.player().getHealth());
	}

	// ---- upkeep

	/** A bed when none is carried and sheep are in sight (gene skill.make_bed). */
	public static Option bed(Perception seen) {
		if (!Tune.on("skill.make_bed") || !Mc.dimension().equals("overworld") || Mc.isNight() || !MakeBed.worthIt(seen)) return null;
		return new Option("make_bed", null, "sheep in sight and no bed: a bed skips nights and moves our respawn here");
	}

	/** The food goal's search: the food ladder instead of exploring for cows (gene skill.food_secure). */
	public static Option food(Option old) {
		if (!Tune.on("skill.food_secure") || !Mc.dimension().equals("overworld")) return old;
		return new Option("food_secure", "6", "food from whatever source works: hunt, remembered animals, fish, explore");
	}

	/** Before long work in one spot in the dark: light it first (gene skill.secure_camp). */
	public static Option beforeWork(Option main) {
		if (main == null || !Tune.on("skill.secure_camp")) return main;
		if (!Set.of("smelt", "build_portal", "cast_portal").contains(main.skill())) return main;
		if (!Mc.dimension().equals("overworld") || !SecureCamp.worthIt()) return main;
		return new Option("secure_camp", null, "light the spot before " + main.label());
	}
}
