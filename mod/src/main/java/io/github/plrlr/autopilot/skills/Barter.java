package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashMap;
import java.util.Map;

/**
 * barter: trade gold ingots with piglins for ender pearls (and whatever else they throw).
 *
 * Mechanics (minecraft.wiki/w/Bartering, checked 2026-09-27): an adult piglin takes a gold ingot
 * the player uses on it, inspects it for 6 s, then throws one random item; babies keep the gold,
 * brutes ignore it. Pearls come 2-4 at a time about 3% of the time: ~15 ingots per pearl, so the
 * loop's genes decide how much gold is worth trading before hunting endermen for the rest.
 * Wearing any gold armor keeps piglins calm (the planner puts a gold helmet on first).
 *
 * One ingot per adult piglin in reach, all inspecting at once; then pick up what they throw;
 * repeat until the ingots run out, enough pearls, or no piglin is left in reach.
 */
public final class Barter extends Skill {
	/** Piglin entity id -> tick it was last given an ingot (it's busy for ~6 s after). */
	private final Map<Integer, Integer> given = new HashMap<>();
	/** Ingots traded this session, across barter runs (the planner's gold budget counts them). */
	private static int tradedTotal;

	public static int traded() {
		return tradedTotal;
	}
	private int ingotsGiven;
	private int pearlsBefore;
	private int goldBefore;
	/** Barter loot that isn't worth a slot: a full bag breaks other skills (a test run took 66 gravel). */
	private static final java.util.Set<String> JUNK = java.util.Set.of("gravel", "blackstone", "soul_sand", "netherrack",
			"basalt", "nether_brick", "soul_soil", "magma_cream", "gold_ingot");
	private int lastGiveTick;
	private Piglin current;

	@Override
	public String name() {
		return "barter";
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 150;
		if (!Mc.dimension().equals("the_nether")) {
			fail(Fail.WRONG_PLACE, "piglins are in the Nether");
			return;
		}
		if (Mc.count("gold_ingot") == 0) {
			fail(Fail.NEED_ITEM, "no gold ingots to trade");
			return;
		}
		pearlsBefore = Mc.count("ender_pearl");
		goldBefore = Mc.count("gold_ingot");
	}

	private static boolean tradable(Entity e) {
		return e instanceof Piglin p && p.isAlive() && !p.isBaby() && Mc.canSee(p);
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		int pearls = Mc.count("ender_pearl") - pearlsBefore;
		// Ingots the piglins took (the inventory catches up a tick after the server takes one).
		int gone = Math.max(0, goldBefore - Mc.count("gold_ingot"));
		if (gone > ingotsGiven) {
			tradedTotal += gone - ingotsGiven;
			ingotsGiven = gone;
		}
		if (Mc.count("ender_pearl") >= Tune.i("pearls.target")) {
			done("enough pearls: " + Mc.count("ender_pearl") + " (" + ingotsGiven + " ingots traded)");
			return;
		}
		// Drops first: whatever the piglins threw lies around them.
		ItemEntity drop = nearestDrop(pl);
		if (drop != null && ticks - lastGiveTick > 20 * 3) {
			if (ticks % 10 == 0) Bari.path(new GoalBlock(drop.blockPosition()));
			return;
		}
		if (Mc.count("gold_ingot") == 0) {
			// Last trades still being inspected: wait for them, then pick up.
			if (ticks - lastGiveTick < 20 * 8 || drop != null) return;
			done("traded " + ingotsGiven + " ingots, got " + pearls + " pearls");
			return;
		}
		// The next free adult piglin (not inspecting one of ours).
		if (current == null || !tradable(current) || busy(current)) {
			current = null;
			double best = 24;
			for (Entity e : Mc.mc().level.entitiesForRendering()) {
				if (!tradable(e) || busy(e)) continue;
				double d = e.distanceTo(pl);
				if (d < best) {
					best = d;
					current = (Piglin) e;
				}
			}
		}
		if (current == null) {
			// Everyone in reach is inspecting: wait; nobody at all: done for here.
			if (!given.isEmpty() && ticks - lastGiveTick < 20 * 8) return;
			if (ingotsGiven == 0) fail(Fail.NOT_FOUND, "no adult piglin in sight");
			else done("traded " + ingotsGiven + " ingots, got " + pearls + " pearls; no free piglin left here");
			return;
		}
		double d = current.distanceTo(pl);
		if (d > 2.8) {
			if (ticks % 10 == 0) Bari.path(new GoalNear(current.blockPosition(), 1));
			return;
		}
		Bari.stop();
		if (!Items2.id(pl.getMainHandItem()).equals("gold_ingot")) {
			Mc.holdItem(s -> Items2.id(s).equals("gold_ingot"));
			return;
		}
		Mc.lookAt(current.getBoundingBox().getCenter());
		int before = Mc.count("gold_ingot");
		Mc.mc().gameMode.interact(pl, current, new EntityHitResult(current), InteractionHand.MAIN_HAND);
		Mc.swing();
		given.put(current.getId(), ticks);
		lastGiveTick = ticks;
		current = null;
	}

	private boolean busy(Entity e) {
		Integer t = given.get(e.getId());
		return t != null && ticks - t < 20 * 7;
	}

	private static ItemEntity nearestDrop(LocalPlayer pl) {
		ItemEntity best = null;
		double bd = 12;
		for (Entity e : Mc.mc().level.entitiesForRendering()) {
			if (!(e instanceof ItemEntity it) || !it.isAlive()) continue;
			// Only loot worth a slot, and not what we dropped ourselves.
			if (JUNK.contains(Items2.id(it.getItem())) || it.getAge() < 10) continue;
			double d = e.distanceTo(pl);
			if (d < bd) {
				bd = d;
				best = it;
			}
		}
		return best;
	}
}
