package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * secure_camp: before standing in one place for a while (smelting, casting the portal) in the
 * dark, light the ground around us so nothing spawns next to the work.
 *
 * The smelt / retreat / smelt thrash in generations 41-46 (every 4-6 s for 100+ s) was a mob
 * spawning by the furnace at night: the bot ran, came back, ran. Monsters spawn only at block
 * light 0, so torches on the dark floor within reach stop that at the source. Crafts 4 torches
 * first if it has coal and sticks but no torches.
 */
public final class SecureCamp extends Skill {
	/** Floor spots darker than this get a torch (a torch lights 14 at its block, 13 one away...). */
	static final int DARK = 7;
	private static long lastDoneMs;
	private final List<BlockPos> spots = new ArrayList<>();
	private int placed, tries;
	private CraftSkill craft;

	@Override
	public String name() {
		return "secure_camp";
	}

	@Override
	public boolean workingInPlace() {
		return true;
	}

	/** Worth doing here: dark at our feet, and not done in the last 2 minutes. */
	public static boolean worthIt() {
		return Act.blockLight(Mc.player().blockPosition()) <= DARK && System.currentTimeMillis() - lastDoneMs > 120_000
				&& (Mc.count("torch") >= 2 || Mc.count("coal") + Mc.count("charcoal") > 0 && Mc.count("stick") > 0);
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 30;
		Bari.stop();
		if (Mc.count("torch") < 2) {
			craft = new CraftSkill();
			craft.begin(memory, "torch:4");
		}
		BlockPos feet = Mc.player().blockPosition();
		// Candidate floor spots on a 3-block grid within reach, darkest first.
		for (int dx = -3; dx <= 3; dx += 3)
			for (int dz = -3; dz <= 3; dz += 3)
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos p = feet.offset(dx, dy, dz);
					if ((dx != 0 || dz != 0) && Mc.state(p).isAir() && Mc.solid(p.below())) spots.add(p);
				}
		spots.sort((a, b) -> Integer.compare(Act.blockLight(a), Act.blockLight(b)));
	}

	@Override
	protected void tick() {
		if (craft != null) {
			craft.update();
			if (craft.result() == null) return;
			craft = null;
		}
		if (ticks % 4 != 0) return;
		while (!spots.isEmpty() && Act.blockLight(spots.get(0)) > DARK) spots.remove(0);
		if (spots.isEmpty() || placed >= 6 || Mc.count("torch") == 0 || tries > 20) {
			lastDoneMs = System.currentTimeMillis();
			Facts.report("camp_safe");
			if (placed > 0 || spots.isEmpty()) done("lit the camp with " + placed + " torches");
			else fail(Mc.count("torch") == 0 ? Fail.NEED_ITEM : Fail.PLACE_FAILED, "couldn't light the camp");
			return;
		}
		tries++;
		if (Act.torch(spots.remove(0))) placed++;
	}
}
