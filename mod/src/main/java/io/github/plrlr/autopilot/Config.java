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

			# Opus picks the goals; a free brain picks the actions toward each goal.
			# Action brain: auto (default: the first free AI below that has a key, else the rules),
			# mock (rules only), groq, cerebras, gemini, or opus (changeable in the K panel).
			TACTICIAN=auto
			# Opus picks the big goals (strategist). Set to false to use simple rules for goals too.
			OPUS_STRATEGIST=true

			# Opus runs through `claude -p` on your Claude plan (no API key). Calls count toward plan limits.
			CLAUDE_CMD=claude
			OPUS_MODEL=opus
			OPUS_MAX_CALLS_PER_HOUR=30
			OPUS_TACTICIAN_MAX_CALLS_PER_HOUR=120
			OPUS_TIMEOUT_S=90

			# Free AI keys for the quick action decisions. Each needs a free account; paste the key after =.
			# Groq: https://console.groq.com/keys  (free plan: 30 requests/min, 1,000/day, 8,000 tokens/min)
			GROQ_API_KEY=
			GROQ_MODEL=openai/gpt-oss-20b
			GROQ_MAX_RPM=30
			GROQ_MAX_TPM=8000
			GROQ_MAX_PER_DAY=1000

			# Cerebras: https://cloud.cerebras.ai  (free trial: 5 requests/min, 1M tokens/day)
			CEREBRAS_API_KEY=
			CEREBRAS_MODEL=gpt-oss-120b
			CEREBRAS_MAX_RPM=5
			CEREBRAS_MAX_TPM=30000
			CEREBRAS_MAX_PER_DAY=2000

			# Gemini (Google AI Studio): https://aistudio.google.com/apikey  (free tier: Flash and Flash-Lite)
			GEMINI_API_KEY=
			GEMINI_MODEL=gemini-3.5-flash-lite
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
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			c.parse(lines);
			c.addMissingKeys(lines);
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
			String key = line.substring(0, eq).strip();
			// A blank setting means "use the default" (keys default to blank anyway).
			if (v.isEmpty() && values.containsKey(key)) continue;
			values.put(key, v);
		}
	}

	/**
	 * Settings added in newer versions are appended to the user's file (with their comments),
	 * so they can see and fill them in. Existing lines are never changed.
	 */
	private void addMissingKeys(List<String> existing) throws IOException {
		java.util.Set<String> have = new java.util.HashSet<>();
		for (String raw : existing) {
			String l = raw.strip();
			int eq = l.indexOf('=');
			if (!l.startsWith("#") && eq > 0) have.add(l.substring(0, eq).strip());
		}
		StringBuilder add = new StringBuilder();
		StringBuilder comments = new StringBuilder();
		for (String raw : DEFAULTS.lines().toList()) {
			String l = raw.strip();
			if (l.startsWith("#")) comments.append(l).append(System.lineSeparator());
			else if (l.isEmpty()) comments.setLength(0);
			else {
				int eq = l.indexOf('=');
				if (eq > 0 && !have.contains(l.substring(0, eq).strip())) {
					add.append(comments).append(l).append(System.lineSeparator());
				}
				comments.setLength(0);
			}
		}
		if (add.isEmpty()) return;
		Files.writeString(file, System.lineSeparator() + "# Added by a newer MC Autopilot version:" + System.lineSeparator() + add,
				StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
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
