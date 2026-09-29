package io.github.plrlr.autopilot;

/** The movement test used by the stuck detector; water bobbing is not travel. */
final class StuckBox {
	private StuckBox() {}

	static boolean stalled(double xSpread, double ySpread, double zSpread, double box,
			boolean inWater, boolean ignoreWaterBob) {
		return xSpread < box && zSpread < box && (inWater && ignoreWaterBob || ySpread < 1.5);
	}
}
