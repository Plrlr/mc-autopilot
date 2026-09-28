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
	private static final java.util.Set<String> SEEN_ONLY = java.util.Set.of("log", "sand", "obsidian");
	private static final int SEEN_RANGE = 64;
	private boolean seenOnly;
	/** "diamond:1:lava": mining deep to find lava, so lava in sight ends it (a laptop run dug on 45 s past a pool). */
	private boolean untilLava;
	private SeenMiner seenMiner;
	/** Waiting a few seconds for a seen block of this group before giving up (null when not). */
	private String lookGroup;
	/** Flint has its own way: one seen gravel, placed and broken until flint drops. */
	private FlintSteps flint;

	@Override
	public String name() {
		return "collect";
	}

	@Override
	protected void start() {
		item = argName();
		want = argCount(1);
		untilLava = arg != null && arg.endsWith(":lava");
		TechTree.Source src = TechTree.MINE.get(item);
		if (src == null) {
			fail(Fail.NO_RECIPE, "don't know where " + item + " comes from");
			return;
		}
		if (item.equals("flint")) {
			flint = new FlintSteps(this, memory);
			return;
		}
		if (Items2.bestTier("pickaxe") < src.tier()) {
			fail(Fail.NEED_ITEM, "need a " + TechTree.pickaxeForTier(src.tier()) + " to mine " + item);
			return;
		}
		blocks = Bari.blocks(src.blocks());
		if (blocks.length == 0) {
			fail(Fail.NEED_ITEM, "no blocks for " + item);
			return;
		}
		before = Mc.count(item);
		lastCount = before;
		// Fair play: only blocks the player has seen. Logs, sand and obsidian come only from there
		// (none seen: fail, and the planner explores for them). Ores seen in a cave wall are mined
		// first; then legit branch mining, which only digs toward ores it can actually see.
		String group = io.github.plrlr.autopilot.state.WorldMemory.groupOf(src.blocks().get(0).replace("*", "oak"));
		seenOnly = SEEN_ONLY.contains(item);
		// Stone too: it's nearly always in view, and digging down from where we stand went badly
		// in water (a laptop run dug under a lake, ran out of air, surfaced, dug again, for minutes).
		if (group != null && (seenOnly || src.mineY() != null || item.equals("stone")) && SeenMiner.anySeen(memory, group, SEEN_RANGE)) {
			seenMiner = new SeenMiner(memory, group, SEEN_RANGE);
			timeoutTicks = 20 * 360;
			return;
		}
		if (Mc.player().isInWater() && src.mineY() == null) {
			fail(Fail.WRONG_PLACE, "in water: " + item + " is dug from dry ground");
			return;
		}
		if (seenOnly) {
			// Look around a moment first: at spawn (or right after a respawn) the memory is still
			// empty for a second, and three instant "none seen" sent a laptop run exploring for 60 s.
			lookGroup = group;
			timeoutTicks = 20 * 4;
			return;
		}
		startBaritone(src);
	}

	/** Stone and dirt (always a few blocks down) and ores nobody has seen: Baritone's legit mining. */
	private void startBaritone(TechTree.Source src) {
		seenMiner = null;
		lastProgressTick = ticks;
		restarts = 0;
		// Ores have a mining depth (iron and coal: genes, so the loop can trade ore density against
		// the caves and mobs deeper down); everything else is a surface block.
		mineY = src.mineY();
		if (item.equals("raw_iron")) mineY = io.github.plrlr.autopilot.Tune.i("gather.iron_y");
		if (item.equals("coal")) mineY = io.github.plrlr.autopilot.Tune.i("gather.coal_y");
		// Deep ores take a while to find by branch mining.
		timeoutTicks = ticks + 20 * (mineY != null && mineY < 0 ? 900 : 360);
		// Stone and dirt too: legit mode mines the ones in view, else digs down to them.
		Bari.setLegitMine(true);
		Bari.setMineY(mineY != null ? mineY : Mc.player().getBlockY() - (item.equals("dirt") ? 1 : 3));
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
	protected void cleanup() {
		// Cut off as stuck on the way to a seen block: the next collect would pick the same one.
		if (seenMiner != null && result() != null && result().code() == Fail.STUCK) seenMiner.setAsideTarget();
		super.cleanup();
	}

	@Override
	public boolean workingInPlace() {
		return (flint != null && flint.inPlace()) || (seenMiner != null && seenMiner.breaking());
	}

	@Override
	protected void tick() {
		if (flint != null) {
			flint.tick();
			return;
		}
		if (untilLava && memory.nearest("lava") != null) {
			done("lava in sight");
			return;
		}
		if (lookGroup != null) {
			if (SeenMiner.anySeen(memory, lookGroup, SEEN_RANGE)) {
				seenMiner = new SeenMiner(memory, lookGroup, SEEN_RANGE);
				lookGroup = null;
				timeoutTicks = ticks + 20 * 360;
			} else if (ticks > 20 * 3) {
				fail(Fail.NOT_FOUND, "no " + item + " seen nearby");
			}
			return;
		}
		if (seenMiner != null) {
			int got = Mc.count(item) - before;
			if (got >= want) {
				done("collected " + got + " " + item + " (seen blocks)");
				return;
			}
			if (seenMiner.tick() == SeenMiner.Status.NONE_LEFT) {
				Bari.stop();
				if (!seenOnly) {
					startBaritone(TechTree.MINE.get(item));
				} else if (got > 0) {
					fail(Fail.NO_PROGRESS, "found only " + got + " " + item + " in sight");
				} else {
					fail(Fail.NOT_FOUND, "no " + item + " seen nearby");
				}
			}
			return;
		}
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
		int progress = now;
		if (progress != lastCount) {
			lastCount = progress;
			lastProgressTick = ticks;
		}
		// Ores can take a while to find by branch mining; surface blocks shouldn't.
		int patience = Bari.get().getMineProcess() != null && TechTree.MINE.get(item).mineY() != null ? 150 : 60;
		if (ticks - lastProgressTick > 20 * patience) {
			fail(Fail.NO_PROGRESS, "no progress for " + patience + " s (got " + (now - before) + "/" + want + ")");
			return;
		}
		if (ticks > 20 && !Bari.get().getMineProcess().isActive()) {
			// Baritone gives up when it knows of no such block; try again a couple of times, then report.
			if (restarts++ < 2) Bari.get().getMineProcess().mine(blocks);
			else fail((now - before) > 0 ? Fail.NO_PROGRESS : Fail.NOT_FOUND, (now - before) > 0 ? "found only " + (now - before) + " " + item : "can't find any " + item + " nearby");
		}
	}
}
