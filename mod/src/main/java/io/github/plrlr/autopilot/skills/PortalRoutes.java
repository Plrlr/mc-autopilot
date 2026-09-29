package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wave 2 of docs/skills-40.md: the other ways to a portal (the cast is CastPortalSite). Each is a
 * composite over tested skills with its own safety checks, so the strategist can weigh them:
 *   lava_scout     find a pool big enough to cast from
 *   ruined_portal  loot a ruined portal's chest, mine its obsidian
 *   obsidian_pool  harden a pool and mine 10 obsidian, never one with lava under it
 *   diamond_hunt   safe stairs to diamond depth, then legit branch mining
 *   portal_repair  light a finished frame, or finish ours
 */
public final class PortalRoutes {
	private PortalRoutes() {}

	/** Lava sources the player has seen within 4 blocks of p (a pool's size, as far as we know). */
	static int poolSize(WorldMemory memory, BlockPos p) {
		int n = 0;
		for (WorldMemory.Seen s : memory.all("lava")) if (s.pos().distSqr(p) <= 16) n++;
		return n;
	}

	/** lava_scout: a pool with 10+ seen sources (each frame block takes one) is castable. */
	public static final class LavaScout extends Composite {
		private int explores;

		@Override
		public String name() {
			return "lava_scout";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 5;
			maxChildFails = 3;
		}

		private BlockPos castable() {
			BlockPos best = null;
			int bestN = 9;
			for (WorldMemory.Seen s : memory.all("lava")) {
				int n = poolSize(memory, s.pos());
				if (n > bestN) {
					bestN = n;
					best = s.pos();
				}
			}
			return best;
		}

		@Override
		protected Option next() {
			if (castable() != null) return null;
			if (explores++ >= 3) return null;
			return new Option("explore", "lava", "look for a lava pool big enough to cast from");
		}

		@Override
		protected void finish() {
			BlockPos p = castable();
			if (p == null) {
				fail(Fail.NOT_FOUND, "no pool with 10 seen lava sources");
				return;
			}
			Facts.report("castable_lava");
			done("castable pool at " + p.toShortString() + " (" + poolSize(memory, p) + " sources seen)");
		}
	}

	/** ruined_portal: loot the chest by it, then mine its obsidian (diamond pickaxe) toward our own frame. */
	public static final class RuinedPortal extends Composite {
		private enum Phase {WALK, LOOT, MINE, DONE}

		private Phase phase = Phase.WALK;
		private BlockPos site;
		private Station chest;
		private int looted;

		@Override
		public String name() {
			return "ruined_portal";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 6;
			maxChildFails = 3;
			WorldMemory.Seen s = memory.nearest("ruined_portal");
			if (s == null || !Mc.dimension().equals("overworld")) {
				fail(Fail.NOT_FOUND, "no ruined portal seen");
				return;
			}
			site = s.pos();
		}

		@Override
		protected boolean ownTick() {
			switch (phase) {
				case WALK -> {
					if (Act.flatDist(site) <= 5) {
						Bari.stop();
						WorldMemory.Seen c = memory.nearest("chest");
						phase = c != null && c.pos().distSqr(site) <= 12 * 12
								&& (!io.github.plrlr.autopilot.Tune.on("safety.spawner_room") || !SpawnerRoom.near(memory, c.pos(), 8)) ? Phase.LOOT : Phase.MINE;
						if (phase == Phase.LOOT) chest = new Station("chest", ChestMenu.class, memory, c.pos());
						return true;
					}
					if (!Bari.pathing() && ticks % 40 == 1) Bari.path(new GoalNear(site, 3));
					if (ticks > 20 * 120) fail(Fail.UNREACHABLE, "couldn't reach the ruined portal");
					return true;
				}
				case LOOT -> {
					if (!chest.ready()) {
						chest.tick();
						if (chest.error != null) phase = Phase.MINE; // no loot is fine: the obsidian is the prize
						return true;
					}
					if (ticks % 3 != 0) return true;
					if (ChestOps.takeOne((ChestMenu) Mc.player().containerMenu, ChestOps::loot)) looted++;
					else {
						Mc.player().closeContainer();
						phase = Phase.MINE;
					}
					return true;
				}
				default -> {
					return false;
				}
			}
		}

