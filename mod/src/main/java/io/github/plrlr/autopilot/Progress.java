package io.github.plrlr.autopilot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.plrlr.autopilot.plan.Goal;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tracks the furthest milestone (1-13) this world has reached, saved per world so the number
 * survives restarts. This is the headline result for comparing brains.
 */
public final class Progress {
	private final Path dir;
	private String world = "";
	private int furthest;
	private int deaths;
	private boolean sawNight;
	private int nightsSurvived;
	private boolean dragonKilled;

	public Progress(Path dir) {
		this.dir = dir;
	}

	public int furthest() {
		return furthest;
	}

	public int deaths() {
		return deaths;
	}

	public void load(String worldName) {
		world = worldName.replaceAll("[^A-Za-z0-9_-]", "_");
		furthest = 0;
		deaths = 0;
		nightsSurvived = 0;
		dragonKilled = false;
		try {
			Path f = file();
			if (Files.exists(f)) {
				JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
				furthest = o.has("furthest") ? o.get("furthest").getAsInt() : 0;
				deaths = o.has("deaths") ? o.get("deaths").getAsInt() : 0;
				nightsSurvived = o.has("nights") ? o.get("nights").getAsInt() : 0;
				dragonKilled = o.has("dragon") && o.get("dragon").getAsBoolean();
			}
		} catch (Exception e) {
			AutopilotMod.LOGGER.warn("Could not read progress: {}", e.toString());
		}
	}

	public void died() {
		deaths++;
		save();
	}

	/** Returns the new milestone number if it just went up, else 0. */
	public int update(WorldMemory memory) {
		LocalPlayer pl = Mc.player();
		if (pl == null) return 0;
		boolean night = Mc.isNight() && Mc.dimension().equals("overworld");
		if (night) sawNight = true;
		else if (sawNight) {
			sawNight = false;
			nightsSurvived++;
		}
		if (Mc.dimension().equals("the_end")) {
			for (Entity e : Mc.mc().level.entitiesForRendering()) {
				if (e instanceof EnderDragon d && d.isDeadOrDying()) dragonKilled = true;
			}
		}
		int m = 0;
		if (Goal.have("wooden_pickaxe") > 0 || Mc.count("crafting_table") > 0) m = 1;
		if (m >= 1 && Goal.have("stone_pickaxe") > 0) m = 2;
		if (m >= 2 && Mc.count("food") >= 5 && nightsSurvived >= 1) m = 3;
		// Food, armor and diamonds are side rungs of a fast run (Goal.optional): the main line
		// counts without them.
		if (m >= 2 && Goal.have("iron_pickaxe") > 0) m = 4;
		if (m >= 4 && Goal.hasArmor("iron_helmet") && Goal.hasArmor("iron_chestplate") && Goal.hasArmor("iron_leggings") && Goal.hasArmor("iron_boots")) m = 5;
		if (m >= 4 && Goal.have("diamond_pickaxe") > 0) m = 6;
		// Later rungs are proven by where you are or what you hold; reaching them implies the earlier ones mattered less.
		if (Mc.dimension().equals("the_nether")) m = Math.max(m, 7);
		if (Goal.have("blaze_rod") > 0) m = Math.max(m, 8);
		if (Mc.count("ender_pearl") > 0 && m >= 8) m = Math.max(m, 9);
		if (Mc.count("ender_eye") > 0) m = Math.max(m, 10);
		if (memory.nearest("end_portal_frame") != null) m = Math.max(m, 11);
		if (Mc.dimension().equals("the_end")) m = Math.max(m, 12);
		// The kill is proven by the game, not guessed from the dragon being out of view: we saw it
		// die, or the exit portal on the island is lit (its end_portal blocks only appear once the
		// dragon is dead).
		if (Mc.dimension().equals("the_end") && memory.nearest("end_portal") != null) dragonKilled = true;
		if (dragonKilled) m = Math.max(m, 13);
		if (m > furthest) {
			furthest = m;
			save();
			return m;
		}
		return 0;
	}

	private Path file() {
		return dir.resolve("progress-" + world + ".json");
	}

	private void save() {
		if (world.isEmpty()) return;
		try {
			Files.createDirectories(dir);
			JsonObject o = new JsonObject();
			o.addProperty("furthest", furthest);
			o.addProperty("deaths", deaths);
			o.addProperty("nights", nightsSurvived);
			o.addProperty("dragon", dragonKilled);
			Files.writeString(file(), new Gson().toJson(o), StandardCharsets.UTF_8);
		} catch (Exception e) {
			AutopilotMod.LOGGER.warn("Could not save progress: {}", e.toString());
		}
	}
}
