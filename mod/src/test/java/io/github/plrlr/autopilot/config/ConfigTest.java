package io.github.plrlr.autopilot.config;

import io.github.plrlr.autopilot.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {
	@Test
	void oldFileGetsNewKeysAndKeepsUserValues(@TempDir Path game) throws Exception {
		Path f = game.resolve("config").resolve("mc-autopilot.env");
		Files.createDirectories(f.getParent());
		Files.writeString(f, "TACTICIAN=mock\nGROQ_API_KEY=abc\nGEMINI_MODEL=\n");
		Config c = Config.load(game);
		assertEquals("mock", c.str("TACTICIAN"));
		assertEquals("abc", c.str("GROQ_API_KEY"));
		// Blank means default.
		assertEquals("gemini-3.5-flash-lite", c.str("GEMINI_MODEL"));
		String text = Files.readString(f);
		assertTrue(text.contains("CEREBRAS_API_KEY="), "new keys appended");
		assertTrue(text.startsWith("TACTICIAN=mock\nGROQ_API_KEY=abc"), "user lines untouched");
		// Loading again adds nothing more.
		Config.load(game);
		assertEquals(text, Files.readString(f));
	}

	@Test
	void freshFileUsesAuto(@TempDir Path game) {
		assertEquals("auto", Config.load(game).str("TACTICIAN"));
	}
}
