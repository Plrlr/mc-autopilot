package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.brains.SkillStats;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.Set;

/**
 * The strategist's view of the real game: the inventory as the goal ladder counts it, the facts,
 * the genes and the learned skill stats. Facts and contexts are read once per plan (one decision),
 * so a plan sees one consistent state. Game thread only.
 */
final class GameWorld implements Strategist.World {
	private final Set<String> facts;
	private final List<String> contexts;

	GameWorld(WorldMemory memory) {
		facts = Facts.now(memory);
		LocalPlayer pl = Mc.player();
		boolean under = pl != null && !pl.level().canSeeSky(pl.blockPosition().above());
		contexts = SkillStats.contexts(Mc.dimension(), Mc.isNight(), under);
	}

	@Override
	public int have(String item) {
		return Goal.have(item);
	}

	@Override
	public Set<String> facts() {
		return facts;
	}

	@Override
	public boolean geneOn(String gene) {
		return Tune.on(gene);
	}

	@Override
	public List<String> contexts() {
		return contexts;
	}

	@Override
	public SkillStats.Estimate estimate(String key, double priorP, double priorSeconds) {
		return SkillStats.shared().estimate(key, contexts, priorP, priorSeconds);
	}

	@Override
	public double deathSeconds() {
		return Tune.get("brain.death_s");
	}
}
