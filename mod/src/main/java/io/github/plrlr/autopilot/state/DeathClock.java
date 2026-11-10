package io.github.plrlr.autopilot.state;

/**
 * The despawn clock for our dropped items (2026-11-09, gene death.recovery_v2).
 *
 * Dropped items vanish after 6000 ticks (5 min), but they only age while their chunk is loaded
 * and the game runs. The old timer counted wall ticks from the death (4.5 min) and so forgot items
 * that were still lying there: after a far respawn, a pause, or a trip to the other dimension.
 * This one counts only ticks the bot is alive, unpaused, in the death's dimension and within
 * LOADED_RADIUS of the spot (a little less than the simulation distance, so it errs early).
 *
 * Pure: the caller feeds it the player's place each unpaused live tick. Unit-tested.
 */
public final class DeathClock {
	/** 4.5 minutes of loaded time: half a minute's margin before the game's 5. */
	public static final long BUDGET = 20L * 60 * 4 + 20 * 30;
	public static final double LOADED_RADIUS = 128;

	private static final DeathClock SHARED = new DeathClock();

	public static DeathClock shared() {
		return SHARED;
	}

	private String dim;
	private int x, y, z;
	/** Loaded ticks spent; -1 = no drops being timed. */
	private long used = -1;

	public synchronized void start(String dim, int x, int y, int z, long alreadyUsed) {
		this.dim = dim;
		this.x = x;
		this.y = y;
		this.z = z;
		used = Math.max(0, alreadyUsed);
	}

	public synchronized void stop() {
		used = -1;
	}

	/** Drops are being timed (not yet expired or recovered). */
	public synchronized boolean active() {
		return used >= 0 && used <= BUDGET;
	}

	/** The drops have started aging: the bot has been near them since the death. */
	public synchronized boolean running() {
		return active() && used > 0;
	}

	public synchronized long used() {
		return Math.max(0, used);
	}

	public synchronized long remaining() {
		return used < 0 ? 0 : Math.max(0, BUDGET - used);
	}

	/**
	 * One live, unpaused game tick with the player at (px, py, pz) in dimension pdim. Returns true
	 * exactly once, on the tick the drops expire (the caller then forgets the spot).
	 */
	public synchronized boolean tick(String pdim, double px, double py, double pz) {
		if (used < 0 || used > BUDGET) return false;
		if (pdim == null || !pdim.equals(dim)) return false;
		double dx = px - x, dy = py - y, dz = pz - z;
		if (dx * dx + dy * dy + dz * dz > LOADED_RADIUS * LOADED_RADIUS) return false;
		used++;
		return used > BUDGET;
	}
}
