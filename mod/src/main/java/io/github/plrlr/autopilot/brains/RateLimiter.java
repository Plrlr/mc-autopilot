package io.github.plrlr.autopilot.brains;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.plrlr.autopilot.AutopilotMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Client-side limits per provider: requests per minute, tokens per minute, requests per day,
 * and (for Opus) calls per hour. Daily counts are saved to usage-YYYY-MM-DD.json so restarting
 * Minecraft doesn't reset them. We stop at 90% of each limit to stay clear of the real one.
 */
public final class RateLimiter {
	private final String name;
	private final int perMinute, tokensPerMinute, perDay, perHour;
	private final Deque<long[]> minute = new ArrayDeque<>(); // {time, tokens}
	private final Deque<Long> hour = new ArrayDeque<>();
	private final Path usageDir;
	private LocalDate day = LocalDate.now();
	private int today;
	private long backoffUntil;

	public RateLimiter(String name, int perMinute, int tokensPerMinute, int perDay, int perHour, Path usageDir) {
		this.name = name;
		this.perMinute = perMinute;
		this.tokensPerMinute = tokensPerMinute;
		this.perDay = perDay;
		this.perHour = perHour;
		this.usageDir = usageDir;
		this.today = loadToday();
	}

	/** Why a call isn't allowed right now, or null if it is. */
	public synchronized String blocked(int estTokens) {
		long now = System.currentTimeMillis();
		rollDay();
		trim(now);
		if (now < backoffUntil) return name + " backing off for " + (backoffUntil - now) / 1000 + " s";
		if (perDay > 0 && today >= perDay * 0.9) return name + " daily limit near (" + today + "/" + perDay + ")";
		if (perMinute > 0 && minute.size() >= Math.max(1, (int) (perMinute * 0.9))) return name + " per-minute limit near";
		if (tokensPerMinute > 0) {
			long used = 0;
			for (long[] e : minute) used += e[1];
			if (used + estTokens > tokensPerMinute * 0.9) return name + " tokens-per-minute limit near";
		}
		if (perHour > 0 && hour.size() >= perHour) return name + " hourly cap reached (" + perHour + "/h)";
		return null;
	}

	public synchronized void record(int tokens) {
		long now = System.currentTimeMillis();
		rollDay();
		minute.addLast(new long[]{now, tokens});
		hour.addLast(now);
		today++;
		saveToday();
	}

	/** After a 429/overload: wait with jitter before trying this provider again. */
	public synchronized void backoff(long baseMs) {
		long jitter = (long) (Math.random() * baseMs * 0.5);
		backoffUntil = System.currentTimeMillis() + baseMs + jitter;
	}

	public synchronized int callsThisHour() {
		trim(System.currentTimeMillis());
		return hour.size();
	}

	public int perHour() {
		return perHour;
	}

	private void trim(long now) {
		while (!minute.isEmpty() && now - minute.peekFirst()[0] > 60_000) minute.removeFirst();
		while (!hour.isEmpty() && now - hour.peekFirst() > 3_600_000) hour.removeFirst();
	}

	private void rollDay() {
		if (!LocalDate.now().equals(day)) {
			day = LocalDate.now();
			today = 0;
		}
	}

	private Path file() {
		return usageDir.resolve("usage-" + day + ".json");
	}

	private int loadToday() {
		try {
			Path f = file();
			if (!Files.exists(f)) return 0;
			JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			return o.has(name) ? o.get(name).getAsInt() : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	private void saveToday() {
		// All providers share one file; one lock across instances keeps them from overwriting each other.
		synchronized (RateLimiter.class) {
			saveTodayLocked();
		}
	}

	private void saveTodayLocked() {
		try {
			Files.createDirectories(usageDir);
			Path f = file();
			JsonObject o = Files.exists(f) ? JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
			o.addProperty(name, today);
			Files.writeString(f, new Gson().toJson(o), StandardCharsets.UTF_8);
		} catch (Exception e) {
			AutopilotMod.LOGGER.warn("Could not save usage counts: {}", e.toString());
		}
	}
}
