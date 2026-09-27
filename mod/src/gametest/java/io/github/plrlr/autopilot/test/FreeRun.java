package io.github.plrlr.autopilot.test;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Lets the game run at normal speed while the autopilot plays inside a client game test.
 *
 * Fabric's client game tests run the client, the server and the test thread in lockstep: each
 * game tick the three take turns (ThreadingImpl's phaser, and the client and server each wait on
 * a semaphore for the test thread). That makes tests deterministic, but the client and server
 * never run at the same time, so a long run plays in slow motion: ~0.65x on the cloud and on an
 * 8-thread laptop alike, the server "15000 ms behind" every 30 s.
 *
 * During the long play the test steps aside: with ThreadingImpl.testThread null and phases off,
 * the client and server skip the semaphores and the phaser (checked in its bytecode, Fabric API
 * 0.161.0) and run in parallel like a normal game. The test thread then only posts short read-only
 * tasks to the game's own task queues (Minecraft.submit, MinecraftServer.submit). At the end it
 * rejoins, and the framework takes over again to close the world.
 */
final class FreeRun {
	private FreeRun() {}

	private static final String IMPL = "net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl";
	private static Thread savedTestThread;
	private static boolean active;

	static boolean active() {
		return active;
	}

	/** Steps aside. Returns false (and changes nothing) if the framework isn't what we expect. */
	static synchronized boolean start() {
		try {
			Class<?> c = Class.forName(IMPL);
			Field phases = c.getDeclaredField("enablePhases");
			Field test = c.getDeclaredField("testThread");
			phases.setAccessible(true);
			savedTestThread = (Thread) test.get(null);
			test.set(null, null);
			phases.setBoolean(null, false);
			// Right now the client and server are parked on their semaphores, waiting for the test
			// thread's next task (it's the test's turn). Wake each once with "no task": they leave
			// the lockstep and, with no test thread registered, don't wait again.
			c.getField("taskToRun").set(null, null);
			((java.util.concurrent.Semaphore) c.getField("CLIENT_SEMAPHORE").get(null)).release();
			((java.util.concurrent.Semaphore) c.getField("SERVER_SEMAPHORE").get(null)).release();
			active = true;
			System.out.println("[autopilot-test] FREE RUN: client and server run in parallel (no lockstep)");
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.out.println("[autopilot-test] FREE RUN unavailable, playing in lockstep: " + e);
			return false;
		}
	}

	/** Rejoins the lockstep so the framework can finish the test. */
	static synchronized void stop() {
		if (!active) return;
		try {
			Class<?> c = Class.forName(IMPL);
			Field phases = c.getDeclaredField("enablePhases");
			Field test = c.getDeclaredField("testThread");
			phases.setAccessible(true);
			phases.setBoolean(null, true);
			test.set(null, savedTestThread);
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.out.println("[autopilot-test] FREE RUN: couldn't rejoin the lockstep: " + e);
		}
		active = false;
	}

	/** Runs a short task on the client thread and waits for its result (10 s at most). */
	static <T> T onClient(Supplier<T> task) {
		try {
			return Minecraft.getInstance().submit(task).get(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new RuntimeException("client task failed", e);
		}
	}

	static void onClient(Runnable task) {
		onClient(() -> {
			task.run();
			return null;
		});
	}

	/** Saves the world and the player files on the server thread (for the checkpoint bank). */
	static void saveWorld() {
		var server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) return;
		try {
			server.submit(() -> {
				server.saveEverything(true, true, true);
				server.getPlayerList().saveAll();
			}).get(60, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new RuntimeException("save failed", e);
		}
	}
}
