package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.AutopilotMod;
import io.github.plrlr.autopilot.EvolvedGenes;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.skills.Skill;
import io.github.plrlr.autopilot.skills.Skills;
import io.github.plrlr.autopilot.state.WorldMemory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Append-only menu for skills written by the loop; their genes default to off. */
public final class EvolvedSkills {
	private EvolvedSkills() {}

	public record Entry(String gene, String skill, Supplier<Skill> make, Offer offer, String help) {}

	private static final List<Entry> ALL = new ArrayList<>();
	private static final Map<String, Failure> FAILURES = new HashMap<>();
	private static final Set<String> REPORTED = new HashSet<>();
	private static String lastSkill, lastArg, lastCode, lastDetail;
	private static double lastEndSeconds;

	static {
		// evolve:entries (scripts/loop/evolve.py appends one add(...) line per evolved skill here)
		add(new Entry("evolved.surface_swim", "surface_swim", SurfaceSwim::new, SurfaceSwim::offer, "After shore fails in water: swim by hand on the surface toward seen land, switching targets on stalls"));
		// evolve:end
	}

	private static void add(Entry e) {
		try {
			if (e == null || !EvolvedGenes.validName(e.gene()) || !e.gene().substring(8).equals(e.skill())
					|| e.make() == null || e.offer() == null) {
				AutopilotMod.LOGGER.warn("Skipping invalid evolved skill entry");
				return;
			}
			Tune.Gene gene = Tune.genes().get(e.gene());
			if (gene == null || gene.kind() != Tune.Kind.BOOL || isEvolved(e.skill())) {
				AutopilotMod.LOGGER.warn("Skipping evolved skill {}: missing BOOL gene or duplicate skill", e.skill());
				return;
			}
			ALL.add(e);
		} catch (RuntimeException ex) {
			AutopilotMod.LOGGER.warn("Skipping evolved skill entry", ex);
		}
	}

	public static void register(Map<String, Skills.Entry> menu) {
		for (Entry e : ALL) menu.putIfAbsent(e.skill(), new Skills.Entry(e.make(), e.help()));
	}

	/** No context or player query is needed while all evolved genes are off. */
	public static List<Option> offers(Option main, List<Option> options, WorldMemory memory) {
		boolean enabled = false;
		for (Entry e : ALL) if (Tune.on(e.gene())) { enabled = true; break; }
		if (!enabled) return List.of();
		double now = Player.gameSeconds();
		Context c = new Context(main, options, memory, lastSkill, lastArg, lastCode, lastDetail, lastEndSeconds);
		List<Option> out = new ArrayList<>(2);
		for (Entry e : ALL) {
			if (!Tune.on(e.gene())) continue;
			Failure f = FAILURES.get(e.skill());
			if (f != null && now < f.until) continue;
			Option o;
			try {
				o = e.offer().offer(c);
			} catch (RuntimeException | LinkageError | StackOverflowError ex) {
				// LinkageError: the skill's class failed to load (a throwing static initializer is an
				// ExceptionInInitializerError, not a RuntimeException). Either way: no offer, play on.
				if (REPORTED.add(e.skill())) AutopilotMod.LOGGER.warn("Evolved offer {} failed", e.skill(), ex);
				continue;
			}
			if (o == null || !e.skill().equals(o.skill())) continue;
			out.add(o);
			if (out.size() == 2) break;
		}
		return out;
	}

	public static void onSkillEnd(String skill, String arg, Skill.Result r) {
		lastSkill = skill;
		lastArg = arg;
		lastCode = r.ok() || r.code() == null ? null : r.code().name();
		lastDetail = r.detail();
		lastEndSeconds = Player.gameSeconds();
		if (!isEvolved(skill)) return;
		if (r.ok()) FAILURES.remove(skill);
		else if (r.code() != Fail.INTERRUPTED) {
			Failure f = FAILURES.computeIfAbsent(skill, k -> new Failure());
			if (++f.streak >= 3) {
				f.streak = 0;
				f.until = lastEndSeconds + 60;
			}
		}
	}

	public static boolean isEvolved(String skill) {
		for (Entry e : ALL) if (e.skill().equals(skill)) return true;
		return false;
	}

	private static final class Failure {
		int streak;
		double until;
	}
}
