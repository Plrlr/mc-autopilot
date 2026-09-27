package io.github.plrlr.autopilot.plan;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The option list is ordered by phase: urgent survival first, then getting dropped items back,
 * getting back to the surface, cheap upkeep, the goal's own step, and finally extras. The first
 * option is what the rules do, so the order is the behaviour.
 *
 * Planner.options() reads all of its state through the static Mc helper, so it cannot run in a
 * unit test. The ordering rules are checked here through two pure hooks requested in the Freebuff
 * section of docs/coordination.md. They are looked up by reflection and the tests are skipped
 * until the hooks land, so this file compiles and the build stays green in the meantime.
 */
class PlannerPriorityTest {
	private static final String PENDING = "Planner priority hook not merged yet (docs/coordination.md, Freebuff)";

	/** The phase-ordering hook: the assembly currently inline at the top of options(). */
	private static Method orderHook() {
		try {
			return Planner.class.getMethod("order", List.class, Option.class, Option.class,
					List.class, Option.class, List.class);
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	/** The batch-9 food hook: whether the far search for animals is worth it at this hunger. */
	private static Method foodHook() {
		try {
			return Planner.class.getMethod("foodSearchWorthIt", int.class);
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Option> call(Method m, List<Option> urgent, Option recover, Option surface,
	                                 List<Option> upkeep, Option main, List<Option> extras) throws Exception {
		return (List<Option>) m.invoke(null, urgent, recover, surface, upkeep, main, extras);
	}

	private static int indexOf(List<Option> options, String label) {
		for (int i = 0; i < options.size(); i++) if (options.get(i).label().equals(label)) return i;
		return -1;
	}

	@Test
	void urgentBeatsUpkeep() throws Exception {
		Method m = orderHook();
		Assumptions.assumeTrue(m != null, PENDING);
		List<Option> out = call(m,
				List.of(new Option("attack", "zombie", "a monster is close")),
				null, null,
				List.of(new Option("eat", null, "hungry")),
				new Option("build_portal", null, "the goal's step"),
				List.of(new Option("explore", "any", "new ground")));
		assertTrue(indexOf(out, "attack zombie") < indexOf(out, "eat"),
				"survival must be offered before upkeep");
	}

	@Test
	void upkeepBeatsTheGoalStep() throws Exception {
		Method m = orderHook();
		Assumptions.assumeTrue(m != null, PENDING);
		List<Option> out = call(m,
				List.of(), null, null,
				List.of(new Option("smelt", "cooked_beef:3", "cook the raw beef")),
				new Option("build_portal", null, "the goal's step"),
				List.of());
		assertTrue(indexOf(out, "smelt cooked_beef:3") < indexOf(out, "build_portal"),
				"cheap upkeep must be offered before the goal's own step");
	}

	@Test
	void goalStepBeatsExtras() throws Exception {
		Method m = orderHook();
		Assumptions.assumeTrue(m != null, PENDING);
		List<Option> out = call(m,
				List.of(), null, null, List.of(),
				new Option("build_portal", null, "the goal's step"),
				List.of(new Option("explore", "any", "new ground")));
		assertTrue(indexOf(out, "build_portal") < indexOf(out, "explore any"),
				"the goal's own step must be offered before fallbacks");
	}

	@Test
	void duplicateLabelsKeepTheirFirstPhase() throws Exception {
		Method m = orderHook();
		Assumptions.assumeTrue(m != null, PENDING);
		// Same label in upkeep and in the goal step: the earlier phase wins, so the tactician sees
		// it once, in the urgent/upkeep part of the list.
		Option eat = new Option("eat", null, "hungry");
		List<Option> out = call(m,
				List.of(), null, null,
				List.of(eat),
				new Option("eat", null, "the goal's step"),
				List.of());
		assertEquals(1, out.size(), "the same label must not appear twice");
		assertEquals("eat", out.get(0).label());
	}

	@Test
	void listIsCappedAtTen() throws Exception {
		Method m = orderHook();
		Assumptions.assumeTrue(m != null, PENDING);
		List<Option> urgent = new ArrayList<>();
		for (int i = 0; i < 12; i++) urgent.add(new Option("attack", "mob" + i, "monster " + i));
		List<Option> out = call(m, urgent, null, null, List.of(),
				new Option("build_portal", null, "the goal's step"),
				List.of(new Option("explore", "any", "new ground")));
		assertEquals(10, out.size(), "the menu is capped at 10 options");
		assertEquals("attack mob0", out.get(0).label(), "the cap keeps the front of the list");
	}

	@Test
	void foodSearchIsOnlyWorthItAtHungerEightOrLess() throws Exception {
		Method m = foodHook();
		Assumptions.assumeTrue(m != null, PENDING);
		assertTrue((Boolean) m.invoke(null, 8), "at hunger 8 the search is still worth it");
		assertFalse((Boolean) m.invoke(null, 9), "batch 9: at hunger 9 the search beat a ready portal");
		assertFalse((Boolean) m.invoke(null, 14));
		assertFalse((Boolean) m.invoke(null, 20));
	}

	@Test
	void readyPortalLeadsWhenTheAnimalSearchIsSuppressed() throws Exception {
		Method order = orderHook();
		Method food = foodHook();
		Assumptions.assumeTrue(order != null && food != null, PENDING);
		// The upkeep guard is foodSearchWorthIt(hunger): at hunger 9 the explore-for-animals
		// option is not built at all, so the ready portal is the first thing offered.
		int hunger = 9;
		List<Option> upkeep = new ArrayList<>();
		Option search = new Option("explore", Planner.ANIMALS, "hungry: look for animals");
		if ((Boolean) food.invoke(null, hunger)) upkeep.add(search);
		List<Option> out = call(order, List.of(), null, null, upkeep,
				new Option("build_portal", null, "cast the nether portal"),
				List.of(new Option("explore", "any", "new ground")));
		assertEquals(0, indexOf(out, "build_portal"),
				"a ready portal must lead the list at hunger 9");
	}
}
