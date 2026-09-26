package io.github.plrlr.autopilot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings from %APPDATA%\.minecraft\config\mc-autopilot.env (KEY=value lines).
 * The file lives in the Minecraft folder, not the repo, so API keys can't be committed.
 */
public final class Config {
	private static final String DEFAULTS = """
			# MC Autopilot settings. Lines are KEY=value. Restart Minecraft after editing.

			# Tactician brain at startup: opus, mock, groq, or gemini (changeable in the K panel)
			TACTICIAN=opus
			# Opus also picks the big goals (strategist). Set to false to use simple rules instead.
			OPUS_STRATEGIST=true

			# Opus runs through `claude -p` on your Claude plan (no API key). Calls count toward plan limits.
			CLAUDE_CMD=claude
			OPUS_MODEL=opus
			OPUS_MAX_CALLS_PER_HOUR=30
			OPUS_TACTICIAN_MAX_CALLS_PER_HOUR=120
			OPUS_TIMEOUT_S=90

			# Groq (free). A model from Groq's free list that supports strict JSON schema.
			GROQ_API_KEY=
			GROQ_MODEL=openai/gpt-oss-20b
			GROQ_MAX_RPM=20
			GROQ_MAX_TPM=6000
			GROQ_MAX_PER_DAY=900

			# Gemini (free). Use a Flash or Flash-Lite model your AI Studio free tier lists.
			GEMINI_API_KEY=
			GEMINI_MODEL=
			GEMINI_MAX_RPM=5
			GEMINI_MAX_TPM=100000
			GEMINI_MAX_PER_DAY=900
			""";

	private final Map<String, String> values = new LinkedHashMap<>();
	public final Path file;

	private Config(Path file) {
		this.file = file;
	}

	public static Config load(Path gameDir) {
		Path file = gameDir.resolve("config").resolve("mc-autopilot.env");
		Config c = new Config(file);
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, DEFAULTS, StandardCharsets.UTF_8);
			}
			c.parse(DEFAULTS.lines().toList());
			c.parse(Files.readAllLines(file, StandardCharsets.UTF_8));
		} catch (IOException e) {
			AutopilotMod.LOGGER.warn("Could not read {}, using defaults: {}", file, e.toString());
			c.parse(DEFAULTS.lines().toList());
		}
		return c;
	}

	private void parse(List<String> lines) {
		for (String raw : lines) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) continue;
			int eq = line.indexOf('=');
			if (eq <= 0) continue;
			String v = line.substring(eq + 1).strip();
			// Allow KEY="value" so people can paste quoted keys.
			if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
			values.put(line.substring(0, eq).strip(), v);
		}
	}

	public String str(String key) {
		return values.getOrDefault(key, "");
	}

	public int integer(String key, int fallback) {
		try {
			return Integer.parseInt(str(key));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public boolean bool(String key, boolean fallback) {
		String v = str(key).toLowerCase();
		if (v.equals("true")) return true;
		if (v.equals("false")) return false;
		return fallback;
	}
}
