package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.plrlr.autopilot.plan.Goal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Chooses the goal to work on. Opus does it through claude -p when enabled and under its hourly
 * cap; otherwise simple rules pick the lowest unfinished rung of the ladder.
 */
public final class Strategist {
	public record Plan(Goal goal, String reason, List<String> steps, String brain, boolean valid, long ms, int tokensIn,
					   int tokensOut, String note) {}

	private final ClaudeCli cli;
	private final RateLimiter limiter;
	private volatile boolean opusEnabled;
	private volatile long pausedUntil;

	public Strategist(ClaudeCli cli, RateLimiter limiter, boolean opusEnabled) {
		this.cli = cli;
		this.limiter = limiter;
		this.opusEnabled = opusEnabled;
	}

	public boolean opusEnabled() {
		return opusEnabled;
	}

	public void setOpusEnabled(boolean on) {
		opusEnabled = on;
	}

	public RateLimiter limiter() {
		return limiter;
	}

	/** Rules: lowest unfinished rung, with night safety first. */
	public static Plan rules(Predicate<Goal> done, boolean nightDanger, String note) {
		if (nightDanger) return new Plan(Goal.SURVIVE_NIGHT, "it's night and monsters are around", List.of(), "mock", true, 0, 0, 0, note);
		for (Goal g : Goal.ladder()) {
			if (!done.test(g)) return new Plan(g, "next unfinished goal on the ladder", List.of(), "mock", true, 0, 0, 0, note);
		}
		return new Plan(Goal.KILL_DRAGON, "everything else is done", List.of(), "mock", true, 0, 0, 0, note);
	}

	public CompletableFuture<Plan> plan(JsonObject state, Goal current, Predicate<Goal> done, boolean nightDanger,
										List<String> recent) {
		if (!opusEnabled) return CompletableFuture.completedFuture(rules(done, nightDanger, "Opus strategist off"));
		String blocked = System.currentTimeMillis() < pausedUntil ? "Claude plan limit reached" : limiter.blocked(0);
		if (blocked != null) return CompletableFuture.completedFuture(rules(done, nightDanger, blocked));

		StringBuilder ladder = new StringBuilder();
		List<String> keys = new ArrayList<>();
		for (Goal g : Goal.values()) {
			keys.add(g.key());
			ladder.append("- ").append(g.key());
			if (g.milestone > 0) ladder.append(" (rung ").append(g.milestone).append(done.test(g) ? ", DONE" : "").append(')');
			ladder.append(": ").append(g.description).append('\n');
		}
		String user = "GOAL LADDER:\n" + ladder
				+ "CURRENT GOAL: " + (current == null ? "none yet" : current.key()) + '\n'
				+ "STATE: " + state + '\n'
				+ (recent.isEmpty() ? "" : "RECENT ACTIONS: " + String.join(" | ", recent) + '\n')
				+ "Answer as JSON {\"goal\": \"<goal key>\", \"reason\": \"<one sentence>\", \"steps\": [\"<short step>\", ...]}.";
		limiter.record(0);
		return cli.ask(Prompts.STRATEGIST, user, schema(keys).toString()).thenApply(r -> {
			if (!r.ok()) {
				if (r.error() != null && r.error().toLowerCase().contains("limit")) pausedUntil = System.currentTimeMillis() + 15 * 60_000;
				Plan p = rules(done, nightDanger, "Opus failed: " + r.error());
				return new Plan(p.goal(), p.reason(), p.steps(), "mock", false, r.ms(), r.tokensIn(), r.tokensOut(), p.note());
			}
			JsonObject a = r.answer();
			Goal g = a.has("goal") ? Goal.byKey(a.get("goal").getAsString()) : null;
			if (g == null) {
				Plan p = rules(done, nightDanger, "Opus named an unknown goal");
				return new Plan(p.goal(), p.reason(), p.steps(), "mock", false, r.ms(), r.tokensIn(), r.tokensOut(), p.note());
			}
			List<String> steps = new ArrayList<>();
			if (a.has("steps") && a.get("steps").isJsonArray()) {
				for (JsonElement e : a.getAsJsonArray("steps")) {
					if (steps.size() < 5 && e.isJsonPrimitive()) steps.add(e.getAsString());
				}
			}
			String reason = a.has("reason") ? a.get("reason").getAsString() : "";
			return new Plan(g, reason, steps, "opus", true, r.ms(), r.tokensIn(), r.tokensOut(), null);
		});
	}

	private static JsonObject schema(List<String> goalKeys) {
		JsonObject goal = new JsonObject();
		goal.addProperty("type", "string");
		JsonArray e = new JsonArray();
		goalKeys.forEach(e::add);
		goal.add("enum", e);
		JsonObject str = new JsonObject();
		str.addProperty("type", "string");
		JsonObject steps = new JsonObject();
		steps.addProperty("type", "array");
		steps.add("items", str);
		steps.addProperty("maxItems", 5);
		JsonObject props = new JsonObject();
		props.add("goal", goal);
		props.add("reason", str);
		props.add("steps", steps);
		JsonObject s = new JsonObject();
		s.addProperty("type", "object");
		s.add("properties", props);
		JsonArray req = new JsonArray();
		req.add("goal");
		req.add("reason");
		req.add("steps");
		s.add("required", req);
		s.addProperty("additionalProperties", false);
		return s;
	}
}
