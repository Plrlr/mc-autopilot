package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonObject;
import io.github.plrlr.autopilot.plan.Goal;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Skills;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Picks the next skill. The selected brain answers with one option from the list; the answer is
 * validated, retried once with a correction, and otherwise replaced by the rules' choice.
 * On rate limits it moves down the chain: selected -> other available LLMs -> rules.
 */
public final class Tactician {
	/** auto = the first free LLM that has a key (groq, cerebras, gemini), else the rules. */
	public static final List<String> NAMES = List.of("auto", "mock", "groq", "cerebras", "gemini", "opus");
	private static final List<String> FREE = List.of("groq", "cerebras", "gemini");

	private final Map<String, LlmBackend> backends;
	private volatile String selected;
	private volatile boolean toldNoKeys;

	public Tactician(Map<String, LlmBackend> backends, String initial) {
		this.backends = backends;
		// Actions default to free brains; Opus is spent on goals.
		this.selected = NAMES.contains(initial) ? initial : "auto";
	}

	public String selected() {
		return selected;
	}

	/** What will actually answer: for auto, the first free LLM with a key, else mock. */
	public String effective() {
		if (!selected.equals("auto")) return selected;
		for (String n : FREE) {
			LlmBackend b = backends.get(n);
			if (b != null && !hasNoKey(b)) return n;
		}
		return "mock";
	}

	private static boolean hasNoKey(LlmBackend b) {
		String u = b.unavailable(0);
		return u != null && u.startsWith("no ");
	}

	public void select(String name) {
		if (NAMES.contains(name)) selected = name;
	}

	public LlmBackend backend(String name) {
		return backends.get(name);
	}

	/**
	 * notify gets short messages for the user (fallbacks, limits). Everything here runs off the
	 * game thread except building the prompt, which only uses the strings passed in.
	 */
	public CompletableFuture<Decision> decide(JsonObject state, Goal goal, List<String> plan, List<String> recent,
											  List<Option> options, Consumer<String> notify) {
		Option rules = options.get(0);
		String first = effective();
		if (first.equals("mock")) {
			if (selected.equals("auto") && !toldNoKeys) {
				toldNoKeys = true;
				notify.accept("No free AI keys in mc-autopilot.env, so the rules pick actions (see START_HERE.md).");
			}
			return CompletableFuture.completedFuture(Decision.mock(rules, "rules", true));
		}
		// A single sensible option leaves nothing to decide; don't spend a call on it.
		if (options.size() == 1) return CompletableFuture.completedFuture(Decision.mock(rules, "only option", true));

		String user = userPrompt(state, goal, plan, recent, options);
		int est = (Prompts.TACTICIAN.length() + user.length()) / 4 + 80;
		List<LlmBackend> chain = new ArrayList<>();
		chain.add(backends.get(first));
		for (String n : FREE) {
			LlmBackend b = backends.get(n);
			// Fall over to other free brains only; Opus is used only when chosen.
			if (b != null && !chain.contains(b) && !hasNoKey(b)) chain.add(b);
		}
		return tryChain(chain, 0, user, est, options, notify, new StringBuilder());
	}

	private CompletableFuture<Decision> tryChain(List<LlmBackend> chain, int i, String user, int est, List<Option> options,
												 Consumer<String> notify, StringBuilder why) {
		if (i >= chain.size()) {
			if (!why.isEmpty()) notify.accept("Using rules: " + why);
			return CompletableFuture.completedFuture(Decision.mock(options.get(0), "fallback: " + why, true));
		}
		LlmBackend b = chain.get(i);
		String blocked = b.unavailable(est);
		if (blocked != null) {
			if (i == 0) why.append(blocked);
			return tryChain(chain, i + 1, user, est, options, notify, why);
		}
		if (i > 0 && !why.isEmpty()) notify.accept("Switching to " + b.name() + ": " + why);
		List<String> labels = options.stream().map(Option::label).toList();
		return b.ask(Prompts.TACTICIAN, user, labels).thenCompose(r -> {
			if (r.rateLimited() || (!r.ok() && r.answer() == null && r.error() != null && isTransient(r.error()))) {
				if (why.isEmpty()) why.append(b.name()).append(": ").append(r.error());
				return tryChain(chain, i + 1, user, est, options, notify, why);
			}
			Option pick = r.ok() ? match(r.answer(), options) : null;
			if (pick != null) {
				return CompletableFuture.completedFuture(new Decision(pick, text(r.answer(), "why"), b.name(), true, r.ms(), r.tokensIn(), r.tokensOut(), null));
			}
			// One retry with a one-line correction, then give up on this answer.
			String bad = r.ok() ? text(r.answer(), "choice") : r.error();
			String retry = user + "\nYour last answer \"" + bad + "\" was not valid. Copy one option exactly.";
			return b.ask(Prompts.TACTICIAN, retry, labels).thenApply(r2 -> {
				Option p2 = r2.ok() ? match(r2.answer(), options) : null;
				if (p2 != null) {
					return new Decision(p2, text(r2.answer(), "why"), b.name(), true, r.ms() + r2.ms(),
							r.tokensIn() + r2.tokensIn(), r.tokensOut() + r2.tokensOut(), "needed a retry");
				}
				Decision d = Decision.mock(options.get(0), b.name() + " gave an invalid answer twice", false);
				return new Decision(d.choice(), d.why(), "mock", false, r.ms() + r2.ms(), r.tokensIn() + r2.tokensIn(),
						r.tokensOut() + r2.tokensOut(), d.note());
			});
		});
	}

	private static boolean isTransient(String err) {
		String e = err.toLowerCase();
		return e.contains("timed out") || e.contains("network") || e.contains("limit") || e.contains("http 5");
	}

	private static Option match(JsonObject answer, List<Option> options) {
		String c = text(answer, "choice").strip();
		for (Option o : options) if (o.label().equals(c)) return o;
		for (Option o : options) if (o.label().equalsIgnoreCase(c)) return o;
		return null;
	}

	private static String text(JsonObject o, String k) {
		return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
	}

	static String userPrompt(JsonObject state, Goal goal, List<String> plan, List<String> recent, List<Option> options) {
		StringBuilder sb = new StringBuilder();
		sb.append("GOAL: ").append(goal.key()).append(" - ").append(goal.description).append('\n');
		if (!plan.isEmpty()) sb.append("STRATEGIST PLAN: ").append(String.join("; ", plan)).append('\n');
		sb.append("STATE: ").append(state).append('\n');
		if (!recent.isEmpty()) sb.append("RECENT: ").append(String.join(" | ", recent)).append('\n');
		Set<String> skills = new LinkedHashSet<>();
		for (Option o : options) skills.add(o.skill());
		sb.append("SKILLS:");
		for (String s : skills) {
			var e = Skills.MENU.get(s);
			if (e != null) sb.append(" ").append(s).append(" = ").append(e.help()).append(';');
		}
		sb.append("\nOPTIONS (the first is what simple rules would do):\n");
		for (Option o : options) sb.append("- ").append(o.label()).append("  (").append(o.why()).append(")\n");
		sb.append("Answer as JSON {\"choice\": \"<one option exactly>\", \"why\": \"<short reason>\"}.");
		return sb.toString();
	}
}
