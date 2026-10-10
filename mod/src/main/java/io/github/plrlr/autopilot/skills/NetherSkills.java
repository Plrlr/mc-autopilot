package io.github.plrlr.autopilot.skills;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import io.github.plrlr.autopilot.Exposure;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.PieChart;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Nether stage: find a fortress, then get blaze rods there.
 *
 * fortress find: walk long straight legs through the Nether and look around while walking.
 * Fortresses are big dark brick structures a player spots from far away, but WorldMemory's close
 * scan only reaches 16 blocks; here a sweep of view rays (what the camera could see, no x-ray)
 * remembers nether bricks and spawners out to 96 blocks.
 *
 * fortress blazes:n: blazes hover and shoot, so chasing them fails (attack gives up after 8 s
 * without getting closer). Instead stand at the spawner (or in the fortress), shield up while
 * they're at range, and hit the ones that drift into reach. Ends when the bag holds n rods.
 */
public final class NetherSkills {
	private NetherSkills() {}

	/** A brick with remembered bricks beside it in both directions: a floor, not a wall's top line. */
	static boolean floorPatch(BlockPos p, java.util.Set<BlockPos> known) {
		return (known.contains(p.east()) || known.contains(p.west())) && (known.contains(p.north()) || known.contains(p.south()));
	}

	/** Blocks only fortresses are made of (bastions use blackstone, so they don't count). */
	static boolean fortressBlock(String id) {
		return id.equals("nether_bricks") || id.startsWith("nether_brick_");
	}

	/**
	 * View ray n of a sweep: yaw goes round in 7.5° steps (48 per turn), and each turn uses the
	 * next pitch band, from slightly up to well down, so ~8 ticks cover every direction once.
	 * Returns {yaw, pitch} in degrees (Minecraft: pitch > 0 looks down).
	 */
	static float[] sweepRay(int n) {
		float yaw = (n % 48) * 7.5f;
		float[] pitches = {-12f, 0f, 10f, 22f, 35f};
		float pitch = pitches[(n / 48) % pitches.length];
		return new float[]{yaw, pitch};
	}

