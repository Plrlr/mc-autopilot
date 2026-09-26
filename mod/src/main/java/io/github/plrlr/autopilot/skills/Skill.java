package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.AutopilotMod;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.KeyMapping;

/**
 * A skill is plain code, not AI: it runs a little every game tick until it succeeds, fails or
 * times out. A fresh instance is made for every run so no state leaks between runs.
 */
public abstract class Skill {
	/** code is null when ok. */
	public record Result(boolean ok, Fail code, String detail) {}

	protected WorldMemory memory;
	protected String arg;
	protected int ticks;
	protected int timeoutTicks = 20 * 60;
	private Result result;

	/** Name as the brains see it. */
	public abstract String name();

	/**
	 * False for skills that must finish once started (sleeping, a shelter, a furnace load):
	 * routine re-checks (heartbeat, new goal) won't interrupt them; danger still can.
	 */
	public boolean interruptible() {
		return true;
	}

	protected abstract void start();

	protected abstract void tick();

	/** Undo anything held: keys, Baritone goals, open containers. Called once when the skill ends. */
	protected void cleanup() {
		Bari.stop();
		releaseKeys();
	}

	public final void begin(WorldMemory memory, String arg) {
		this.memory = memory;
		this.arg = arg;
		this.ticks = 0;
		this.result = null;
		try {
			start();
		} catch (Exception e) {
			AutopilotMod.LOGGER.warn("Skill {} failed to start", name(), e);
			fail(Fail.ERROR, "error: " + e.getClass().getSimpleName());
		}
	}

	public final void update() {
		if (result != null) return;
		if (Mc.player() == null) {
			fail(Fail.ERROR, "no player");
			return;
		}
		if (++ticks > timeoutTicks) {
			fail(Fail.TIMEOUT, "timed out after " + ticks / 20 + " s");
			return;
		}
		try {
			tick();
		} catch (Exception e) {
			AutopilotMod.LOGGER.warn("Skill {} crashed", name(), e);
			fail(Fail.ERROR, "error: " + e.getClass().getSimpleName());
		}
	}

	/** Stop from outside (user took over, a reflex, the brain switched). */
	public final void abort(Fail code, String why) {
		if (result == null) {
			result = new Result(false, code, why);
			safeCleanup();
		}
	}

	public final Result result() {
		return result;
	}

	protected final void done(String detail) {
		if (result == null) {
			result = new Result(true, null, detail);
			safeCleanup();
		}
	}

	protected final void fail(Fail code, String detail) {
		if (result == null) {
			result = new Result(false, code, detail);
			safeCleanup();
		}
	}

	private void safeCleanup() {
		try {
			cleanup();
		} catch (Exception e) {
			AutopilotMod.LOGGER.warn("Cleanup of {} failed", name(), e);
		}
	}

	public static void releaseKeys() {
		var o = Mc.mc().options;
		for (KeyMapping k : new KeyMapping[]{o.keyUse, o.keyAttack, o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint}) {
			k.setDown(false);
		}
	}

	/** Argument helpers: "iron_ingot:3" -> "iron_ingot" and 3. */
	protected String argName() {
		if (arg == null) return "";
		int c = arg.indexOf(':');
		return c < 0 ? arg : arg.substring(0, c);
	}

	protected int argCount(int fallback) {
		if (arg == null) return fallback;
		int c = arg.indexOf(':');
		if (c < 0) return fallback;
		try {
			return Math.max(1, Integer.parseInt(arg.substring(c + 1)));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
