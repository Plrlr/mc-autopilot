package io.github.plrlr.autopilot.log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.plrlr.autopilot.AutopilotMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One JSONL line per decision or event, in .minecraft/mc-autopilot/logs/run-YYYY-MM-DD.jsonl.
 * Writes happen on a background thread so disk hiccups never stall the game.
 */
public final class RunLog {
	private static final Gson GSON = new Gson();
	private final Path dir;
	private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "autopilot-log");
		t.setDaemon(true);
		return t;
	});

	public RunLog(Path dir) {
		this.dir = dir;
	}

	public Path dir() {
		return dir;
	}

	public void write(JsonObject line) {
		line.addProperty("t", System.currentTimeMillis());
		String text = GSON.toJson(line) + "\n";
		Path file = dir.resolve("run-" + LocalDate.now() + ".jsonl");
		writer.execute(() -> {
			try {
				Files.createDirectories(dir);
				Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (IOException e) {
				AutopilotMod.LOGGER.warn("Could not write run log: {}", e.toString());
			}
		});
	}

	public void event(String kind, String detail) {
		JsonObject o = new JsonObject();
		o.addProperty("event", kind);
		o.addProperty("detail", detail);
		write(o);
	}
}
