package io.github.plrlr.autopilot.test;

import io.github.plrlr.autopilot.AutopilotMod;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Goal;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The checkpoint bank's harness side (Go-Explore: return to states the bot really reached, and
 * explore on from there). When a run first reaches a stage on the way to the dragon, the world is
 * saved and zipped; the learning loop keeps the zips and starts later runs from them, so every
 * stage gets practice without replaying the whole game first. Test worlds only: saving uses the
 * test server's save-all command, like the scenarios; the bot itself never runs commands.
 */
final class Bank {
	private Bank() {}

	/**
	 * Stages in order; index 0 is a fresh world. "diamond" (since 2026-09-29) is the diamond route's
	 * kit: the pickaxe made, obsidian and the frame to go. "lava" is the kit with lava in reach: the portal
	 * wall is lava to obsidian (by gen 29, 22 of 30 kit starts saw lava, 5 made any obsidian), so
	 * starts from there practice the cast without first spending ten minutes looking for lava.
	 */
	static final List<String> STAGES = List.of("spawn", "kit", "lava", "diamond", "nether", "rods", "eyes", "stronghold", "end");

	/** Remembered lava this close (blocks) makes a kit a "lava" stage. */
	private static final double LAVA_NEAR = 16;

	/** The furthest stage the player's current state shows. Client thread. */
	static int stage(Minecraft mc) {
		String dim = mc.player.level().dimension().identifier().getPath();
		if (dim.equals("the_end")) return 8;
		if (AutopilotMod.instance().memory.nearest("end_portal_frame") != null) return 7;
		if (Mc.count("ender_eye") >= 14) return 6;
		if (Mc.count("blaze_rod") >= 7) return 5;
		if (dim.equals("the_nether")) return 4;
		// The diamond route's last leg (2026-09-29): a diamond pickaxe, a bucket and flint and steel;
		// starts from here practice the obsidian and the frame without the long dig for diamonds.
		if (Items2.bestTier("pickaxe") >= 3 && Goal.have("bucket") >= 1 && Mc.count("flint_and_steel") > 0) return 3;
		if (Items2.bestTier("pickaxe") >= 2 && Goal.have("bucket") >= 2 && Mc.count("flint_and_steel") > 0) {
			var lava = AutopilotMod.instance().memory.nearest("lava");
			boolean near = lava != null && Vec3.atCenterOf(lava.pos()).distanceTo(mc.player.position()) <= LAVA_NEAR;
			return near ? 2 : 1;
		}
		return 0;
	}

	/**
	 * Saves the world and zips it to checkpoints/<stage>.zip with a small description next to it.
	 * ctx is null during a free run (FreeRun): then the server's own task queue does the saving.
	 */
	static void save(ClientGameTestContext ctx, TestSingleplayerContext sp, int stage, long gameSeconds) {
		String name = STAGES.get(stage);
		if (ctx == null) {
			FreeRun.saveWorld();
		} else {
			sp.getServer().runCommand("save-all flush");
			// 26.3 keeps the player (inventory, position, dimension) in its own file, not in level.dat
			// (which only names the owner: singleplayer_uuid), and save-all didn't write it: the first
			// restore self-test came back with a fresh player. Save the players explicitly.
			sp.getServer().runOnServer(server -> server.getPlayerList().saveAll());
			ctx.waitTicks(40);
		}
		Path src = sp.getWorldSave().getSaveDirectory();
		Path dir = Path.of("checkpoints");
		try {
			Files.createDirectories(dir);
			Path zip = dir.resolve(name + ".zip");
			try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip)); Stream<Path> files = Files.walk(src)) {
				for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
					if (f.getFileName().toString().equals("session.lock")) continue;
					out.putNextEntry(new ZipEntry(src.relativize(f).toString().replace('\\', '/')));
					try (InputStream in = Files.newInputStream(f)) {
						in.transferTo(out);
					} catch (IOException e) {
						// A file the server is writing right now: skip it (region files were flushed).
					}
					out.closeEntry();
				}
			}
			java.util.function.Supplier<String> invF = () -> {
				Minecraft mc = Minecraft.getInstance();
				StringBuilder sb = new StringBuilder();
				for (var st : mc.player.getInventory().getNonEquipmentItems())
					if (!st.isEmpty()) sb.append(Items2.id(st)).append(' ').append(st.getCount()).append(", ");
				return sb.toString();
			};
			java.util.function.Supplier<String> dimF = () -> Minecraft.getInstance().player.level().dimension().identifier().getPath();
			String inv = ctx == null ? FreeRun.onClient(invF) : ctx.computeOnClient(mc -> invF.get());
			String dim = ctx == null ? FreeRun.onClient(dimF) : ctx.computeOnClient(mc -> dimF.get());
			Files.writeString(dir.resolve(name + ".json"), String.format(
					"{\"stage\":\"%s\",\"game_seconds\":%d,\"seed\":\"%s\",\"dimension\":\"%s\",\"inventory\":\"%s\",\"bytes\":%d}",
					name, gameSeconds, System.getProperty("autopilot.test.seed", ""), dim, inv.replace("\"", ""), Files.size(zip)),
					StandardCharsets.UTF_8);
			System.out.println("[autopilot-test] CHECKPOINT " + name + " at " + gameSeconds + "s (" + Files.size(zip) / 1024 + " KB)");
		} catch (IOException e) {
			System.out.println("[autopilot-test] CHECKPOINT " + name + " failed: " + e);
		}
	}

	/**
	 * Replaces the freshly created test world with a saved checkpoint and opens it: close the world,
	 * swap the save folder's contents for the zip's, open the same save again.
	 */
	static TestSingleplayerContext restore(ClientGameTestContext ctx, TestSingleplayerContext sp, Path zip) {
		TestWorldSave save = sp.getWorldSave();
		Path dir = save.getSaveDirectory();
		sp.close();
		try {
			try (Stream<Path> old = Files.walk(dir)) {
				for (Path p : old.sorted(Comparator.reverseOrder()).toList()) {
					if (!p.equals(dir) && !p.getFileName().toString().equals("session.lock")) Files.delete(p);
				}
			}
			try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
				for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
					Path out = dir.resolve(e.getName()).normalize();
					if (!out.startsWith(dir)) continue; // no paths outside the save
					if (e.isDirectory()) {
						Files.createDirectories(out);
						continue;
					}
					Files.createDirectories(out.getParent());
					try (OutputStream o = Files.newOutputStream(out)) {
						in.transferTo(o);
					}
				}
			}
		} catch (IOException e) {
			throw new AssertionError("couldn't restore checkpoint " + zip + ": " + e, e);
		}
		System.out.println("[autopilot-test] START from checkpoint " + zip.getFileName());
		TestSingleplayerContext reopened = save.open();
		ctx.waitTicks(60);
		return reopened;
	}
}
