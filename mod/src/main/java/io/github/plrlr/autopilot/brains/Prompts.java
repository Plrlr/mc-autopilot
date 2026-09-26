package io.github.plrlr.autopilot.brains;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the prompt texts from the mod jar (src/main/resources/autopilot-prompts). */
final class Prompts {
	private Prompts() {}

	static final String TACTICIAN = load("tactician.txt");
	static final String STRATEGIST = load("strategist.txt");

	private static String load(String name) {
		try (InputStream in = Prompts.class.getResourceAsStream("/autopilot-prompts/" + name)) {
			if (in == null) throw new IllegalStateException("missing prompt " + name);
			// Single line: it is passed as a command-line argument to claude.
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip().replaceAll("\\s*\\R\\s*", " ");
		} catch (IOException e) {
			throw new IllegalStateException("can't read prompt " + name, e);
		}
	}
}
