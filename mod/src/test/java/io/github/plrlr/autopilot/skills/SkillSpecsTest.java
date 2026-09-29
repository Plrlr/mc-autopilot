package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Tune;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The route library must stay consistent with the skill menu and the genes, or routes silently vanish. */
class SkillSpecsTest {
	@Test
	void everySpecNamesARealSkill() {
		for (SkillSpec s : SkillSpecs.ALL)
			assertTrue(Skills.MENU.containsKey(s.skill()), s.key() + ": no skill named " + s.skill() + " in Skills.MENU");
	}

	@Test
	void everyGeneExists() {
		for (SkillSpec s : SkillSpecs.ALL)
			if (s.gene() != null) assertTrue(Tune.genes().containsKey(s.gene()), s.key() + ": unknown gene " + s.gene());
	}

	@Test
	void everySpecDoesSomething() {
		for (SkillSpec s : SkillSpecs.ALL)
			assertFalse(s.gives().isEmpty() && s.makes().isEmpty(), s.key() + " gives no item and makes no fact");
	}

	@Test
	void specsWithTheSameKeyAndArgAreNotDuplicated() {
		Set<String> seen = new HashSet<>();
		for (SkillSpec s : SkillSpecs.ALL) assertTrue(seen.add(s.skill() + " " + s.arg()), "duplicate spec " + s.skill() + " " + s.arg());
	}

	@Test
	void everySkillHasAGeneAMenuEntryAndASpec() {
		var skillGenes = Tune.genes().keySet().stream().filter(n -> n.startsWith("skill.")).toList();
		assertEquals(44, skillGenes.size(), "docs/skills-40.md: 40 skill genes, plus wave 6 (fluids): 44");
		for (String g : skillGenes) {
			String name = g.substring("skill.".length());
			assertTrue(Skills.MENU.containsKey(name), g + ": no skill named " + name);
			assertTrue(SkillSpecs.ALL.stream().anyMatch(s -> g.equals(s.gene())), g + ": no route spec");
		}
	}

	@Test
	void keysMatchWhatTheBrainLogs() {
		assertEquals("fortress:blazes", SkillSpec.keyOf("fortress", "blazes:7"));
		assertEquals("explore:cow", SkillSpec.keyOf("explore", "cow,pig"));
		assertEquals("build_portal", SkillSpec.keyOf("build_portal", null));
		assertEquals("barter", SkillSpec.keyOf("barter", ""));
	}

	@Test
	void routesExistForEveryLateGameStep() {
		for (String fact : new String[]{"portal_known", "in_nether", "fortress_known", "in_stronghold", "frame_known", "end_portal_open", "in_end", "dragon_dead"})
			assertFalse(SkillSpecs.making(fact).isEmpty(), "no route makes " + fact);
		for (String item : new String[]{"blaze_rod", "ender_pearl", "obsidian"})
			assertFalse(SkillSpecs.giving(item).isEmpty(), "no route gives " + item);
	}
}
