package io.github.plrlr.autopilot;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The genes: defaults are the hand-tuned values plus settled winners, params are clamped, bad files fall back safely. */
class TuneTest {
	@AfterEach
	void reset() {
		Tune.reset();
	}

	@Test
	void defaultsAreTheHandTunedValuesPlusTheSettledWinners() {
		assertEquals(7, Tune.get("reflex.creeper_dist"));
		assertEquals(8, Tune.i("combat.flee_hp"));
		assertEquals(14, Tune.i("food.eat_at"));
		assertEquals(8, Tune.i("food.stock"));
		// Baked in 2026-09-29 (what every champion since gen 49 plays; scripts/loop/genes.py).
		assertFalse(Tune.on("route.armor_before_portal"));
		assertTrue(Tune.on("cave.torches"));
		assertTrue(Tune.on("focus.commit"));
		assertEquals(15, Tune.i("plan.hide_hp"));
		assertEquals(12, Tune.i("death.recover_night_armor"));
		assertEquals(0, Tune.get("learned.weight"));
		assertEquals(0, Tune.changed().size());
	}

	@Test
	void appliesAndClamps() {
		JsonObject p = new JsonObject();
		p.addProperty("combat.flee_hp", 99);          // above the limit of 14
		p.addProperty("food.stock", 5.6);             // whole-number gene: rounded
		p.addProperty("route.bed", false);            // switches accept booleans
		p.addProperty("no.such.gene", 3);
		String warn = Tune.apply(p, "test");
		assertEquals(14, Tune.i("combat.flee_hp"));
		assertEquals(6, Tune.i("food.stock"));
		assertFalse(Tune.on("route.bed"));
		assertTrue(warn.contains("no.such.gene"));
		assertEquals(3, Tune.changed().size());
	}

	@Test
	void loadsTheLoopsWrappedFormatAndSurvivesBadFiles(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
		Path f = dir.resolve("params.json");
		Files.writeString(f, "{\"id\":\"g12@gen40\",\"genes\":{\"food.stock\":12}}");
		assertTrue(Tune.load(f).contains("g12@gen40"));
		assertEquals(12, Tune.i("food.stock"));
		Files.writeString(f, "not json");
		assertTrue(Tune.load(f).startsWith("params: defaults"));
		assertEquals(8, Tune.i("food.stock"));
		assertEquals("params: defaults", Tune.load(dir.resolve("missing.json")));
	}
}
