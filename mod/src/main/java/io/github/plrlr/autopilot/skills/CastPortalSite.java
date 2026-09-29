package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * cast_portal: the cast portal, with the site made instead of found.
 *
 * Evidence (generation 45 lava starts): build_portal failed NO_ROOM "no flat open ground for a
 * portal near the lava" in 4 of 5 runs, every try; the run that found a flat spot cast and lit
 * its portal. The casting works; finding a site is what fails. So:
 *
 *   APPROACH  walk to within 8 blocks of the remembered lava pool
 *   PICK      the cheapest site 3-8 blocks away (PortalSite: fewest blocks to dig and fill,
 *             nothing dug next to water or lava)
 *   DIG       clear the frame's space and the standing rows, block by block, from within reach
 *   FLOOR     lay a block wherever the frame or the standing rows lack ground
 *   CAST      hand the site to CastPortal (build_portal) and let it cast and light
 * A site that turns out bad (a block that won't break, fluid appearing) is set aside and the next
 * cheapest is picked, up to 3 sites.
 */
public final class CastPortalSite extends Composite {
	private enum Phase {APPROACH, PICK, MAKE, DIG, FLOOR, CAST, CLIMB, DONE}

	private Phase phase = Phase.APPROACH;
	private final Act.Breaker breaker = new Act.Breaker();
	private final Set<BlockPos> bad = new HashSet<>();
	private PortalSite.Plan plan;
	private BlockPos pool;
	private int idx, sites, stuck, placeTries;
	private boolean climbed;
	/**
	 * dig_portal: the same room, dug for a frame of 10 carried obsidian (the diamond route) instead
	 * of a cast. No lava pool needed; underground there is never flat open ground, so the room
	 * is always dug (a site already open costs nothing and wins the search).
	 */
	private final boolean placed;

	public CastPortalSite() {
		this(false);
	}

	public CastPortalSite(boolean placed) {
		this.placed = placed;
	}
	private PortalSiteMaker maker;

	static final PortalSite.Probe WORLD = new PortalSite.Probe() {
		@Override
		public boolean free(BlockPos p) {
			return Mc.free(p);
		}

		@Override
		public boolean solid(BlockPos p) {
			return Mc.solid(p);
		}

		@Override
		public boolean fluid(BlockPos p) {
			return !Mc.state(p).getFluidState().isEmpty();
		}

		@Override
		public boolean fluidNear(BlockPos p) {
			for (Direction d : Direction.values()) if (!Mc.state(p.relative(d)).getFluidState().isEmpty()) return true;
			return false;
		}

		@Override
		public boolean hard(BlockPos p) {
			String id = Mc.id(Mc.state(p).getBlock());
			return id.equals("bedrock") || id.contains("obsidian") || id.equals("reinforced_deepslate") || Mc.isInteractive(p);
		}
	};

	@Override
	public String name() {
		return placed ? "dig_portal" : "cast_portal";
	}

	/**
	 * Long work in one place: routine re-checks (eat, refill a bucket) don't break it off; danger
	 * still does. In the first drill the planner interrupted it twice after two cast blocks.
	 */
	@Override
	public boolean interruptible() {
		return false;
	}

	@Override
	protected void start() {
		timeoutTicks = 20 * 60 * 12;
		maxChildFails = 2;
		if (placed) {
			if (!Mc.dimension().equals("overworld")) {
				fail(Fail.WRONG_PLACE, "build the portal in the overworld");
				return;
			}
			if (Mc.count("obsidian") + PortalSkills.placedFrameObsidian() < 10 || Mc.count("flint_and_steel") == 0) {
				fail(Fail.NEED_ITEM, "the frame needs 10 obsidian and flint and steel");
				return;
			}
			phase = PortalSkills.frameInProgress() ? Phase.CAST : Phase.PICK;
			return;
		}
		WorldMemory.Seen lava = memory.nearest("lava");
		if (lava == null) {
			fail(Fail.NOT_FOUND, "no known lava pool to cast from");
			return;
		}
		if (Mc.count("bucket") + Mc.count("water_bucket") + Mc.count("lava_bucket") < 2 || Mc.count("flint_and_steel") == 0) {
			fail(Fail.NEED_ITEM, "the cast needs two buckets and flint and steel");
			return;
		}
		pool = lava.pos();
		// A half-built frame is progress: finish it. (The first drill cast two blocks, was interrupted,
		// and the retry rejected its own site for the obsidian and water now beside it.)
		if (CastPortal.siteInProgress()) {
			log("resuming the frame under way");
			phase = Phase.CAST;
		}
	}

	@Override
	protected boolean ownTick() {
		switch (phase) {
			case APPROACH -> {
				if (Act.flatDist(pool) <= 8) {
					Bari.stop();
					phase = Phase.PICK;
					return true;
				}
				if (!Bari.pathing() && ticks % 40 == 1) Bari.path(new GoalNear(pool, 6));
				if (ticks > 20 * 90) fail(Fail.UNREACHABLE, "couldn't get near the lava pool");
				return true;
			}
			case PICK -> {
				if (!placed && io.github.plrlr.autopilot.Tune.on("portal.site_maker")) {
					maker = new PortalSiteMaker(pool);
					phase = Phase.MAKE;
					return true;
				}
				if (sites++ >= 3) {
					fail(Fail.NO_ROOM, "three portal sites went bad");
					return true;
				}
				plan = PortalSite.best(Mc.player().blockPosition(), pool, WORLD, bad);
				if (plan == null && placed && !climbed) {
					// Nothing safe to dig here (lava and water all around, down at the lava caves):
					// the frame needs no lava, so take it up to the surface and build there.
					climbed = true;
					sites = 0;
					phase = Phase.CLIMB;
					return false;
				}
				if (plan == null) {
					fail(Fail.NO_ROOM, "no site near the lava can be dug out safely");
					return true;
				}
				log("site " + plan.origin().toShortString() + " along " + plan.along() + ": dig " + plan.dig().size() + ", floor " + plan.floor().size());
				idx = 0;
				phase = Phase.DIG;
				return true;
			}
			case MAKE -> {
				maker.tick();
				if (maker.failure() != null) { fail(Fail.NO_ROOM, maker.failure()); return true; }
				PortalSite.Plan made = maker.site();
				if (made != null) {
					CastPortal.useSite(made.origin(), made.along());
					log("site ready at " + made.origin().toShortString());
					phase = Phase.CAST;
					return false;
				}
				return true;
			}
			case DIG -> {
				if (idx >= plan.dig().size()) {
					breaker.stop();
					idx = 0;
					phase = Phase.FLOOR;
					return true;
				}
				BlockPos c = plan.dig().get(idx);
				if (WORLD.fluid(c) || WORLD.fluidNear(c) && !Mc.free(c)) {
					siteWentBad("fluid by " + c.toShortString());
					return true;
				}
				if (Mc.free(c)) {
					idx++;
					stuck = 0;
					return true;
				}
				if (!inReach(c)) {
					breaker.stop();
					walkNear(c);
					return true;
				}
				Bari.stop();
				if (breaker.tick(c)) idx++;
				else if (breaker.ticks() > 20 * 10) siteWentBad("couldn't break " + c.toShortString());
				return true;
			}
			case FLOOR -> {
				if (idx >= plan.floor().size()) {
					if (placed ? !PortalSkills.siteFits(plan.origin(), plan.along())
							: !CastGeometry.fits(plan.origin(), plan.along(), PortalSite.WALL_H)) {
						siteWentBad("site still doesn't fit after the work");
						return true;
					}
					if (placed) PortalSkills.useSite(plan.origin(), plan.along());
					else CastPortal.useSite(plan.origin(), plan.along());
					log("site ready at " + plan.origin().toShortString());
					phase = Phase.CAST;
					return false;
				}
				BlockPos f = plan.floor().get(idx);
				if (Mc.solid(f)) {
					idx++;
					placeTries = 0;
					return true;
				}
				if (!inReach(f)) {
					walkNear(f);
					return true;
				}
				Bari.stop();
				if (ticks % 4 == 0 && !Act.place(f) && ++placeTries > 10) siteWentBad("couldn't lay floor at " + f.toShortString());
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private void walkNear(BlockPos p) {
		if (!Bari.pathing()) {
			Bari.path(new GoalNear(p, 2));
			if (++stuck > 12) siteWentBad("couldn't get within reach of " + p.toShortString());
		}
	}

	private void siteWentBad(String why) {
		log("site " + plan.origin().toShortString() + " abandoned: " + why);
		breaker.stop();
		Bari.stop();
		bad.add(plan.origin());
		stuck = 0;
		phase = Phase.PICK;
	}

	private static boolean inReach(BlockPos p) {
		return Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(p)) <= Mc.reach() - 0.5;
	}

	@Override
	protected Option next() {
		if (phase == Phase.CLIMB) {
			phase = Phase.PICK;
			return new Option("goto", "surface", "no room for the frame down here: build it up top");
		}
		if (phase != Phase.CAST) return phase == Phase.DONE ? null : WAIT;
		phase = Phase.DONE;
		if (placed) return new Option("build_portal", "placed", "build the frame in the room we dug and light it");
		return new Option("build_portal", null, "cast the portal on the site we made");
	}

	@Override
	protected void finish() {
		Skill.Result r = lastResult();
		if (r != null && r.ok()) {
			Facts.report("portal_known");
			done(r.detail());
		} else fail(r == null || r.code() == null ? Fail.NO_PROGRESS : r.code(), r == null ? "no cast" : r.detail());
	}

	@Override
	protected void cleanup() {
		if (maker != null) maker.stop();
		breaker.stop();
		super.cleanup();
	}

	private static void log(String s) {
		io.github.plrlr.autopilot.AutopilotMod.LOGGER.info("[portal_site] {}", s);
	}
}