	/** Casts `rays` view rays of the sweep from the eye; remembers fortress blocks and spawners hit. */
	static int look(WorldMemory memory, int from, int rays, double range) {
		LocalPlayer pl = Mc.player();
		Vec3 eye = pl.getEyePosition();
		int n = from;
		for (int i = 0; i < rays; i++, n++) {
			float[] yp = sweepRay(n);
			Vec3 dir = Vec3.directionFromRotation(yp[1], yp[0]);
			// Lava is opaque: a lava sea hides what's behind it, so fluids stop the ray too.
			BlockHitResult r = pl.level().clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.VISUAL,
					ClipContext.Fluid.ANY, pl));
			if (r.getType() != HitResult.Type.BLOCK) continue;
			BlockPos p = r.getBlockPos();
			String id = Mc.id(Mc.state(p).getBlock());
			if (fortressBlock(id)) memory.remember("nether_bricks", p.immutable(), id);
			else if (id.equals("spawner")) memory.remember("spawner", p.immutable(), id);
		}
		return n;
	}

	/**
	 * The spawner to fight at. A spawner seen in the Nether was taken for the blaze spawner, but
	 * bastions hold magma cube spawners. Gene fortress.spawner_check: only one with fortress bricks
	 * within 6 blocks (a fortress spawner stands on a brick platform) or a blaze seen near it.
	 */
	public static WorldMemory.Seen blazeSpawner(WorldMemory memory, Perception seen) {
		WorldMemory.Seen first = memory.nearest("spawner");
		if (first == null) return null;
		List<WorldMemory.Seen> bricks = memory.all("nether_bricks");
		WorldMemory.Seen best = null;
		double bd = Double.MAX_VALUE;
		BlockPos me = Mc.player().blockPosition();
		for (WorldMemory.Seen s : memory.all("spawner")) {
			boolean fortress = false;
			for (WorldMemory.Seen b : bricks) if (b.pos().distSqr(s.pos()) <= 36) { fortress = true; break; }
			if (!fortress && seen != null)
				for (Perception.Seen m : seen.mobs) if (m.type().equals("blaze") && m.entity() != null
						&& m.entity().blockPosition().distSqr(s.pos()) <= 16 * 16) { fortress = true; break; }
			double d = s.pos().distSqr(me);
			if (fortress && d < bd) {
				bd = d;
				best = s;
			}
		}
		if (best == first) return first;
		// The nearest spawner isn't a corroborated blaze spawner: the old rule would still go there.
		return Exposure.mark("fortress.spawner_check", "a spawner with no fortress bricks or blaze near it") ? best : first;
	}

	public static final class Fortress extends Skill {
		private static final int LEG = 200;
		private static final double VIEW = 96;

		private boolean findMode;
		private int want;
		private int ray;
		private double startX, startZ;
		/** Pie-ray: turning in steps, reading the spawner slice at each (-1 when not sweeping). */
		private static final int SWEEP_STEPS = 12;
		private int sweep = -1;
		private int legStart;
		private final int[] spawnerSlice = new int[SWEEP_STEPS];

		// blazes mode. Tried anchors outlive one run of the skill: a reflex or an eat restarts it,
		// and a fresh list sent it back to the same empty corner (loop 0355).
		private BlockPos anchor;
		private static final List<BlockPos> visitedAnchors = new ArrayList<>();
		/** Gene fortress.anchor_scope: when each anchor was tried, and the life and dimension they belong to. */
		private static final java.util.Map<BlockPos, Long> visitedAt = new java.util.HashMap<>();
		private static String visitedScope = "";
		private static final long ANCHOR_RETRY_MS = 120_000;
		/** Approaching a wall face (no floor known yet): stand off it instead of walking into it. */
		private boolean approach;
		private int sinceBlaze;
		private int rodsBefore;
		private boolean walking;

		// Baritone settings changed while this skill runs, restored in cleanup.
		private Boolean savedItemSaver, savedAllowPlace;
		private Double savedBreakPenalty, savedSpawnerAvoid;
		private List<net.minecraft.world.level.block.Block> savedNoBreak;

		/**
		 * The first test walked a 200-block leg through solid netherrack near the roof and wore the
		 * iron pickaxe out; without it the bot dug by hand. Here: stop using a tool before it
		 * breaks, and make digging costly so open caves and corridors win over tunnels (in blazes
		 * mode Baritone dug 26 bricks out of the fortress and sank to y 41 under it, where no blaze
		 * can be seen; loop 0355). In blazes mode, Baritone's spawner avoidance (16 blocks) would
		 * keep us from the one place blazes come to, so it's switched off.
		 */
		private void tuneBaritone() {
			Settings st = BaritoneAPI.getSettings();
			savedItemSaver = st.itemSaver.value;
			st.itemSaver.value = true;
			// No bridging or pillaring here: Baritone planned 80-block sneak-bridges over the Nether's
			// drops, and a fortress search died to a fall 10 s in. Paths it can't walk fail and turn.
			savedAllowPlace = st.allowPlace.value;
			st.allowPlace.value = false;
			savedBreakPenalty = st.blockBreakAdditionalPenalty.value;
			st.blockBreakAdditionalPenalty.value = 12.0;
			if (!findMode) {
				savedSpawnerAvoid = st.mobSpawnerAvoidanceCoefficient.value;
				st.mobSpawnerAvoidanceCoefficient.value = 1.0;
				// Gene fortress.no_tunnel: the penalty alone didn't stop it (gen176 eval-g159-2 carried 19
				// bricks it dug while fighting there). Baritone may not break fortress blocks at all, so it
				// walks the bridges and doorways, where blazes can be seen and reached.
				if (Exposure.mark("fortress.no_tunnel", "fighting at a fortress: bricks could be dug")) {
					savedNoBreak = st.blocksToDisallowBreaking.value;
					List<net.minecraft.world.level.block.Block> keep = new ArrayList<>(savedNoBreak);
					keep.addAll(List.of(net.minecraft.world.level.block.Blocks.NETHER_BRICKS,
							net.minecraft.world.level.block.Blocks.NETHER_BRICK_FENCE, net.minecraft.world.level.block.Blocks.NETHER_BRICK_STAIRS,
							net.minecraft.world.level.block.Blocks.NETHER_BRICK_SLAB, net.minecraft.world.level.block.Blocks.NETHER_BRICK_WALL,
							net.minecraft.world.level.block.Blocks.SPAWNER));
					st.blocksToDisallowBreaking.value = keep;
				}
			}
		}

		private void restoreBaritone() {
			Settings st = BaritoneAPI.getSettings();
			if (savedItemSaver != null) st.itemSaver.value = savedItemSaver;
			if (savedAllowPlace != null) st.allowPlace.value = savedAllowPlace;
			if (savedBreakPenalty != null) st.blockBreakAdditionalPenalty.value = savedBreakPenalty;
			if (savedSpawnerAvoid != null) st.mobSpawnerAvoidanceCoefficient.value = savedSpawnerAvoid;
			if (savedNoBreak != null) st.blocksToDisallowBreaking.value = savedNoBreak;
		}

		@Override
		public String name() {
			return "fortress";
		}

		/** A fight in a fortress shouldn't be dropped for a routine re-check; danger still interrupts. */
		@Override
		public boolean interruptible() {
			return findMode;
		}

		@Override
		protected void start() {
			if (!Mc.dimension().equals("the_nether")) {
				fail(Fail.WRONG_PLACE, "fortresses are in the Nether");
				return;
			}
			findMode = !"blazes".equals(argName());
			tuneBaritone();
			LocalPlayer pl = Mc.player();
			if (findMode) {
				timeoutTicks = 20 * 240;
				if (memory.nearest("nether_bricks") != null) {
					fail(Fail.ALREADY_DONE, "a fortress is already known");
					return;
				}
				visitedAnchors.clear();
				visitedAt.clear();
				startX = pl.getX();
				startZ = pl.getZ();
				if (io.github.plrlr.autopilot.Tune.on("nether.pie_chart")) sweep = 0;
				else walkLeg(null);
				return;
			}
			timeoutTicks = 20 * 300;
			want = argCount(6);
			rodsBefore = Mc.count("blaze_rod");
			if (rodsBefore >= want) {
				fail(Fail.ALREADY_DONE, "already have " + rodsBefore + " blaze rods");
				return;
			}
			anchor = pickAnchor();
			if (anchor == null && Perception.look(32).nearest("blaze") == null) {
				fail(Fail.NOT_FOUND, "no fortress or blaze known");
				return;
			}
			if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
			CombatSkills.holdWeapon();
			if (anchor != null) walkTo(anchor);
		}

		/** The spawner if we know one (blazes appear right there), else the nearest fortress block not yet tried. */
		private BlockPos pickAnchor() {
			scopeAnchors();
			approach = false;
			WorldMemory.Seen s = blazeSpawner(memory, Perception.look(32));
			if (s != null && !tried(s.pos(), 0)) return s.pos();
			LocalPlayer pl = Mc.player();
			// Floors and bridge tops first: a brick in a wall or under the floor made Baritone tunnel
			// into the fortress, out of sight of every blaze. Seen from afar, though, a fortress is
			// only wall faces (the fight found no anchor at all, NOT_FOUND x3 right after "saw a
			// fortress 61 blocks away"), so then walk to the nearest brick; floors come into view.
			BlockPos best = null, bestAny = null, bestPatch = null;
			double bd = Double.MAX_VALUE, bdAny = Double.MAX_VALUE, bdPatch = Double.MAX_VALUE;
			List<WorldMemory.Seen> bricks = memory.all("nether_bricks");
			java.util.Set<BlockPos> known = new java.util.HashSet<>();
			for (WorldMemory.Seen b : bricks) known.add(b.pos());
			for (WorldMemory.Seen b : bricks) {
				if (tried(b.pos(), 20 * 20)) continue;
				double d = b.pos().distSqr(pl.blockPosition());
				if (d < bdAny) {
					bdAny = d;
					bestAny = b.pos();
				}
				if (d < bd && Mc.free(b.pos().above()) && Mc.free(b.pos().above(2))) {
					bd = d;
					best = b.pos();
					if (d < bdPatch && floorPatch(b.pos(), known)) {
						bdPatch = d;
						bestPatch = b.pos();
					}
				}
			}
			BlockPos old = best != null ? best : bestAny;
			// Gene fortress.floor_anchor: a floor, not the top of a wall (bricks with free space above
			// in a line, so the old rule sent Baritone up or into walls); with no floor in view, stand
			// off the nearest wall and look, rather than walking into it.
			BlockPos neu = bestPatch != null ? bestPatch : bestAny;
			if (neu != null && !neu.equals(old)
					&& Exposure.mark("fortress.floor_anchor", bestPatch != null ? "a floor patch instead of a wall top" : "no floor in view: stand off the wall")) {
				approach = bestPatch == null;
				return neu;
			}
			return old;
		}

		/**
		 * An anchor tried within `radius` (squared; 0 = that block). Gene fortress.anchor_scope: only
		 * tries in the last 2 minutes count. The list was static and cleared only when a find started,
		 * which never happens while bricks are known, so once every anchor was tried the fight failed
		 * NOT_FOUND at once for the rest of the game (113 times in the audit's Nether first lives),
		 * though blazes spawn again all over a fortress.
		 */
		private boolean tried(BlockPos p, double radius) {
			long now = System.currentTimeMillis();
			for (BlockPos v : visitedAnchors) {
				if (radius == 0 ? !v.equals(p) : v.distSqr(p) >= radius) continue;
				Long at = visitedAt.get(v);
				if (at != null && now - at > ANCHOR_RETRY_MS
						&& Exposure.mark("fortress.anchor_scope", "an anchor tried over 2 minutes ago is open again")) continue;
				return true;
			}
			return false;
		}

		private static void visit(BlockPos p) {
			visitedAnchors.add(p);
			visitedAt.put(p, System.currentTimeMillis());
		}

		/** Gene fortress.anchor_scope: tries belong to one life in one dimension. */
		private static void scopeAnchors() {
			var a = io.github.plrlr.autopilot.AutopilotMod.instance();
			String scope = Mc.dimension() + "/" + (a == null ? 0 : a.progress.deaths());
			if (scope.equals(visitedScope)) return;
			visitedScope = scope;
			if (!visitedAnchors.isEmpty() && Exposure.mark("fortress.anchor_scope", "a new life or dimension: " + visitedAnchors.size() + " old anchors")) {
				visitedAnchors.clear();
				visitedAt.clear();
			}
		}

		private void walkTo(BlockPos p) {
			walking = true;
			Bari.path(new GoalNear(p, approach ? 6 : 3));
		}

		@Override
		protected void tick() {
			ray = look(memory, ray, 16, VIEW);
			if (findMode) tickFind();
			else tickBlazes();
		}

		/** A leg toward the pie chart's spawners (yaw), else toward new ground. */
		private void walkLeg(Float yaw) {
			if (yaw != null) {
				double r = Math.toRadians(yaw);
				Bari.path(new GoalXZ((int) (startX - Math.sin(r) * LEG), (int) (startZ + Math.cos(r) * LEG)));
				return;
			}
			// Fortresses and bastions share 432-block regions: long legs reach new regions,
			// short ones keep circling the same one.
			int h = memory.exploreHeading(startX, startZ, LEG);
			double a = h * Math.PI / 4;
			Bari.path(new GoalXZ((int) (startX + Math.cos(a) * LEG), (int) (startZ + Math.sin(a) * LEG)));
		}

		/** Turn a full circle in 30-degree steps and read the spawner slice at each, like a runner. */
		private boolean tickSweep() {
			if (sweep < 0) return false;
			if (ticks % 3 != 0) return true;
			float yaw = sweep * (360f / SWEEP_STEPS);
			if (sweep > 0) spawnerSlice[sweep - 1] = PieChart.spawnersInView(Mc.player().getYRot());
			if (sweep == SWEEP_STEPS) {
				int best = 0;
				for (int i = 1; i < SWEEP_STEPS; i++) if (spawnerSlice[i] > spawnerSlice[best]) best = i;
				sweep = -1;
				Float toward = spawnerSlice[best] > 0 ? best * (360f / SWEEP_STEPS) : null;
				if (toward != null) Mc.say("Pie chart shows spawners that way; heading there.");
				walkLeg(toward);
				legStart = ticks;
				return true;
			}
			Mc.player().setYRot(yaw);
			sweep++;
			return true;
		}

		private void tickFind() {
			if (tickSweep()) return;
			if (ticks % 10 != 0) return;
			WorldMemory.Seen b = memory.nearest("nether_bricks");
			LocalPlayer pl = Mc.player();
			if (b != null) {
				done("saw a fortress " + Math.round(Math.sqrt(b.pos().distSqr(pl.blockPosition()))) + " blocks away");
				return;
			}
			if (ticks - legStart > 20 && !Bari.pathing()) {
				double moved = Math.hypot(pl.getX() - startX, pl.getZ() - startZ);
				if (moved < 16) {
					memory.markBadAhead(startX, startZ, LEG);
					fail(Fail.UNREACHABLE, "couldn't make headway that way (lava or cliffs); will turn");
				} else {
					done("walked " + Math.round(moved) + " blocks, no fortress in view yet");
				}
			}
		}

		private void tickBlazes() {
			LocalPlayer pl = Mc.player();
			int rods = Mc.count("blaze_rod");
			if (rods >= want) {
				done("have " + rods + " blaze rods (+" + (rods - rodsBefore) + ")");
				return;
			}
			// A better anchor came into view (the spawner itself): go there instead.
			WorldMemory.Seen spawner = blazeSpawner(memory, null);
			if (spawner != null && !spawner.pos().equals(anchor) && !tried(spawner.pos(), 0)) {
				anchor = spawner.pos();
				approach = false;
				walkTo(anchor);
			}
			Perception seen = Perception.look(32);
			Perception.Seen blaze = reachableBlaze(seen);
			var keyUse = Mc.mc().options.keyUse;
			if (recover(pl, blaze, seen, keyUse)) return;
			if (result() != null) return;
			// Wither skeletons and other fortress mobs walk up and hit us: fight them here. Leaving
			// them to the generic reflex restarted this skill every time (loop 0355).
			Perception.Seen close = seen.nearestHostile();
			boolean closeMob = close != null && close.dist() <= 3.2 && !close.type().equals("blaze");
			// Between blazes, keep hunger at 18+ so health comes back between hits: below 18 it
			// doesn't regenerate at all (loop 0341 fought at hunger 15 with 16 steaks in the bag).
			// Gene nether.recover_bounded: not while a mob is at arm's length (eating stands still).
			if (pl.getFoodData().getFoodLevel() < 18 && (blaze == null || blaze.dist() > 6)
					&& !(closeMob && io.github.plrlr.autopilot.Tune.on("nether.recover_bounded")) && eat(pl, keyUse)) return;
			stopEating(keyUse);
			if (closeMob) {
				keyUse.setDown(false);
				if (Bari.pathing()) Bari.stop();
				walking = false;
				Mc.lookAt(close.entity().getBoundingBox().getCenter());
				if (pl.getAttackStrengthScale(0.5f) >= 0.95f) {
					Mc.mc().gameMode.attack(pl, close.entity());
					Mc.swing();
				}
				return;
			}
			if (blaze != null) {
				// Chase and hit. Waiting at the spawner with the shield up (the first design) only
				// blocked for 105 s: blazes keep their distance. The plain chase killed one every ~6 s.
				sinceBlaze = 0;
				keyUse.setDown(false);
				Entity e = blaze.entity();
				if (blaze.dist() <= 3.2) {
					if (Bari.pathing()) Bari.stop();
					walking = false;
					Mc.lookAt(e.getBoundingBox().getCenter());
					if (pl.getAttackStrengthScale(0.5f) >= 0.95f) {
						Mc.mc().gameMode.attack(pl, e);
						Mc.swing();
					}
					return;
				}
				// Rods first if one is lying close by; otherwise close in on the blaze.
				if (blaze.dist() > 6 || !rodNearby(pl, seen)) {
					trackChase(blaze);
					if (ticks % 10 == 0) Bari.path(new GoalNear(e.blockPosition(), 2));
					walking = true;
					return;
				}
			} else {
				keyUse.setDown(false);
				sinceBlaze++;
			}
			// Rods on the ground nearby: pick them up before anything else.
			if (ticks % 10 == 0) {
				for (ItemEntity it : seen.items) {
					if (Items2.id(it.getItem()).equals("blaze_rod") && it.distanceTo(pl) < 12) {
						Bari.path(new GoalNear(it.blockPosition(), 0));
						walking = true;
						return;
					}
				}
			}
			if (walking && !Bari.pathing()) walking = false;
			// No blaze for 30 s here: try another part of the fortress (blazes spawn all over it).
			if (sinceBlaze > 20 * 30) {
				sinceBlaze = 0;
				if (anchor != null) visit(anchor);
				visit(pl.blockPosition().immutable());
				anchor = pickAnchor();
				if (anchor == null) anchor = fallbackAnchor(pl);
				if (anchor == null) {
					if (rods > rodsBefore) done("got " + (rods - rodsBefore) + " blaze rods; no more blazes found here");
					else fail(Fail.NOT_FOUND, "no blazes in the parts of the fortress we know");
					return;
				}
				walkTo(anchor);
			}
		}

		/**
		 * Every part of the fortress we know has been tried. Blazes respawn, so go back and wait at
		 * the blaze spawner, else at the spot tried longest ago (we stood there, so it is reachable).
		 */
		private BlockPos fallbackAnchor(LocalPlayer pl) {
			approach = false;
			WorldMemory.Seen s = blazeSpawner(memory, null);
			if (s != null && s.pos().distSqr(pl.blockPosition()) > 4 * 4) return s.pos();
			BlockPos best = null;
			long bestAt = Long.MAX_VALUE;
			for (BlockPos v : visitedAnchors) {
				if (v.distSqr(pl.blockPosition()) < 8 * 8) continue;
				Long at = visitedAt.get(v);
				long t = at == null ? 0 : at;
				if (t < bestAt) {
					bestAt = t;
					best = v;
				}
			}
			if (best == null && s != null) return s.pos();
			return best;
		}

		private Entity chased;
		private double chaseBest;
		private int chaseStale;

		/**
		 * A blaze we got no closer to in 10 s is out over lava or outside the walls: leave it for a
		 * minute (CombatSkills' unreachable list) and wait for one inside. Chasing those is what
		 * made Baritone bridge out of the fortress (a death) and kept the fight from ever moving on.
		 */
		private void trackChase(Perception.Seen blaze) {
			if (blaze.entity() != chased) {
				chased = blaze.entity();
				chaseBest = blaze.dist();
				chaseStale = 0;
			} else if (blaze.dist() < chaseBest - 0.5) {
				chaseBest = blaze.dist();
				chaseStale = 0;
			} else if (++chaseStale > 20 * 10) {
				CombatSkills.markUnreachable(chased);
				chased = null;
				Bari.stop();
				walking = false;
			}
		}

		/** The nearest blaze, skipping ones we couldn't reach unless they came close. */
		private static Perception.Seen reachableBlaze(Perception seen) {
			for (Perception.Seen m : seen.mobs)
				if (m.type().equals("blaze") && (m.dist() < 4 || !CombatSkills.unreachable(m.entity()))) return m;
			return null;
		}

		private static boolean rodNearby(LocalPlayer pl, Perception seen) {
			for (ItemEntity it : seen.items) if (Items2.id(it.getItem()).equals("blaze_rod") && it.distanceTo(pl) < 12) return true;
			return false;
		}

		private boolean recovering;
		private boolean eating;
		private boolean shielding;
		private int recoverStart;
		private float recoverBest;

		/**
		 * Low health (12, since burning keeps hurting after we stop): eat, then shield, then fight
		 * again at 16. Eating comes first even in sight and on fire: health only comes back at 18+
		 * hunger (loop 0341 burned to death at hunger 15 with 16 steaks). Full, with a blaze in sight,
		 * stand and hold the shield up at it: a raised shield stops its fireballs, so nothing new
		 * catches fire while health comes back. Running for cover instead kept the bot burning in a
		 * room with no cover until it died, twice, even at full hunger (batch 14 blaze runs 1 and 2).
		 * Returns true while recovering.
		 */
		private boolean recover(LocalPlayer pl, Perception.Seen blaze, Perception seen, net.minecraft.client.KeyMapping keyUse) {
			float hp = pl.getHealth();
			if (!recovering && hp > 12) return false;
			if (recovering && hp >= 16) {
				recovering = false;
				stopEating(keyUse);
				stopShielding(keyUse);
				return false;
			}
			if (!recovering) {
				recoverStart = ticks;
				recoverBest = hp;
			}
			recoverBest = Math.max(recoverBest, hp);
			recovering = true;
			String stop = hopeless(pl, seen);
			if (stop != null && io.github.plrlr.autopilot.Exposure.mark("nether.recover_bounded", stop)) {
				recovering = false;
				stopEating(keyUse);
				stopShielding(keyUse);
				// A mob at arm's length: the fight code below answers it. Healing that can't come: leave
				// (the planner can take us home to eat, NetherPlan.homeToEat).
				if (!stop.startsWith("threat")) fail(stop.startsWith("no food") ? Fail.NEED_ITEM : Fail.NO_PROGRESS, stop);
				return false;
			}
			if (pl.getFoodData().getFoodLevel() < 20 && eat(pl, keyUse)) {
				shielding = false;
				return true;
			}
			stopEating(keyUse);
			boolean inSight = blaze != null && Mc.canSee(blaze.entity());
			if (inSight && Items2.id(pl.getOffhandItem()).equals("shield")) {
				if (Bari.pathing()) Bari.stop();
				walking = false;
				Mc.lookAt(blaze.entity().getBoundingBox().getCenter());
				// Use goes to the main hand first: the weapon has none, so the off-hand shield rises.
				keyUse.setDown(true);
				shielding = true;
				return true;
			}
			stopShielding(keyUse);
			if (inSight || pl.isOnFire()) {
				// No shield: run from the nearest blaze (or just move, if burning) until it can't see us.
				BlockPos from = blaze != null ? blaze.entity().blockPosition() : pl.blockPosition();
				if (ticks % 10 == 0) Bari.path(new baritone.api.pathing.goals.GoalRunAway(14, from));
				return true;
			}
			// Out of sight and full: wait for health to come back.
			if (Bari.pathing()) Bari.stop();
			return true;
		}

		/**
		 * Gene nether.recover_bounded: why waiting to heal here is pointless, or null. Recovery stood
		 * still while a wither skeleton hit it (gen176 eval-g159-2 died that way), and waited for
		 * health that can't come back: below 18 hunger with nothing to eat, health doesn't regenerate.
		 */
		private String hopeless(LocalPlayer pl, Perception seen) {
			Perception.Seen close = seen.nearestHostile();
			if (close != null && close.dist() <= 3.2 && !close.type().equals("blaze"))
				return "threat: " + close.type() + " at " + Math.round(close.dist()) + " while waiting to heal";
			int hunger = pl.getFoodData().getFoodLevel();
			if (hunger < 18 && Mc.count(Items2.matcher("food")) == 0)
				return "no food: hunger " + hunger + " and nothing to eat, health can't come back";
			if (ticks - recoverStart > 20 * 45 && recoverBest <= pl.getHealth() + 0.5f && pl.getHealth() < 14)
				return "no health back in 45 s (at " + Math.round(pl.getHealth()) + ")";
			return null;
		}

		private void stopShielding(net.minecraft.client.KeyMapping keyUse) {
			if (!shielding) return;
			shielding = false;
			keyUse.setDown(false);
		}

		/** Holds food and keeps eating; false with no food in the bag. Standing still: eating while walking crawls anyway. */
		private boolean eat(LocalPlayer pl, net.minecraft.client.KeyMapping keyUse) {
			if (Mc.count(Items2.matcher("food")) == 0) return false;
			if (Bari.pathing()) Bari.stop();
			walking = false;
			if (!Items2.matcher("food").test(pl.getMainHandItem())) Mc.holdItem(Items2.matcher("food"));
			keyUse.setDown(true);
			eating = true;
			return true;
		}

		private void stopEating(net.minecraft.client.KeyMapping keyUse) {
			if (!eating) return;
			eating = false;
			keyUse.setDown(false);
			CombatSkills.holdWeapon();
		}

		@Override
		protected void cleanup() {
			Mc.mc().options.keyUse.setDown(false);
			restoreBaritone();
			super.cleanup();
		}
	}
}
