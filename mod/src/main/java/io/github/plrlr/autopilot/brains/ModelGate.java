package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.plrlr.autopilot.Tune;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 2026-11-09, gene learned.gate: lets learned.weight act only while the model passes the held-out
 * check train.py writes into learned.json (adv_r2_heldout, sign_check). The champion played weight
 * 1.0 with a held-out R2 of -0.007 and sign agreement 0.557 over 20 trainings: a coin flip that
 * re-ranked the rules. Closed by default: no file, a missing field or a parse error means unproven.
 * Learned.java reads the same file but ignores these fields; this reads only them.
 */
public final class ModelGate {
	/** The held-out numbers; NaN / 0 when missing. */
	public record Check(double advR2, double agree, int n) {
		static final Check NONE = new Check(Double.NaN, Double.NaN, 0);

		/** Strictly better than both thresholds, on at least minN checked choices. NaN never passes. */
		public boolean passes(double minR2, double minAgree, int minN) {
			return advR2 > minR2 && agree > minAgree && n >= minN;
		}
	}

	private volatile Check check = Check.NONE;
	private final AtomicInteger loads = new AtomicInteger();

	/** Reads the file off the game thread with a 10 s timeout (pattern of Learned.loadAsync). */
	public void loadAsync(Path file) {
		int n = loads.incrementAndGet();
		check = Check.NONE;
		CompletableFuture.supplyAsync(() -> {
			try {
				return file != null && Files.exists(file) ? parse(Files.readString(file, StandardCharsets.UTF_8)) : Check.NONE;
			} catch (Exception e) {
				return Check.NONE;
			}
		}).orTimeout(10, TimeUnit.SECONDS).whenComplete((c, err) -> {
			if (loads.get() == n) check = err != null || c == null ? Check.NONE : c;
		});
	}

	/** Open = the model proved itself on held-out games, against the genes at decision time. */
	public boolean open() {
		return check.passes(Tune.get("learned.gate_r2"), Tune.get("learned.gate_sign"), Tune.i("learned.gate_n"));
	}

	/** learned.json's text -> its held-out numbers. Pure: anything unexpected gives NONE (closed). */
	public static Check parse(String json) {
		try {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			if (!o.has("adv_r2_heldout") || o.get("adv_r2_heldout").isJsonNull()) return Check.NONE;
			if (!o.has("sign_check") || !o.get("sign_check").isJsonObject()) return Check.NONE;
			JsonObject s = o.getAsJsonObject("sign_check");
			if (!s.has("agree") || s.get("agree").isJsonNull() || !s.has("n") || s.get("n").isJsonNull()) return Check.NONE;
			return new Check(o.get("adv_r2_heldout").getAsDouble(), s.get("agree").getAsDouble(), s.get("n").getAsInt());
		} catch (RuntimeException e) {
			return Check.NONE;
		}
	}
}
