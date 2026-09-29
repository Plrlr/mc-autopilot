package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalBlock;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.plan.Option;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * obsidian_mold [n]: obsidian one block at a time, on solid ground (the diamond route's safe way).
 *
 * Hardening a deep lava lake leaves one layer of obsidian over more lava: mining it drops the
 * block into the lava, where it burns, and opens a hole to fall in. So, like a careful player,
 * make each block in a one-block pit (MoldSite) with the two buckets:
 *
 *   SITE   pick a stand S, facing a pit P and a water spot W (MoldSite); walk to S
 *   DIG    dig the pit
 *   LAVA   (child fill_bucket lava) a bucket of lava from the pool, then back to S
 *   POUR   lava into the pit, then water on W: it runs into the pit and the lava turns to obsidian
 *   SCOOP  take the water back
 *   MINE   mine the obsidian in the pit (it's on solid ground: nothing burns)
 *   PICKUP (child pickup) step down into the pit for it; around again from LAVA
 *
 * Needs a diamond pickaxe, a water bucket and a second bucket.
 */
public final class ObsidianMold extends Composite {
	private enum Phase {SITE, WALK, DIG, LAVA, POUR_LAVA, POUR_WATER, WAIT, SCOOP, MINE, PICKUP, DONE}

	private Phase phase = Phase.SITE, afterWalk = Phase.DIG;
	private final Act.Breaker breaker = new Act.Breaker();
	private final Set<BlockPos> bad = new HashSet<>();
	private MoldSite.Site site;
	private int want, start, sites, wait, tries, walkTries;

	@Override
	public String name() {
		return "obsidian_mold";
	}

	/** Buckets of lava and water in play: a routine re-plan mid-pour would leave lava open by our feet. */
	@Override
	public boolean interruptible() {
		return false;
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60 * 8;
		maxChildFails = 4;
		want = argCount(10);
		start = Mc.count("obsidian");
		if (!Mc.dimension().equals("overworld")) {
			fail(Fail.WRONG_PLACE, "water boils away outside the overworld");
			return;
		}
		if (Items2.bestTier("pickaxe") < 3) {
			fail(Fail.NEED_ITEM, "obsidian needs a diamond pickaxe");
			return;
		}
		if (Mc.count("water_bucket") == 0 || Mc.count("bucket") + Mc.count("lava_bucket") == 0) {
			fail(Fail.NEED_ITEM, "the mold needs a water bucket and a second bucket");
		}
	}

	private int made() {
		return Mc.count("obsidian") - start;
	}

	@Override
	protected boolean ownTick() {
		switch (phase) {
			case SITE -> {
				if (++sites > 4) {
					fail(Fail.NO_ROOM, "four mold sites went bad");
					return true;
				}
				site = MoldSite.find(Mc.player().blockPosition(), CastPortalSite.WORLD, bad);
				if (site == null) {
					fail(Fail.NO_ROOM, "no spot for a one-block mold near here");
					return true;
				}
				log("site: stand " + site.stand().toShortString() + " facing " + site.dir());
				walkTo(Phase.DIG);
				return true;
			}
			case WALK -> {
				if (Mc.player().blockPosition().equals(site.stand()) && Mc.player().onGround()) {
					Bari.stop();
					phase = afterWalk;
					tries = 0;
					wait = 0;
					return true;
				}
				if (!Bari.pathing()) {
					if (++walkTries > 12) siteWentBad("couldn't get to the stand");
					else Bari.path(new GoalBlock(site.stand()));
				}
				return true;
			}
			case DIG -> {
				BlockPos p = site.pit();
				if (!CastPortalSite.WORLD.free(p) || CastPortalSite.WORLD.fluid(p)) {
					if (CastPortalSite.WORLD.fluid(p) || CastPortalSite.WORLD.fluidNear(p)) {
						siteWentBad("fluid by the pit");
						return true;
					}
					if (!breaker.tick(p) && breaker.ticks() > 20 * 10) siteWentBad("couldn't dig the pit");
					return true;
				}
				breaker.stop();
				phase = Phase.LAVA;
				return false;
			}
			case LAVA -> {
				if (Mc.count("lava_bucket") > 0) {
					walkTo(Phase.POUR_LAVA);
					return true;
				}
				return false; // next(): fill_bucket lava
			}
			case POUR_LAVA -> {
				if (BucketSkills.isSource(site.pit(), "lava")) {
					phase = Phase.POUR_WATER;
					tries = 0;
					return true;
				}
				if (!atStand()) return true;
				if (ticks % 5 != 0) return true;
				if (++tries > 8) {
					siteWentBad("the lava wouldn't go in the pit");
					return true;
				}
				pour(site.pit(), "lava_bucket");
				return true;
			}
			case POUR_WATER -> {
				if (!atStand()) return true;
				if (ticks % 5 != 0) return true;
				if (Mc.count("water_bucket") == 0) {
					fail(Fail.NEED_ITEM, "lost the water bucket");
					return true;
				}
				if (++tries > 8) {
					siteWentBad("couldn't pour the water");
					return true;
				}
				if (pour(site.water(), "water_bucket")) {
					phase = Phase.WAIT;
					wait = 0;
				}
				return true;
			}
			case WAIT -> {
				// Water runs a block every 5 ticks; the lava hardens once it arrives.
				if (++wait < 20 && !isObsidian(site.pit())) return true;
				phase = Phase.SCOOP;
				wait = 0;
				return true;
			}
			case SCOOP -> {
				if (Mc.count("water_bucket") > 0) {
					// Let the running water drain off before mining, so the block doesn't float away.
					if (!CastPortalSite.WORLD.fluid(site.open()) || ++wait > 40) {
						phase = Phase.MINE;
						wait = 0;
					}
					return true;
				}
				if (wait++ % 5 != 0) return true;
				if (wait > 80) {
					fail(Fail.USE_FAILED, "couldn't scoop the water back up");
					return true;
				}
				Mc.holdItem(s -> Items2.id(s).equals("bucket"));
				Mc.lookAt(Vec3.atCenterOf(site.water()).add(0, 0.3, 0));
				Mc.useItem();
				return true;
			}
			case MINE -> {
				BlockPos p = site.pit();
				if (CastPortalSite.WORLD.free(p)) {
					breaker.stop();
					phase = Phase.PICKUP;
					return false;
				}
				if (BucketSkills.isSource(p, "lava")) {
					// The water never reached it: pour again rather than mine next to open lava.
					phase = Phase.POUR_WATER;
					tries = 0;
					return true;
				}
				if (!breaker.tick(p) && breaker.ticks() > 20 * 20) siteWentBad("couldn't mine the pit");
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	protected Option next() {
		switch (phase) {
			case LAVA -> {
				if (Mc.count("bucket") == 0) {
					fail(Fail.NEED_ITEM, "no empty bucket for the lava");
					return WAIT;
				}
				return new Option("fill_bucket", "lava", "a bucket of lava for the mold");
			}
			case PICKUP -> {
				phase = made() >= want ? Phase.DONE : Phase.LAVA;
				if (phase == Phase.DONE) Facts.report("obsidian_known");
				return new Option("pickup", null, "pick up the obsidian from the pit");
			}
			case DONE -> {
				return null;
			}
			default -> {
				return WAIT;
			}
		}
	}

	@Override
	protected void finish() {
		if (made() >= want) done("made " + made() + " obsidian in the mold");
		else fail(Fail.NO_PROGRESS, "made " + made() + " of " + want + " obsidian");
	}

	@Override
	protected void cleanup() {
		breaker.stop();
		super.cleanup();
	}

	private void walkTo(Phase then) {
		afterWalk = then;
		walkTries = 0;
		phase = Phase.WALK;
		Bari.path(new GoalBlock(site.stand()));
	}

	/** Still standing at S; if we've drifted (water pushes), walk back first. */
	private boolean atStand() {
		if (Mc.player().blockPosition().equals(site.stand())) return true;
		walkTo(phase);
		return false;
	}

	/** Empties the held bucket onto the top face of the block under `into`, if our view lands there. */
	private static boolean pour(BlockPos into, String bucket) {
		Vec3 aim = new Vec3(into.getX() + 0.5, into.getY() + 0.02, into.getZ() + 0.5);
		BlockHitResult hit = BucketSkills.trace(aim.add(0, -0.3, 0), ClipContext.Fluid.NONE);
		if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(into.below())) return false;
		if (!Mc.holdItem(s -> Items2.id(s).equals(bucket))) return false;
		Mc.lookAt(aim);
		return Mc.useItem();
	}

	private static boolean isObsidian(BlockPos p) {
		return Mc.id(Mc.state(p).getBlock()).equals("obsidian");
	}

	private void siteWentBad(String why) {
		log("site " + (site == null ? "?" : site.stand().toShortString()) + " abandoned: " + why);
		breaker.stop();
		Bari.stop();
		if (site != null) bad.add(site.stand());
		phase = Phase.SITE;
	}

	private static void log(String s) {
		io.github.plrlr.autopilot.AutopilotMod.LOGGER.info("[obsidian_mold] {}", s);
	}
}
