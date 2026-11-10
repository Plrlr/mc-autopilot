package io.github.plrlr.autopilot.state;

import io.github.plrlr.autopilot.AutopilotMod;
import io.github.plrlr.autopilot.Exposure;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The game-thread glue for RunNotes (2026-11-09, gene notes.persist): skills note what they saw
 * here, the file notes-<world>.json (next to progress-<world>.json) is read and written on one
 * background thread, and a load puts the remembered portals, stash and fortress back into
 * WorldMemory. The notes are kept in RAM either way (death.avoid_radius counts deaths from them);
 * with the gene off no file is read or written.
 */
public final class Notes {
	private static final long SAVE_EVERY_MS = 30_000, LOAD_TIMEOUT_MS = 10_000;
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "autopilot-notes");
		t.setDaemon(true);
		return t;
	});

	private static RunNotes notes = new RunNotes();
	private static Path file;
	private static int loadId;
	private static long loadStartMs = -1;
	private static volatile RunNotes loaded;
	private static volatile int loadedId = -1;
	private static long lastSaveMs;
	private static Path lastDir;
	private static String lastWorld;
	private static volatile boolean reloadAsked;

	private Notes() {}

	public static RunNotes get() {
		return notes;
	}

	public static RunNotes.Pos here(BlockPos p) {
		return new RunNotes.Pos(Mc.dimension(), p.getX(), p.getY(), p.getZ());
	}

	public static BlockPos block(RunNotes.Pos p) {
		return new BlockPos(p.x(), p.y(), p.z());
	}

	/** A new world: start empty, then read its notes in the background (gene on). */
	public static void load(Path dir, String world) {
		lastDir = dir;
		lastWorld = world;
		notes = new RunNotes();
		file = dir.resolve("notes-" + world.replaceAll("[^A-Za-z0-9_-]", "_") + ".json");
		loaded = null;
		loadStartMs = -1;
		if (!Tune.on("notes.persist")) return;
		int id = ++loadId;
		Path f = file;
		loadStartMs = System.currentTimeMillis();
		IO.execute(() -> {
			RunNotes n;
			try {
				n = Files.exists(f) ? RunNotes.parse(Files.readString(f, StandardCharsets.UTF_8)) : new RunNotes();
			} catch (Exception e) {
				AutopilotMod.LOGGER.warn("Could not read run notes: {}", e.toString());
				n = new RunNotes();
			}
			loaded = n;
			loadedId = id;
		});
	}

	/** Each tick on the game thread: take a finished load, and save now and then. Never throws. */
	public static void tick(WorldMemory memory) {
		try {
			if (reloadAsked && lastDir != null) {
				reloadAsked = false;
				load(lastDir, lastWorld);
			}
			if (loadStartMs >= 0) {
				RunNotes n = loaded;
				if (n != null && loadedId == loadId) {
					loadStartMs = -1;
					loaded = null;
					n.absorb(notes);
					notes = n;
					reseed(memory);
				} else if (System.currentTimeMillis() - loadStartMs > LOAD_TIMEOUT_MS) {
					// The disk didn't answer in time: carry on with what we have (a late answer is ignored).
					loadStartMs = -1;
					loadId++;
				}
			}
			if (notes.dirty() && System.currentTimeMillis() - lastSaveMs > SAVE_EVERY_MS) save();
		} catch (RuntimeException e) {
			AutopilotMod.LOGGER.warn("Run notes: {}", e.toString());
		}
	}

	/** This world's notes file, or null before a world was entered (the test harness's checkpoints copy it). */
	public static Path file() {
		return file;
	}

	/**
	 * The test harness swapped the save under us (a checkpoint restore keeps the world's name, so
	 * Autopilot.checkWorld doesn't notice): read the notes again on the next game tick. Any thread.
	 */
	public static void reloadNext() {
		reloadAsked = true;
	}

	/** Writes in the background; the game thread only serializes the (small) notes. */
	public static void save() {
		lastSaveMs = System.currentTimeMillis();
		if (!Tune.on("notes.persist") || file == null || loadStartMs >= 0) return;
		String text = notes.toJson();
		notes.saved();
		Path f = file;
		IO.execute(() -> {
			try {
				Files.createDirectories(f.getParent());
				Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
				Files.writeString(tmp, text, StandardCharsets.UTF_8);
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception e) {
				AutopilotMod.LOGGER.warn("Could not save run notes: {}", e.toString());
			}
		});
	}

	/** Puts back the marks a restart wiped: portals, stashes, the fortress, drops in the other dimension. */
	private static void reseed(WorldMemory memory) {
		int n = 0;
		for (RunNotes.Pos p : notes.portals) {
			memory.rememberIn("nether_portal", block(p), "nether_portal", p.dim());
			n++;
		}
		for (RunNotes.Pos p : notes.stashes) {
			memory.rememberIn("chest", block(p), "chest", p.dim());
			n++;
		}
		if (notes.fortress != null) {
			memory.rememberIn("nether_bricks", block(notes.fortress), "nether_bricks", notes.fortress.dim());
			n++;
		}
		RunNotes.Death d = notes.newestUnrecovered();
		// Drops in the other dimension keep while nobody is near them; the clock resumes where it was.
		if (d != null && Tune.on("death.recovery_v2") && !d.at.dim().equals(Mc.dimension()) && d.aged < DeathClock.BUDGET
				&& memory.everywhere("death").isEmpty()) {
			memory.rememberIn("death", block(d.at), "death", d.at.dim());
			DeathClock.shared().start(d.at.dim(), d.at.x(), d.at.y(), d.at.z(), d.aged);
			n++;
		}
		if (n > 0) Exposure.mark("notes.persist", "reseed " + n + " marks");
	}

	// ---- what skills saw or did (game thread)

	public static void portal(BlockPos p) {
		notes.portal(here(p));
	}

	public static void stash(BlockPos p) {
		notes.stash(here(p));
	}

	public static void fortress(BlockPos p) {
		notes.fortress(here(p));
	}

	public static void spawn(BlockPos p) {
		notes.spawn(here(p));
	}

	public static void eye(double fx, double fy, double fz, double dx, double dz, long tick) {
		notes.eye(new RunNotes.Eye((int) Math.floor(fx), (int) Math.floor(fy), (int) Math.floor(fz), dx, dz, tick));
	}
}
