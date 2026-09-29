package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.skills.EndRoutes;
import io.github.plrlr.autopilot.skills.PortalSkills;
import io.github.plrlr.autopilot.skills.SearchStronghold;
import io.github.plrlr.autopilot.state.WorldMemory;

/**
 * Waves 4 and 5 of docs/skills-40.md (the stronghold and the End): where each skill replaces the
 * rules' step. Every hook returns the old option when its gene is off.
 */
public final class LatePlan {
	private LatePlan() {}

	/** Finding the stronghold: boat over water, dig down at a known point, or triangulate. */
	public static Option locate(Option old) {
		var pl = Mc.player();
		if (Tune.on("skill.boat_cross") && pl.isInWater() && Mc.count(Items2.matcher("boat")) > 0
				&& PortalSkills.LocateStronghold.knownPoint() != null)
			return new Option("boat_cross", null, "water on the way: cross it by boat");
		var point = PortalSkills.LocateStronghold.knownPoint();
		if (Tune.on("skill.dig_to_stronghold") && point != null && !SearchStronghold.inStronghold()
				&& Math.hypot(point.x - pl.getX(), point.z - pl.getZ()) < 24)
			return new Option("dig_to_stronghold", null, "the stronghold is below: safe stairs down");
		if (Tune.on("skill.eye_triangulate") && Mc.count("ender_eye") >= 2)
			return new Option("eye_triangulate", null, "triangulate with bearings 5+ degrees apart");
		return old;
	}

	/** Inside the stronghold: round after round of corridor search. */
	public static Option search(Option old) {
		return Tune.on("skill.stronghold_navigate") ? new Option("stronghold_navigate", null, "search the corridors round after round") : old;
	}

	/**
	 * Before filling the frames (the End is one way): the End kit first. A bow and arrows for the
	 * crystals, beds for the bed route, the silverfish spawner broken.
	 */
	public static Option beforeFill(Option old, WorldMemory memory) {
		if (Tune.on("skill.silverfish_control") && !Facts.now(memory).contains("room_safe")) {
			WorldMemory.Seen f = memory.nearest("end_portal_frame");
			for (WorldMemory.Seen s : memory.all("spawner"))
				if (f != null && s.pos().distSqr(f.pos()) < 16 * 16)
					return new Option("silverfish_control", null, "break the silverfish spawner first");
		}
		if (Tune.on("skill.bow_kit") && (Mc.count("bow") == 0 || Mc.count("arrow") < 16) && Mc.dimension().equals("overworld"))
			return new Option("bow_kit", "32", "a bow and arrows for the crystals before the End");
		if (Tune.on("skill.bed_bomb") && Mc.count(Items2.matcher("bed")) < 5 && Mc.dimension().equals("overworld"))
			return new Option("make_bed", "6", "beds for bed bombs before the End");
		return old;
	}

	/** In the End: land, crystals, then the kill (beds or the strike). Null: the old order. */
	public static Option end() {
		if (!Mc.dimension().equals("the_end")) return null;
		if (Tune.on("skill.end_landing") && !EndRoutes.onIslandNow()) return new Option("end_landing", null, "onto the main island");
		if (Tune.on("skill.crystal_hunt") && Mc.count("bow") > 0 && Mc.count("arrow") > 0 && EndRoutes.crystalsInSight())
			return new Option("crystal_hunt", null, "every crystal down, caged ones too");
		if (Tune.on("skill.bed_bomb") && Mc.count(Items2.matcher("bed")) > 0) return new Option("bed_bomb", null, "bed blasts at the perched dragon's head");
		if (Tune.on("skill.dragon_strike")) return new Option("dragon_strike", null, "arrows while it circles, the head while it perches");
		return null;
	}
}
