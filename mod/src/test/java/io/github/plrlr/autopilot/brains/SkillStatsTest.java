package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonParser;
import io.github.plrlr.autopilot.plan.Option;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SkillStatsTest {
	private static SkillStats loaded(String json) {
		SkillStats s = new SkillStats();
		s.load(JsonParser.parseString(json).getAsJsonObject());
		return s;
	}

	@Test
	void noDataMeansThePrior() {
		SkillStats.Estimate e = new SkillStats().estimate("build_portal", List.of("overworld"), 0.3, 400);
		assertEquals(0.3, e.p(), 1e-9);
		assertEquals(400, e.seconds(), 1e-9);
		assertFalse(e.clearlyBad());
	}

	@Test
	void manyTriesOutweighThePrior() {
		SkillStats s = loaded("{\"build_portal\": {\"n\": 100, \"ok\": 2, \"died\": 10, \"sec\": 300}}");
		SkillStats.Estimate e = s.estimate("build_portal", List.of("overworld"), 0.5, 400);
		assertTrue(e.p() < 0.05, "p " + e.p());
		assertTrue(e.clearlyBad());
		assertTrue(e.death() > 0.08 && e.death() < 0.12, "death " + e.death());
	}

	@Test
	void aContextWithEnoughTriesOverrulesTheOverallNumbers() {
		SkillStats s = loaded("{\"collect:raw_iron\": {\"n\": 100, \"ok\": 80}, \"collect:raw_iron@under\": {\"n\": 20, \"ok\": 2},"
				+ " \"collect:raw_iron@night\": {\"n\": 3, \"ok\": 0}}");
		assertTrue(s.estimate("collect:raw_iron", List.of("overworld"), 0.5, 60).p() > 0.7);
		assertTrue(s.estimate("collect:raw_iron", List.of("overworld", "under"), 0.5, 60).p() < 0.2);
		// 3 night tries aren't enough to overrule 100.
		assertTrue(s.estimate("collect:raw_iron", List.of("overworld", "night"), 0.5, 60).p() > 0.7);
	}

	@Test
	void thisGamesTriesCountAtOnce() {
		SkillStats s = new SkillStats();
		for (int i = 0; i < 5; i++) s.record("build_portal", List.of("overworld"), false, false, 30);
		// 5 local fails count as 10 tries: the estimate drops well below the 0.5 prior at once...
		SkillStats.Estimate e = s.estimate("build_portal", List.of("overworld"), 0.5, 400);
		assertTrue(e.p() < 0.2, "p " + e.p());
		// ...but "clearly bad" (demoted) takes more: a skill isn't written off on a short bad streak.
		assertFalse(e.clearlyBad());
		for (int i = 0; i < 10; i++) s.record("build_portal", List.of("overworld"), false, false, 30);
		assertTrue(s.estimate("build_portal", List.of("overworld"), 0.5, 400).clearlyBad());
		s.clearLocal();
		assertEquals(0.5, s.estimate("build_portal", List.of("overworld"), 0.5, 400).p(), 1e-9);
	}

	@Test
	void costGrowsWhenTriesFailOrKill() {
		SkillStats.Estimate good = new SkillStats.Estimate(0.8, 0.7, 0.9, 60, 60, 0.01, 50);
		SkillStats.Estimate bad = new SkillStats.Estimate(0.2, 0.1, 0.3, 60, 60, 0.2, 50);
		assertTrue(bad.cost(600) > 5 * good.cost(600));
	}

	@Test
	void quickFailuresCostLessThanSlowOnes() {
		// The cast portal: fails in seconds ("no flat ground") most tries, casts in 420 s when it can.
		SkillStats s = new SkillStats();
		for (int i = 0; i < 9; i++) s.record("build_portal", List.of("overworld"), false, false, 5);
		s.record("build_portal", List.of("overworld"), true, false, 420);
		SkillStats.Estimate e = s.estimate("build_portal", List.of("overworld"), 0.05, 420);
		// Mean try ~ (9*5 + 420)*2 + 420*4 over 24 = ~109 s, not 420.
		assertTrue(e.spent() < 130, "spent " + e.spent());
		assertTrue(e.cost(600) < 420 / e.p(), "cost " + e.cost(600));
	}

	@Test
	void demotingKeepsEmergenciesAndNeverEmptiesTheList() {
		// Uses the shared table: a failing key recorded locally, then cleared.
		SkillStats shared = SkillStats.shared();
		shared.clearLocal();
		for (int i = 0; i < 15; i++) shared.record("explore:cow", List.of("overworld"), false, false, 60);
		List<Option> opts = List.of(new Option("explore", "cow,pig", "food"), new Option("collect", "log:3", "wood"));
		List<Option> out = Brain.demoteFailing(opts, Set.of(), List.of("overworld"));
		assertEquals("collect log:3", out.get(0).label());
		assertEquals("explore cow,pig", out.get(1).label());
		// As an emergency it keeps its place.
		assertEquals("explore cow,pig", Brain.demoteFailing(opts, Set.of("explore cow,pig"), List.of("overworld")).get(0).label());
		// Everything failing: the rules' order stands.
		List<Option> only = List.of(new Option("explore", "cow", "food"));
		assertEquals(only, Brain.demoteFailing(only, Set.of(), List.of("overworld")));
		shared.clearLocal();
	}
}
