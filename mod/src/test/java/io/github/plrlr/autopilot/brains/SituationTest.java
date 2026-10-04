package io.github.plrlr.autopilot.brains;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The moment tags (gene brain.situations). scripts/loop/test_skillstats.py checks skillstats.py
 * against the same table: the loop's memory and the game's lookup must name a moment the same way.
 */
class SituationTest {
	@Test
	void tagsMatchTheLoopsTable() {
		assertEquals(List.of(), SkillStats.situation(20, 20, 5, 99));
		assertEquals(List.of("mob"), SkillStats.situation(20, 20, 5, 6));
		assertEquals(List.of("hurt"), SkillStats.situation(8, 20, 5, 7));
		assertEquals(List.of("mob", "hurt", "hurt_mob"), SkillStats.situation(3, 20, 5, 2));
		assertEquals(List.of("nofood"), SkillStats.situation(20, 17, 0, 99));
		assertEquals(List.of(), SkillStats.situation(20, 17, 1, 99), "hungry but carrying food can still heal");
		assertEquals(List.of("nofood", "mob", "hurt", "hurt_mob"), SkillStats.situation(4, 10, 0, 3));
	}
}
