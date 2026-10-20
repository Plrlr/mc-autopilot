package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.skills.FairProbe;
import net.minecraft.core.BlockPos;

import java.util.List;

/** After shore times out in water: pillar up in place by placing blocks under our feet. */
public class PillarOut extends EvolvedSkill {
	private static final List<String> BLOCKS = List.of("cobblestone", "cobbled_deepslate", "dirt",
			"netherrack", "stone", "andesite", "diorite", "granite", "planks");
	private int placed;
	private int lastPlaceTick;

	public static Option offer(Context c) {
		if (c.main() == null || !"shore".equals(c.main().skill())) return null;
		if (!"shore".equals(c.lastSkill())) return null;
		if (!"TIMEOUT".equals(c.lastCode()) && !"STUCK".equals(c.lastCode())) return null;
		if (Player.gameSeconds() - c.lastEndSeconds() > 30) return null;
		if (!Player.inWater()) return null;
		if (blockItem() == null) return null;
		return new Option("pillar_out", null, "shore failed in water: pillar up out of it in place");
	}

	private static String blockItem() {
		for (String b : BLOCKS) if (Player.count(b) >= 2) return b;
		return null;
	}

	private static boolean replaceable(String id) {
		return id != null && (id.contains("water") || id.endsWith("air") || id.contains("seagrass"));
	}

	private static boolean solidId(String id) {
		return id != null && !replaceable(id) && !id.contains("lava") && !id.contains("kelp")
				&& !id.contains("bubble");
	}

	@Override
	public String name() { return "pillar_out"; }

	@Override
	protected void start() {
		timeoutTicks = 20 * 20;
		placed = 0;
		lastPlaceTick = 0;
		Player.stopWalking();
		if (blockItem() == null) fail(Fail.NEED_ITEM, "no blocks to pillar with");
	}

	@Override
	protected void tick() {
		if (!Player.inWater() && Player.onGround()) {
			done("out of the water at " + Player.feet() + " after " + placed + " blocks");
			return;
		}
		if (ticks - lastPlaceTick > 80) {
			fail(Fail.NO_PROGRESS, "no block placed for 4 s (" + placed + " placed): nothing to stand on");
			return;
		}
		String item = blockItem();
		if (item == null) { fail(Fail.NEED_ITEM, "ran out of blocks after " + placed); return; }
		BlockPos f = Player.feet();
		// Headroom: clear the block two above our feet so jumping can lift us.
		BlockPos ceil = f.above(2);
		String cid = Player.blockIfVisible(ceil);
		if (solidId(cid) && !cid.contains("bedrock")) {
			if (FairProbe.lavaSeenNear(ceil)) { fail(Fail.HAZARD, "lava beside the ceiling block"); return; }
			Player.press(Player.Key.JUMP, false);
			Player.holdBestToolFor(ceil);
			Player.lookAt(ceil);
			Player.mine(ceil);
			lastPlaceTick = Math.max(lastPlaceTick, ticks - 40);
			return;
		}
		Player.hold(item);
		Player.turn(Player.yaw(), 90);
		Player.press(Player.Key.JUMP, true);
		BlockPos t = f.below();
		String tid = Player.blockIfVisible(t);
		if (replaceable(tid) && Player.place(t)) {
			placed++;
			lastPlaceTick = ticks;
		}
	}
}