		@Override
		protected Option next() {
			if (phase != Phase.MINE) return phase == Phase.DONE ? null : WAIT;
			phase = Phase.DONE;
			if (Items2.bestTier("pickaxe") >= 3 && memory.nearest("obsidian") != null && Mc.count("obsidian") < 10)
				return new Option("collect", "obsidian:" + (10 - Mc.count("obsidian")), "mine the ruined portal's obsidian");
			return null;
		}

		@Override
		protected void finish() {
			memory.forget("ruined_portal", site);
			if (Mc.count("obsidian") > 0) Facts.report("obsidian_known");
			done("ruined portal: " + looted + " stacks looted, " + Mc.count("obsidian") + " obsidian");
		}
	}

	/** obsidian_pool: harden a lava pool with water, then mine 10 obsidian, checking under each first. */
	public static final class ObsidianPool extends Composite {
		private final Act.Breaker breaker = new Act.Breaker();
		private final Set<BlockPos> skip = new HashSet<>();
		private BlockPos target;
		private int rounds, walkTries, molds;
		private boolean pickup;

		@Override
		public String name() {
			return "obsidian_pool";
		}

		@Override
		protected void start() {
			// Room for the one-block mold (up to 8 min) after the pool itself.
			timeoutTicks = 20 * 60 * 14;
			maxChildFails = 4;
			if (Items2.bestTier("pickaxe") < 3) fail(Fail.NEED_ITEM, "obsidian needs a diamond pickaxe");
		}

		/** Seen obsidian safe to mine: nothing fluid under it (lava under obsidian drops us in it). */
		private BlockPos nextObsidian() {
			BlockPos best = null;
			double bd = Double.MAX_VALUE;
			for (WorldMemory.Seen s : memory.all("obsidian")) {
				BlockPos p = s.pos();
				if (skip.contains(p) || !Mc.id(Mc.state(p).getBlock()).equals("obsidian")) continue;
				if (!Mc.state(p.below()).getFluidState().isEmpty()) continue;
				double d = p.distSqr(Mc.player().blockPosition());
				if (d < bd && d < 24 * 24) {
					bd = d;
					best = p;
				}
			}
			return best;
		}

		@Override
		protected boolean ownTick() {
			if (target == null) return false;
			if (Mc.free(target)) {
				breaker.stop();
				target = null;
				pickup = true;
				return false;
			}
			if (Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(target)) > Mc.reach() - 0.5) {
				if (!Bari.pathing()) {
					Bari.path(new GoalNear(target, 2));
					if (++walkTries > 10) {
						skip.add(target);
						target = null;
					}
				}
				return true;
			}
			Bari.stop();
			if (!breaker.tick(target) && breaker.ticks() > 20 * 15) {
				breaker.stop();
				skip.add(target);
				target = null;
			}
			return true;
		}

		@Override
		protected Option next() {
			if (Mc.count("obsidian") >= 10) return null;
			if (pickup) {
				pickup = false;
				return new Option("pickup", null, "pick up the obsidian");
			}
			target = nextObsidian();
			if (target != null) {
				walkTries = 0;
				return WAIT;
			}
			// Nothing safe to mine: hardening a deep lake leaves obsidian over lava (it burns when
			// mined). With two buckets, make it a block at a time in a pit instead (obsidian_mold).
			if (io.github.plrlr.autopilot.Tune.on("deep.mold") && molds < 2 && Mc.count("water_bucket") > 0
					&& Mc.count("water_bucket") + Mc.count("bucket") + Mc.count("lava_bucket") >= 2) {
				molds++;
				return new Option("obsidian_mold", String.valueOf(10 - Mc.count("obsidian")), "make the rest in a pit, on solid ground");
			}
			if (rounds++ >= 3) return null;
			if (Mc.count("water_bucket") == 0) return new Option("fill_bucket", "water", "water to harden the pool");
			return new Option("make_obsidian", "obsidian", "harden more of the pool");
		}

		@Override
		protected void finish() {
			if (Mc.count("obsidian") >= 10) done("have " + Mc.count("obsidian") + " obsidian");
			else fail(Fail.NO_PROGRESS, "only " + Mc.count("obsidian") + " obsidian after the mold and 3 rounds");
		}

