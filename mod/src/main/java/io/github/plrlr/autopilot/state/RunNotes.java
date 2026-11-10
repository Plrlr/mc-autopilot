package io.github.plrlr.autopilot.state;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Run notes (2026-11-09, gene notes.persist): what the bot itself saw or did in this world that a
 * player would remember after a break: portals, the stash, the fortress, eye readings, where it
 * died and why, its spawn. WorldMemory lives in RAM and is wiped on every restart, so a game
 * resumed from a save (or a later leg of a multi-job run) forgot its own portal. The research
 * run that finished with 605 deaths won by persistence: walk back, re-arm, keep notes.
 *
 * Pure data and JSON only (no Minecraft classes) so it is unit-tested; state/Notes is the glue.
 * Fair play: only places we stood at, looked at or acted on are ever added.
 */
public final class RunNotes {
	public static final int MAX_DEATHS = 50, MAX_PORTALS = 32, MAX_EYES = 16, MAX_STASHES = 8;

	/** A block in a dimension ("overworld", "the_nether", "the_end"). */
	public record Pos(String dim, int x, int y, int z) {
		public double dist(Pos o) {
			double dx = x - o.x, dy = y - o.y, dz = z - o.z;
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
	}

	/** An eye of ender throw: where from, the horizontal direction it flew, when. */
	public record Eye(int x, int y, int z, double dx, double dz, long tick) {}

	public static final class Death {
		public final Pos at;
		public final String cause, killer;
		public final long tick;
		public final int value;
		public boolean recovered, dangerous;
		/** Despawn-clock ticks already spent on its drops (DeathClock), so a restart resumes the count. */
		public long aged;

		public Death(Pos at, String cause, String killer, long tick, int value) {
			this.at = at;
			this.cause = cause == null ? "unknown" : cause;
			this.killer = killer == null ? "" : killer;
			this.tick = tick;
			this.value = value;
		}
	}

	public final List<Pos> portals = new ArrayList<>();
	public final List<Pos> stashes = new ArrayList<>();
	public final List<Eye> eyes = new ArrayList<>();
	public final List<Death> deaths = new ArrayList<>();
	public Pos fortress, spawn;
	private boolean dirty;

	public boolean dirty() { return dirty; }
	public void saved() { dirty = false; }
	public void touch() { dirty = true; }

	public void portal(Pos p) {
		if (addNear(portals, p, 3, MAX_PORTALS)) dirty = true;
	}

	public void stash(Pos p) {
		if (addNear(stashes, p, 2, MAX_STASHES)) dirty = true;
	}

	/** Only the first fortress block seen: that's where the fortress search ended. */
	public void fortress(Pos p) {
		if (fortress != null) return;
		fortress = p;
		dirty = true;
	}

	public void spawn(Pos p) {
		if (p.equals(spawn)) return;
		spawn = p;
		dirty = true;
	}

	public void eye(Eye e) {
		eyes.add(e);
		trim(eyes, MAX_EYES);
		dirty = true;
	}

	public Death death(Pos at, String cause, String killer, long tick, int value) {
		Death d = new Death(at, cause, killer, tick, value);
		deaths.add(d);
		trim(deaths, MAX_DEATHS);
		dirty = true;
		return d;
	}

	/** The latest death, if its drops are still worth a trip (not picked up, not given up on). */
	public Death newestUnrecovered() {
		if (deaths.isEmpty()) return null;
		Death d = deaths.get(deaths.size() - 1);
		return d.recovered || d.dangerous ? null : d;
	}

	public void markRecovered(Death d) {
		if (d == null) return;
		d.recovered = true;
		dirty = true;
	}

	public void markDangerous(Death d) {
		if (d == null) return;
		d.dangerous = true;
		dirty = true;
	}

	/** Deaths in p's dimension within radius blocks of p (p's own death included). */
	public int deathsNear(Pos p, int radius) {
		int n = 0;
		for (Death d : deaths) if (d.at.dim().equals(p.dim()) && d.at.dist(p) <= radius) n++;
		return n;
	}

	/** Gene death.avoid_radius: a place that has killed us twice isn't worth walking back into. */
	public boolean dangerousPlace(Pos p, int radius) {
		return radius > 0 && deathsNear(p, radius) >= 2;
	}

	/** Appends what was noted in RAM before the file finished loading (load runs off-thread). */
	public void absorb(RunNotes early) {
		for (Pos p : early.portals) portal(p);
		for (Pos p : early.stashes) stash(p);
		for (Eye e : early.eyes) eye(e);
		for (Death d : early.deaths) {
			deaths.add(d);
			trim(deaths, MAX_DEATHS);
		}
		if (early.fortress != null) fortress(early.fortress);
		if (early.spawn != null) spawn(early.spawn);
		if (early.dirty) dirty = true;
	}

