package io.github.plrlr.autopilot.log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.plrlr.autopilot.AutopilotMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the autopilot has learned across all its runs and worlds: how often each action worked
 * and why it failed, kept in .minecraft/mc-autopilot/lessons.json. Actions that keep failing
 * get paused sooner, and the worst ones are shown to the brains so they plan around them.
 * Game facts only (action names and failure reasons), nothing personal.
 */
public final class Lessons {
	/** Tally for one action, e.g. "craft furnace". */
	static final class Tally {
		int ok;
		int fail;
		double secs;
		/** Failure code -> count, and one example detail per code. */
		final Map<String, Integer> why = new LinkedHashMap<>();
		final Map<String, String> example = new LinkedHashMap<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path file;
	private final Map<String, Tally> tallies = new LinkedHashMap<>();
	private int unsaved;
	private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "autopilot-lessons");
		t.setDaemon(true);
		return t;
	});

	/** file may be null (tests): nothing is read or written. */
	public Lessons(Path file) {
		this.file = file;
		load();
	}

	/**
	 * Records how an action ended, by failure code (Fail) with the detail kept as an example.
	 * Interruptions and deaths say nothing about the action itself, so they aren't counted.
	 */
	public synchronized void record(String action, boolean ok, String code, String detail, double seconds) {
		if (!ok && ("INTERRUPTED".equals(code) || "DIED".equals(code))) return;
		Tally t = tallies.computeIfAbsent(action, k -> new Tally());
		t.secs += seconds;
		if (ok) t.ok++;
		else {
			t.fail++;
			String c = code == null ? "UNKNOWN" : code;
			t.why.merge(c, 1, Integer::sum);
			t.example.put(c, normalize(detail));
		}
		if (++unsaved >= 10) save();
	}

	/** Share of tries that failed, or 0 with too few tries to judge. */
	public synchronized double failRate(String action) {
		Tally t = tallies.get(action);
		if (t == null || t.ok + t.fail < 5) return 0;
		return t.fail / (double) (t.ok + t.fail);
	}

	/**
	 * The actions that fail most, one short line each: "craft furnace failed 6 of 9 (mostly
	 * PLACE_FAILED: couldn't place the crafting_table)". Only 3+ failures at a 40%+ fail rate.
	 */
	public synchronized List<String> worst(int max) {
		List<Map.Entry<String, Tally>> bad = new ArrayList<>();
		for (var e : tallies.entrySet()) {
			Tally t = e.getValue();
			if (t.fail >= 3 && t.fail >= 0.4 * (t.ok + t.fail)) bad.add(e);
		}
		bad.sort(Comparator.comparingInt((Map.Entry<String, Tally> e) -> e.getValue().fail).reversed());
		List<String> out = new ArrayList<>();
		for (var e : bad) {
			if (out.size() >= max) break;
			Tally t = e.getValue();
			String top = t.why.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("?");
			String ex = t.example.get(top);
			out.add(e.getKey() + " failed " + t.fail + " of " + (t.ok + t.fail) + " (mostly " + top + (ex == null ? "" : ": " + ex) + ")");
		}
		return out;
	}

	/** Numbers differ run to run ("got 2/9", "after 60 s"); without them reasons group together. */
	static String normalize(String detail) {
		String d = detail.replaceAll("-?\\d+(\\.\\d+)?", "#");
		return d.length() > 70 ? d.substring(0, 70) : d;
	}

	private void load() {
		if (file == null || !Files.exists(file)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var e : root.entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				Tally t = new Tally();
				t.ok = o.has("ok") ? o.get("ok").getAsInt() : 0;
				t.fail = o.has("fail") ? o.get("fail").getAsInt() : 0;
				t.secs = o.has("secs") ? o.get("secs").getAsDouble() : 0;
				if (o.has("why")) for (var w : o.getAsJsonObject("why").entrySet()) t.why.put(w.getKey(), w.getValue().getAsInt());
				if (o.has("example")) for (var w : o.getAsJsonObject("example").entrySet()) t.example.put(w.getKey(), w.getValue().getAsString());
				tallies.put(e.getKey(), t);
			}
		} catch (Exception e) {
			AutopilotMod.LOGGER.warn("Could not read lessons: {}", e.toString());
		}
	}

	/** Writes the file in the background (the game thread never waits on the disk). */
	public synchronized void save() {
		unsaved = 0;
		if (file == null) return;
		JsonObject root = new JsonObject();
		for (var e : tallies.entrySet()) {
			Tally t = e.getValue();
			JsonObject o = new JsonObject();
			o.addProperty("ok", t.ok);
			o.addProperty("fail", t.fail);
			o.addProperty("secs", Math.round(t.secs));
			JsonObject why = new JsonObject();
			t.why.forEach(why::addProperty);
			o.add("why", why);
			JsonObject ex = new JsonObject();
			t.example.forEach(ex::addProperty);
			o.add("example", ex);
			root.add(e.getKey(), o);
		}
		String text = GSON.toJson(root);
		writer.execute(() -> {
			try {
				Files.createDirectories(file.getParent());
				Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
				Files.writeString(tmp, text, StandardCharsets.UTF_8);
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception e) {
				AutopilotMod.LOGGER.warn("Could not save lessons: {}", e.toString());
			}
		});
	}
}
