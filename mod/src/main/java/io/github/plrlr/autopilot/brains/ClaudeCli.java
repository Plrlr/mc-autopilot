package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.plrlr.autopilot.AutopilotMod;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Asks Opus through Claude Code's headless mode (`claude -p`), on the user's Claude plan.
 * No tools, no session saved, our own short system prompt, and a JSON schema for the answer.
 * Runs in an empty folder so no CLAUDE.md or project files get loaded.
 */
public final class ClaudeCli {
	public record Reply(JsonObject answer, int tokensIn, int tokensOut, long ms, String error) {
		public boolean ok() {
			return answer != null;
		}
	}

	private final String command;
	private final String model;
	private final int timeoutS;
	private final Path cwd;

	public ClaudeCli(String command, String model, int timeoutS, Path cwd) {
		this.command = command.isBlank() ? "claude" : command;
		this.model = model.isBlank() ? "opus" : model;
		this.timeoutS = Math.max(15, timeoutS);
		this.cwd = cwd;
	}

	public CompletableFuture<Reply> ask(String system, String userText, String schemaJson) {
		return CompletableFuture.supplyAsync(() -> run(system, userText, schemaJson), Brains.POOL);
	}

	private Reply run(String system, String userText, String schemaJson) {
		long t0 = System.currentTimeMillis();
		Process p = null;
		try {
			Files.createDirectories(cwd);
			List<String> cmd = new ArrayList<>(launcher());
			cmd.addAll(List.of("-p", "Answer the request on stdin.",
					"--model", model,
					"--tools", "",
					"--no-session-persistence",
					"--output-format", "json",
					"--system-prompt", system,
					"--json-schema", schemaJson));
			List<String> quoted = new ArrayList<>();
			for (int i = 0; i < cmd.size(); i++) quoted.add(i == 0 ? cmd.get(i) : winQuote(cmd.get(i)));
			ProcessBuilder pb = new ProcessBuilder(quoted).directory(cwd.toFile());
			pb.redirectError(ProcessBuilder.Redirect.DISCARD);
			p = pb.start();
			Process proc = p;
			// Read stdout on another thread so the timeout below still works if claude hangs.
			CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> {
				try {
					return new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				} catch (IOException e) {
					return "";
				}
			}, Brains.POOL);
			try (OutputStream in = p.getOutputStream()) {
				in.write(userText.getBytes(StandardCharsets.UTF_8));
			}
			if (!p.waitFor(timeoutS, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return new Reply(null, 0, 0, System.currentTimeMillis() - t0, "timed out after " + timeoutS + " s");
			}
			String text = out.get(5, TimeUnit.SECONDS);
			return parse(text, System.currentTimeMillis() - t0);
		} catch (Exception e) {
			if (p != null) p.destroyForcibly();
			AutopilotMod.LOGGER.warn("claude -p failed", e);
			return new Reply(null, 0, 0, System.currentTimeMillis() - t0, e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static Reply parse(String text, long ms) {
		if (text == null || text.isBlank()) return new Reply(null, 0, 0, ms, "empty output from claude");
		JsonObject o;
		try {
			o = JsonParser.parseString(text.strip()).getAsJsonObject();
		} catch (Exception e) {
			return new Reply(null, 0, 0, ms, "claude output wasn't JSON: " + clip(text));
		}
		int in = 0, outTok = 0;
		if (o.has("usage") && o.get("usage").isJsonObject()) {
			JsonObject u = o.getAsJsonObject("usage");
			in = intOf(u, "input_tokens") + intOf(u, "cache_read_input_tokens") + intOf(u, "cache_creation_input_tokens");
			outTok = intOf(u, "output_tokens");
		}
		if (o.has("is_error") && o.get("is_error").getAsBoolean()) {
			String msg = o.has("result") ? o.get("result").getAsString() : "unknown error";
			return new Reply(null, in, outTok, ms, "claude error: " + clip(msg));
		}
		if (o.has("structured_output") && o.get("structured_output").isJsonObject()) {
			return new Reply(o.getAsJsonObject("structured_output"), in, outTok, ms, null);
		}
		// Older CLI versions put the JSON in the text result.
		if (o.has("result")) {
			try {
				return new Reply(JsonParser.parseString(o.get("result").getAsString().strip()).getAsJsonObject(), in, outTok, ms, null);
			} catch (Exception ignored) {
				// fall through
			}
		}
		return new Reply(null, in, outTok, ms, "no structured answer in claude output");
	}

	private static int intOf(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : 0;
	}

	private static String clip(String s) {
		s = s.replaceAll("\\s+", " ");
		return s.length() > 160 ? s.substring(0, 160) + "..." : s;
	}

	/**
	 * How to start claude. Prefer a real claude.exe (native installer); an npm install is a .cmd
	 * shim, which has to go through cmd.exe.
	 */
	private List<String> launcher() {
		File direct = new File(command);
		if (direct.isAbsolute() && direct.isFile()) {
			return command.toLowerCase().endsWith(".cmd") ? List.of("cmd.exe", "/c", command) : List.of(command);
		}
		List<String> dirs = new ArrayList<>();
		String path = System.getenv("PATH");
		if (path != null) dirs.addAll(List.of(path.split(File.pathSeparator)));
		String home = System.getProperty("user.home");
		dirs.add(home + "\\.local\\bin");
		dirs.add(System.getenv().getOrDefault("APPDATA", "") + "\\npm");
		for (String d : dirs) {
			File exe = new File(d, command + ".exe");
			if (exe.isFile()) return List.of(exe.getAbsolutePath());
		}
		for (String d : dirs) {
			File cmd = new File(d, command + ".cmd");
			if (cmd.isFile()) return List.of("cmd.exe", "/c", cmd.getAbsolutePath());
		}
		return List.of(command);
	}

	/**
	 * Quote one argument for the Windows command line (the rules CreateProcess/MSVC use):
	 * backslashes before a quote are doubled and the quote is escaped. Java doesn't do this for us.
	 */
	static String winQuote(String arg) {
		if (!arg.isEmpty() && arg.chars().noneMatch(c -> c == ' ' || c == '\t' || c == '"')) return arg;
		StringBuilder sb = new StringBuilder("\"");
		int bs = 0;
		for (char c : arg.toCharArray()) {
			if (c == '\\') {
				bs++;
			} else if (c == '"') {
				sb.append("\\".repeat(bs * 2 + 1)).append('"');
				bs = 0;
			} else {
				sb.append("\\".repeat(bs)).append(c);
				bs = 0;
			}
		}
		sb.append("\\".repeat(bs * 2)).append('"');
		return sb.toString();
	}
}
