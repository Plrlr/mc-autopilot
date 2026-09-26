package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** One LLM provider that can answer a prompt with JSON matching a {choice, why} or similar schema. */
public interface LlmBackend {
	record Reply(JsonObject answer, int tokensIn, int tokensOut, long ms, String error, boolean rateLimited) {
		public boolean ok() {
			return answer != null;
		}
	}

	String name();

	/** Null if usable now, else why not (no key, limit near, ...). */
	String unavailable(int estTokens);

	/**
	 * enumValues: the allowed values for the "choice" field. The schema is built per provider,
	 * since each has its own schema dialect.
	 */
	CompletableFuture<Reply> ask(String system, String user, List<String> enumValues);
}
