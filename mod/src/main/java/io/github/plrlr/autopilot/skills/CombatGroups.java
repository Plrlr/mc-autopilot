package io.github.plrlr.autopilot.skills;

import java.util.List;

/** Small decisions for a pack of visible zombies; coordinates are player-relative. */
final class CombatGroups {
	private CombatGroups() {}

	record Point(double x, double z) {}

	/** Keep finishing one zombie unless it has moved out of reach and another can hit us. */
	static boolean switchTarget(double current, double closest) {
		return current > 3.0 && closest <= 2.8;
	}

	/** A kill is no time to walk into its drops while another zombie can still hit. */
	static boolean collectDrops(int nearbyZombies) {
		return nearbyZombies == 0;
	}

	/** Pick a short safe step that increases the distance to the nearest zombie. */
	static int step(List<Point> zombies, Point[] offsets, boolean[] safe) {
		double best = nearest(zombies, new Point(0, 0));
		int choice = -1;
		for (int i = 0; i < offsets.length; i++) {
			if (!safe[i]) continue;
			double distance = nearest(zombies, offsets[i]);
			if (distance > best + 0.05) { best = distance; choice = i; }
		}
		return choice;
	}

	private static double nearest(List<Point> zombies, Point at) {
		double nearest = Double.POSITIVE_INFINITY;
		for (Point z : zombies) nearest = Math.min(nearest, Math.hypot(z.x - at.x, z.z - at.z));
		return nearest;
	}
}
