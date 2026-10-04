package io.github.plrlr.autopilot.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shore phase returns its own list before the general one is built, so an evolved fallback for
 * shore must be offered there (g164's swim_out never was: zero offers in its drill and race).
 */
class ShoreOptionsTest {
	private static List<String> labels(List<Option> os) {
		return os.stream().map(Option::label).toList();
	}

	@Test
	void fallbacksComeAfterSafetyAndBeforeShore() {
		Option fight = new Option("attack", "zombie", "close");
		Option eat = new Option("eat", null, "hungry");
		Option shore = new Option("shore", null, "dry ground");
		Option swim = new Option("swim_out", null, "shore timed out");
		assertEquals(List.of("attack zombie", "eat", "swim_out", "shore"),
				labels(Planner.shoreOptions(List.of(fight, eat), shore, List.of(swim))));
	}

	@Test
	void withoutFallbacksTheListIsUnchanged() {
		Option shore = new Option("shore", null, "dry ground");
		Option flee = new Option("retreat", null, "outnumbered");
		assertEquals(List.of("retreat", "shore"), labels(Planner.shoreOptions(List.of(flee), shore, List.of())));
	}

	@Test
	void aFallbackCannotDuplicateShore() {
		Option shore = new Option("shore", null, "dry ground");
		assertEquals(List.of("shore"), labels(Planner.shoreOptions(List.of(), shore, List.of(new Option("shore", null, "again")))));
	}
}
