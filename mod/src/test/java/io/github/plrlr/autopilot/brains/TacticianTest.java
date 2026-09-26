package io.github.plrlr.autopilot.brains;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TacticianTest {
	/** A backend that only reports whether it has a key. */
	private record Fake(String name, boolean hasKey) implements LlmBackend {
		@Override
		public String unavailable(int estTokens) {
			return hasKey ? null : "no KEY in mc-autopilot.env";
		}

		@Override
		public CompletableFuture<Reply> ask(String system, String user, List<String> enumValues) {
			throw new UnsupportedOperationException();
		}
	}

	private static Tactician with(boolean groq, boolean cerebras, boolean gemini, String selected) {
		Map<String, LlmBackend> b = new LinkedHashMap<>();
		b.put("groq", new Fake("groq", groq));
		b.put("cerebras", new Fake("cerebras", cerebras));
		b.put("gemini", new Fake("gemini", gemini));
		return new Tactician(b, selected);
	}

	@Test
	void autoUsesFirstFreeBrainWithAKey() {
		assertEquals("groq", with(true, true, true, "auto").effective());
		assertEquals("cerebras", with(false, true, true, "auto").effective());
		assertEquals("gemini", with(false, false, true, "auto").effective());
	}

	@Test
	void autoFallsBackToRulesWithoutKeys() {
		assertEquals("mock", with(false, false, false, "auto").effective());
	}

	@Test
	void explicitChoiceIsKept() {
		assertEquals("gemini", with(true, true, true, "gemini").effective());
		assertEquals("mock", with(true, true, true, "mock").effective());
	}

	@Test
	void unknownNameMeansAuto() {
		assertEquals("auto", with(false, false, false, "banana").selected());
	}
}
