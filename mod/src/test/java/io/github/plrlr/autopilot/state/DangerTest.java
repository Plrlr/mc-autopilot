package io.github.plrlr.autopilot.state;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DangerTest {
	private static Danger.Input input(double hp, boolean shield, boolean fire, boolean wall,
			List<Danger.Mob> mobs, List<Danger.Step> steps) {
		return new Danger.Input(hp, 0, 12, true, shield, fire, false, fire, false, wall, mobs, steps);
	}

	private static Danger.Verdict assess(Danger.Input in) {
		return Danger.assess(in, 3, 7, 8, 10);
	}

	@Test
	void closeMeleeMobMustBeFacedEvenAtLowHealth() {
		var zombie = new Danger.Mob("zombie", 2.5, 2.5, 0, true);
		assertEquals(Danger.Kind.FIGHT, assess(input(4, false, false, false, List.of(zombie),
				List.of(new Danger.Step(-1, 0, true, false, false)))).kind());
	}

	@Test
	void retreatRejectsLedgeAndSecondCreeper() {
		var first = new Danger.Mob("creeper", 4, 4, 0, true);
		var second = new Danger.Mob("creeper", 6, -5, 0, true);
		var verdict = assess(input(12, false, false, false, List.of(first, second), List.of(
				new Danger.Step(-1, 0, false, false, false),
				new Danger.Step(1, 0, true, false, false),
				new Danger.Step(0, 1, true, false, false))));
		assertEquals(Danger.Kind.RETREAT, verdict.kind());
		assertEquals(0, verdict.dx());
		assertEquals(1, verdict.dz());
		assertEquals(Danger.Kind.AVOID_HAZARD,
				assess(input(12, false, false, false, List.of(first),
						List.of(new Danger.Step(-1, 0, false, false, false)))).kind());
	}

	@Test
	void withAShieldOrUpCloseASkeletonIsFoughtNotHiddenFrom() {
		// Issue #16: cover-hopping stalled the drill's skeleton rounds. Shield up or close: fight.
		var steps = List.of(new Danger.Step(0, 1, true, false, true));
		var far = new Danger.Mob("skeleton", 10, 10, 0, true);
		assertEquals(Danger.Kind.FIGHT, assess(input(12, true, false, true, List.of(far), steps)).kind());
		var close = new Danger.Mob("skeleton", 5, 5, 0, true);
		assertEquals(Danger.Kind.FIGHT, assess(input(12, false, false, true, List.of(close), steps)).kind());
	}

	@Test
	void skeletonUsesCoverInsteadOfOpenRetreat() {
		var skeleton = new Danger.Mob("skeleton", 10, 10, 0, true);
		var verdict = assess(input(6, false, false, true, List.of(skeleton), List.of(
				new Danger.Step(-1, 0, true, false, false),
				new Danger.Step(0, 1, true, false, true))));
		assertEquals(Danger.Kind.RETREAT, verdict.kind());
		assertEquals(1, verdict.dz());
	}

	@Test
	void firePrefersSafeWaterAndStopsIfNoSafeExit() {
		var water = new Danger.Step(0, 1, true, true, false);
		var dry = new Danger.Step(1, 0, true, false, false);
		assertEquals(1, assess(input(10, false, true, false, List.of(), List.of(dry, water))).dz());
		assertEquals(Danger.Kind.AVOID_HAZARD,
				assess(input(10, false, true, false, List.of(), List.of())).kind());
	}
}