		@Override
		protected void cleanup() {
			breaker.stop();
			// Given up on this pool (not just interrupted, not short of a tool): the planner looks for another.
			Skill.Result r = result();
			if (r != null && !r.ok() && r.code() != Fail.INTERRUPTED && r.code() != Fail.NEED_ITEM)
				io.github.plrlr.autopilot.plan.PortalPlan.poolFailed(Mc.player().blockPosition());
			super.cleanup();
		}
	}

	/** diamond_hunt[:n]: safe stairs down to -54, then legit branch mining for n diamonds. */
	public static final class DiamondHunt extends Composite {
		private int want, tries, stairs;

		@Override
		public String name() {
			return "diamond_hunt";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 12;
			maxChildFails = 3;
			want = argCount(3);
			if (Items2.bestTier("pickaxe") < 2) fail(Fail.NEED_ITEM, "diamonds need an iron pickaxe");
			if (!Mc.dimension().equals("overworld")) fail(Fail.WRONG_PLACE, "diamonds are in the overworld");
		}

		@Override
		protected Option next() {
			if (Mc.count("diamond") >= want) return null;
			// Stairs twice at most: lava or drops on every side at some depth fail them each time,
			// and collect finds its own way down (Baritone's legit mining digs to the ore's depth).
			if (Mc.player().getBlockY() > -50 && stairs++ < 2) return new Option("stair_down", "-54", "safe stairs to diamond depth");
			if (tries++ >= 3) return null;
			return new Option("collect", "diamond:" + (want - Mc.count("diamond")), "branch-mine for diamonds in sight");
		}

		@Override
		protected void finish() {
			if (Mc.count("diamond") >= want) done("have " + Mc.count("diamond") + " diamonds");
			else fail(Fail.NOT_FOUND, "found " + Mc.count("diamond") + " of " + want + " diamonds");
		}
	}

	/** portal_repair: light a complete frame we can see; else finish our own frame (build_portal placed). */
	public static final class PortalRepair extends Composite {
		private boolean tried;
		private List<BlockPos> lightSpots;
		private int li;

		@Override
		public String name() {
			return "portal_repair";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 120;
			if (memory.nearest("nether_portal") != null) {
				done("the portal is already lit");
				return;
			}
			if (Mc.count("flint_and_steel") == 0) {
				fail(Fail.NEED_ITEM, "no flint and steel to light it");
				return;
			}
			lightSpots = bottoms();
		}

		/** Obsidian with air above and obsidian 4 or 5 above that: a frame's bottom row, lightable from the top face. */
		private List<BlockPos> bottoms() {
			List<BlockPos> out = new ArrayList<>();
			for (WorldMemory.Seen s : memory.all("obsidian")) {
				BlockPos p = s.pos();
				if (Mc.free(p.above()) && (isObsidian(p.above(4)) || isObsidian(p.above(5)))) out.add(p);
			}
			return out;
		}

		private static boolean isObsidian(BlockPos p) {
			return Mc.id(Mc.state(p).getBlock()).equals("obsidian");
		}

		@Override
		protected boolean ownTick() {
			if (memory.nearest("nether_portal") != null || Mc.id(Mc.state(lastLit()).getBlock()).equals("nether_portal")) {
				Facts.report("portal_known");
				done("portal lit");
				return true;
			}
			if (li >= lightSpots.size()) return false;
			BlockPos p = lightSpots.get(li);
			if (Mc.player().getEyePosition().distanceTo(Vec3.atCenterOf(p)) > Mc.reach() - 0.5) {
				if (!Bari.pathing()) Bari.path(new GoalNear(p, 2));
				if (ticks > 20 * 60) li = lightSpots.size();
				return true;
			}
			Bari.stop();
			if (ticks % 10 == 0 && Mc.holdItem(s -> Items2.id(s).equals("flint_and_steel"))) {
				Mc.useOn(p, Direction.UP);
				li++;
			}
			return true;
		}

		private BlockPos lastLit() {
			return li > 0 && li <= lightSpots.size() ? lightSpots.get(li - 1).above() : Mc.player().blockPosition();
		}

		@Override
		protected Option next() {
			if (tried) return null;
			tried = true;
			if (Mc.count("obsidian") > 0) return new Option("build_portal", "placed", "finish our frame with carried obsidian and light it");
			return null;
		}

		@Override
		protected void finish() {
			if (memory.nearest("nether_portal") != null) {
				Facts.report("portal_known");
				done("portal lit");
			} else fail(Fail.NO_PROGRESS, "no lightable frame");
		}
	}
}
