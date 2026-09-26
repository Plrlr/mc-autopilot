package io.github.plrlr.autopilot.brains;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeCliTest {
	@Test
	void plainArgsStayUnquoted() {
		assertEquals("--model", ClaudeCli.winQuote("--model"));
		assertEquals("opus", ClaudeCli.winQuote("opus"));
	}

	@Test
	void emptyArgBecomesEmptyQuotes() {
		assertEquals("\"\"", ClaudeCli.winQuote(""));
	}

	@Test
	void spacesAndQuotesAreEscaped() {
		assertEquals("\"a b\"", ClaudeCli.winQuote("a b"));
		assertEquals("\"{\\\"type\\\":\\\"object\\\"}\"", ClaudeCli.winQuote("{\"type\":\"object\"}"));
	}

	@Test
	void backslashesBeforeQuotesAndAtEndAreDoubled() {
		// MSVC rules: n backslashes before a quote -> 2n+1 backslashes + quote; trailing -> 2n.
		assertEquals("\"a\\\\\\\"b\"", ClaudeCli.winQuote("a\\\"b"));
		assertEquals("\"c:\\dir x\\\\\"", ClaudeCli.winQuote("c:\\dir x\\"));
	}

	@Test
	void parsesStructuredOutput() {
		String out = "{\"type\":\"result\",\"is_error\":false,\"result\":\"\",\"structured_output\":{\"choice\":\"collect log:3\",\"why\":\"x\"},"
				+ "\"usage\":{\"input_tokens\":2,\"cache_read_input_tokens\":100,\"cache_creation_input_tokens\":0,\"output_tokens\":30}}";
		ClaudeCli.Reply r = ClaudeCli.parse(out, 5);
		assertTrue(r.ok());
		assertEquals("collect log:3", r.answer().get("choice").getAsString());
		assertEquals(102, r.tokensIn());
		assertEquals(30, r.tokensOut());
	}

	@Test
	void fallsBackToJsonInResultText() {
		ClaudeCli.Reply r = ClaudeCli.parse("{\"is_error\":false,\"result\":\"{\\\"goal\\\":\\\"food\\\"}\"}", 5);
		assertTrue(r.ok());
		assertEquals("food", r.answer().get("goal").getAsString());
	}

	@Test
	void reportsErrors() {
		ClaudeCli.Reply r = ClaudeCli.parse("{\"is_error\":true,\"result\":\"Claude AI usage limit reached\"}", 5);
		assertFalse(r.ok());
		assertTrue(r.error().contains("limit"));
		assertFalse(ClaudeCli.parse("", 1).ok());
		assertFalse(ClaudeCli.parse("not json", 1).ok());
	}
}
