package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalYLevel;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.TechTree;
import net.minecraft.world.level.block.Block;

/**
 * collect item:n. Baritone mines the source blocks (legit mode: only blocks it has seen, or
 * branch mining at a sensible depth) and picks up the drops. We count the item ourselves.
 */
public final class CollectSkill extends Skill {
	private String item;
	private int want;
	private int before;
	private int lastCount;
	private int lastProgressTick;
	private int restarts;
	private Block[] blocks;
	/** Digging down to the ore's depth first (ticks spent, or -1 when not descending). */
	private int descending = -1;
	private Integer mineY;

	@Override
	public String name() {
		return "collect";
	}

	@Override
	protected void start() {
		item = argName();
		want = argCount(1);
		TechTree.Source src = TechTree.MINE.get(item);
		if (src == null) {
			fail("don't know where " + item + " comes from");
			return;
		}
		if (Items2.bestTier("pickaxe") < src.tier()) {
			fail("need a " + TechTree.pickaxeForTier(src.tier()) + " to mine " + item);
			return;
		}
		blocks = Bari.blocks(src.blocks());
		if (blocks.length == 0) {
			fail("no blocks for " + item);
			return;
		}
		before = Mc.count(item);
		lastCount = before;
		// Deep ores take a while to find by branch mining.
		timeoutTicks = 20 * (src.mineY() != null && src.mineY() < 0 ? 900 : 360);
		// Ores have a mining depth; everything else is a surface block.
		Bari.setLegitMine(src.mineY() != null);
		Bari.setMineY(src.mineY());
		mineY = src.mineY();
		// Baritone's legit branch mining doesn't go down on its own: from the surface it wandered
		// 200+ blocks at y 60 looking for iron. Staircase down to the ore's depth first, unless an
		// ore of this kind is already in view.
		if (mineY != null && Mc.player().getBlockY() > mineY + 8 && !oreInView(src)) {
			descending = 0;
			timeoutTicks += 20 * 120;
			Bari.path(new GoalYLevel(mineY));
			return;
		}
		Bari.get().getMineProcess().mine(blocks);
	}

	private boolean oreInView(TechTree.Source src) {
		for (String b : src.blocks()) {
			String group = io.github.plrlr.autopilot.state.WorldMemory.groupOf(b);
			var seen = group == null ? null : memory.nearest(group);
			if (seen != null && seen.pos().distSqr(Mc.player().blockPosition()) < 24 * 24) return true;
		}
		return false;
	}

	@Override
	protected void tick() {
		if (descending >= 0) {
			descending++;
			int y = Mc.player().getBlockY();
			boolean arrived = Math.abs(y - mineY) <= 3;
			if (arrived || (descending > 20 && !Bari.pathing()) || descending > 20 * 120) {
				// Down (or as far as the way allowed): branch-mine from here.
				descending = -1;
				lastProgressTick = ticks;
				Bari.stop();
				Bari.get().getMineProcess().mine(blocks);
			}
			return;
		}
		int now = Mc.count(item);
		if (now - before >= want) {
			done("collected " + (now - before) + " " + item);
			return;
		}
		if (now != lastCount) {
			lastCount = now;
			lastProgressTick = ticks;
		}
		// Ores can take a while to find by branch mining; surface blocks shouldn't.
		int patience = Bari.get().getMineProcess() != null && TechTree.MINE.get(item).mineY() != null ? 150 : 60;
		if (ticks - lastProgressTick > 20 * patience) {
			fail("no progress for " + patience + " s (got " + (now - before) + "/" + want + ")");
			return;
		}
		if (ticks > 20 && !Bari.get().getMineProcess().isActive()) {
			// Baritone gives up when it knows of no such block; try again a couple of times, then report.
			if (restarts++ < 2) Bari.get().getMineProcess().mine(blocks);
			else fail((now - before) > 0 ? "found only " + (now - before) + " " + item : "can't find any " + item + " nearby");
		}
	}
}
