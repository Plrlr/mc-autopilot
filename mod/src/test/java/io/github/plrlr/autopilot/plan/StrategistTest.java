package io.github.plrlr.autopilot.plan;

import io.github.plrlr.autopilot.brains.SkillStats;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The strategist's reasoning on fake worlds: which route it takes, and why it changes its mind. */
class StrategistTest {
	/** A world of item counts, facts and learned stats: {key: {tries, successes}}. */
	static final class Fake implements Strategist.World {
		final Map<String, Integer> inv = new HashMap<>();
		final Set<String> facts = new HashSet<>(Set.of("overworld"));
		final Set<String> genes = new HashSet<>();
		final Map<String, double[]> stats = new HashMap<>();

		Fake give(Object... kv) {
			for (int i = 0; i < kv.length; i += 2) inv.put((String) kv[i], (Integer) kv[i + 1]);
			return this;
		}

		Fake know(String... f) {
			facts.addAll(List.of(f));
			return this;
		}

		@Override
		public int have(String item) {
			return inv.getOrDefault(item, 0);
		}

		@Override
		public Set<String> facts() {
			return facts;
		}

		@Override
		public boolean geneOn(String gene) {
			return genes.contains(gene);
		}

		@Override
		public List<String> contexts() {
			return List.of("overworld");
		}

		@Override
		public SkillStats.Estimate estimate(String key, double priorP, double priorSeconds) {
			double[] s = stats.get(key);
			double n = s == null ? 0 : s[0], ok = s == null ? 0 : s[1];
			double a = priorP * 4 + ok, b = (1 - priorP) * 4 + n - ok, p = a / (a + b);
			return new SkillStats.Estimate(p, p, p, priorSeconds, priorSeconds, 0.02, n);
		}

		@Override
		public double deathSeconds() {
			return 600;
		}
	}

	private static Strategist.Step toward(Fake w, String fact) {
		return new Strategist(w, (item, n) -> new Option("plain", item + ":" + n, "test")).towardFact(fact);
	}

	/** The speedrun kit at a known lava pool, and a cast that has worked half the time. */
	private static Fake castKit() {
		Fake w = new Fake().give("bucket", 2, "water_bucket", 1, "flint_and_steel", 1, "throwaway", 64, "iron_pickaxe", 1).know("lava_pool", "water_known");
		w.stats.put("build_portal", new double[]{10, 5});
		return w;
	}

	@Test
	void withTheKitAtALavaPoolItCasts() {
		Strategist.Step s = toward(castKit(), "in_nether");
		assertEquals("build_portal", s.option().label());
		assertTrue(s.plan().startsWith("in_nether via enter_portal:nether"), s.plan());
	}

	@Test
	void tenObsidianInTheBagBeatsCasting() {
		Strategist.Step s = toward(castKit().give("obsidian", 10), "in_nether");
		assertEquals("build_portal placed", s.option().label());
	}

	@Test
	void aCastThatKeepsFailingIsDroppedForDiamonds() {
		Fake w = castKit();
		assertEquals("build_portal", toward(w, "in_nether").option().label());
		// 80 casts, 1 success: the learned stats now say casting doesn't work here. Diamonds
		// (a diamond pickaxe, then harden and mine 10 obsidian) become the cheaper way in.
		w.stats.put("build_portal", new double[]{80, 1});
		Strategist.Step s = toward(w, "in_nether");
		assertEquals("plain diamond_pickaxe:1", s.option().label(), s.plan());
	}

	@Test
	void withADiamondPickaxeHardeningThePoolBeatsAShakyCast() {
		Strategist.Step s = toward(castKit().give("diamond_pickaxe", 1), "in_nether");
		assertEquals("make_obsidian obsidian", s.option().label(), s.plan());
	}

	@Test
	void missingWaterBucketGetsFilledFirst() {
		Fake w = castKit();
		w.inv.put("water_bucket", 0);
		assertEquals("fill_bucket water", toward(w, "in_nether").option().label());
		w.facts.remove("water_known");
		assertEquals("explore water", toward(w, "in_nether").option().label());
	}

	@Test
	void noLavaSeenMeansLookForIt() {
		Fake w = castKit();
		w.facts.remove("lava_pool");
		assertEquals("explore lava", toward(w, "in_nether").option().label());
	}

	@Test
	void plainItemWorkGoesToThePlanner() {
		Fake w = castKit();
		w.inv.put("flint_and_steel", 0);
		// Flint and steel is plain crafting: the planner's itemStep makes it.
		assertEquals("plain flint_and_steel:1", toward(w, "in_nether").option().label());
	}

	@Test
	void aRouteBehindAnOffGeneIsNeverTaken() {
		Fake w = new Fake();
		Strategist st = new Strategist(w, (i, n) -> null);
		var spec = io.github.plrlr.autopilot.skills.SkillSpec.of("explore", "test").makes("x").gene("skill.test").build();
		assertEquals(Strategist.INF, st.routeCost(spec, 1, 0));
		w.genes.add("skill.test");
		assertTrue(st.routeCost(spec, 1, 0) < Strategist.INF);
	}

	@Test
	void blazeRodsNeedAFortressFirst() {
		Fake w = new Fake().know("in_nether");
		assertEquals("fortress find", toward(w, "fortress_known").option().label());
		Strategist.Step s = new Strategist(w, (i, n) -> new Option("plain", i, "t")).towardItem("blaze_rod", 7);
		assertEquals("fortress find", s.option().label(), s.plan());
	}

	@Test
	void obsidianIsOnlyMinedWhereItWasSeen() {
		// A diamond pickaxe but no obsidian seen: harden a lava pool first.
		Fake w = new Fake().give("diamond_pickaxe", 1, "water_bucket", 1).know("lava_pool");
		Strategist.Step s = new Strategist(w, (i, n) -> new Option("plain", i, "t")).towardItem("obsidian", 10);
		assertEquals("make_obsidian obsidian", s.option().label(), s.plan());
		w.know("obsidian_known");
		// Seen: plain mining, which the planner's itemStep does (collect obsidian).
		assertEquals("plain obsidian", new Strategist(w, (i, n) -> new Option("plain", i, "t")).towardItem("obsidian", 10).option().label());
	}

	@Test
	void factsAlreadyTrueNeedNoStep() {
		assertNull(toward(new Fake().know("in_nether"), "in_nether"));
	}

	@Test
	void theWholeRouteToTheDragonHasACost() {
		// From a lit portal in the overworld, every later step has a route (with priors only).
		Fake w = castKit().know("portal_known");
		assertNotNull(toward(w, "dragon_dead"));
	}
}