	private static boolean addNear(List<Pos> list, Pos p, int near, int cap) {
		for (Pos q : list) if (q.dim().equals(p.dim()) && q.dist(p) <= near) return false;
		list.add(p);
		trim(list, cap);
		return true;
	}

	private static <T> void trim(List<T> list, int cap) {
		while (list.size() > cap) list.remove(0);
	}

	// ---- JSON

	public String toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("version", 1);
		o.add("portals", posArray(portals));
		o.add("stashes", posArray(stashes));
		if (fortress != null) o.add("fortress", pos(fortress));
		if (spawn != null) o.add("spawn", pos(spawn));
		JsonArray es = new JsonArray();
		for (Eye e : eyes) {
			JsonObject j = new JsonObject();
			j.addProperty("x", e.x());
			j.addProperty("y", e.y());
			j.addProperty("z", e.z());
			j.addProperty("dx", e.dx());
			j.addProperty("dz", e.dz());
			j.addProperty("tick", e.tick());
			es.add(j);
		}
		o.add("eyes", es);
		JsonArray ds = new JsonArray();
		for (Death d : deaths) {
			JsonObject j = pos(d.at);
			j.addProperty("cause", d.cause);
			j.addProperty("killer", d.killer);
			j.addProperty("tick", d.tick);
			j.addProperty("value", d.value);
			j.addProperty("recovered", d.recovered);
			j.addProperty("dangerous", d.dangerous);
			j.addProperty("aged", d.aged);
			ds.add(j);
		}
		o.add("deaths", ds);
		return new Gson().toJson(o);
	}

	/** Never throws: a missing, corrupt or partly odd file gives what could be read (or nothing). */
	public static RunNotes parse(String text) {
		RunNotes n = new RunNotes();
		if (text == null || text.isBlank()) return n;
		try {
			JsonObject o = JsonParser.parseString(text).getAsJsonObject();
			for (JsonElement e : arr(o, "portals")) n.portals.add(pos(e.getAsJsonObject()));
			for (JsonElement e : arr(o, "stashes")) n.stashes.add(pos(e.getAsJsonObject()));
			if (o.has("fortress")) n.fortress = pos(o.getAsJsonObject("fortress"));
			if (o.has("spawn")) n.spawn = pos(o.getAsJsonObject("spawn"));
			for (JsonElement e : arr(o, "eyes")) {
				JsonObject j = e.getAsJsonObject();
				n.eyes.add(new Eye(j.get("x").getAsInt(), j.get("y").getAsInt(), j.get("z").getAsInt(),
						j.get("dx").getAsDouble(), j.get("dz").getAsDouble(), j.get("tick").getAsLong()));
			}
			for (JsonElement e : arr(o, "deaths")) {
				JsonObject j = e.getAsJsonObject();
				Death d = new Death(pos(j), str(j, "cause"), str(j, "killer"), j.has("tick") ? j.get("tick").getAsLong() : 0,
						j.has("value") ? j.get("value").getAsInt() : 0);
				d.recovered = j.has("recovered") && j.get("recovered").getAsBoolean();
				d.dangerous = j.has("dangerous") && j.get("dangerous").getAsBoolean();
				d.aged = j.has("aged") ? j.get("aged").getAsLong() : 0;
				n.deaths.add(d);
			}
		} catch (RuntimeException e) {
			return new RunNotes();
		}
		trim(n.portals, MAX_PORTALS);
		trim(n.stashes, MAX_STASHES);
		trim(n.eyes, MAX_EYES);
		trim(n.deaths, MAX_DEATHS);
		return n;
	}

	private static JsonArray arr(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) ? o.get(k).getAsString() : null;
	}

	private static JsonArray posArray(List<Pos> list) {
		JsonArray a = new JsonArray();
		for (Pos p : list) a.add(pos(p));
		return a;
	}

	private static JsonObject pos(Pos p) {
		JsonObject j = new JsonObject();
		j.addProperty("dim", p.dim());
		j.addProperty("x", p.x());
		j.addProperty("y", p.y());
		j.addProperty("z", p.z());
		return j;
	}

	private static Pos pos(JsonObject j) {
		return new Pos(j.get("dim").getAsString(), j.get("x").getAsInt(), j.get("y").getAsInt(), j.get("z").getAsInt());
	}
}
