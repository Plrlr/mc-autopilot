package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.state.Perception;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Melee and bow skills. */
public final class CombatSkills {
	private CombatSkills() {}

	/** Best sword (or axe) into the hand; returns false if we have neither. */
	static boolean holdWeapon() {
		String best = null;
		int bestScore = -1;
		for (int i = 0; i < 36; i++) {
			String id = Items2.id(Mc.player().getInventory().getItem(i));
			int t = Items2.tier(id);
			int score = id.endsWith("_sword") ? 10 + t : id.endsWith("_axe") ? 5 + t : -1;
			if (score > bestScore) {
				bestScore = score;
				best = id;
			}
		}
		if (best == null) return false;
		String id = best;
		return Mc.holdItem(s -> Items2.id(s).equals(id));
	}

	/** Mobs we couldn't get to (across water, down a hole): entity id -> time (ms) to retry after. */
	private static final java.util.Map<Integer, Long> UNREACHABLE = new java.util.HashMap<>();
	/** Our own visible swings, an estimate of damage without reading a mob's hidden health. */
	private static final Map<Entity, Integer> OBSERVED_HITS = new WeakHashMap<>();

	/** True for a mob an attack recently failed to reach; the planner leaves it alone for a minute. */
	public static boolean unreachable(Entity e) {
		Long until = UNREACHABLE.get(e.getId());
		return until != null && System.currentTimeMillis() < until;
	}

	/**
	 * Up close a mob is fought even after a chase failed, except (gene combat.ignore_walled_mobs)
	 * one we recently failed to reach that we can't see and that isn't hurting us: the client knows
	 * about a spider in a cave pocket behind a wall, a player only hears it. Gens 51-56: four runs
	 * lost 260-470 tries each to "attack spider" failing every ~3 s ("no sightline") while smelting.
	 */
	public static boolean fightable(Entity e, double dist) {
		return !unreachable(e) || dist < 4 && !walled(e);
	}

	/** Recently unreachable, out of sight and not hurting us (only with the gene). */
	public static boolean walled(Entity e) {
		return Tune.on("combat.ignore_walled_mobs") && unreachable(e) && !Mc.canSee(e) && Mc.player().hurtTime == 0;
	}

	/** Leave this mob alone for a minute: the livelock breaker found us looping around it. */
	public static void leaveAlone(Entity e) {
		markUnreachable(e);
	}

	/** Leave this mob alone for a minute (another skill couldn't reach it either). */
	static void markUnreachable(Entity e) {
		UNREACHABLE.put(e.getId(), System.currentTimeMillis() + 60_000);
	}

	/** Only begin a creeper exchange while there is room to land a visible, charged first hit. */
	public static boolean canHitCreeper(Perception.Seen mob) {
		return Tune.on("combat.creeper_hit") && mob != null && mob.type().equals("creeper")
				&& mob.entity() instanceof net.minecraft.world.entity.monster.Creeper c
				&& mob.dist() >= 2.5 && mob.dist() <= Tune.get("combat.creeper_gap") + 1
				&& c.getSwellDir() <= 0 && Mc.canSee(c)
				&& (Items2.bestTier("sword") >= 0 || Items2.bestTier("axe") >= 0);
	}

	/** attack <mob type>: walk up to the nearest one in sight and hit it until it dies. */
	public static final class Attack extends Skill {
		private Entity target;
		private double bestDist = Double.MAX_VALUE;
		private int sinceCloser;
		private BlockPos lastSeenAt;
		private int unseen;
		private int blocked;
		private int deadTicks = -1;
		/** Ticks spent waiting for the fall of a crit jump (strike anyway after a few). */
		private int critWait;
		private int swings;
		private boolean creeperBacking;
		private int backingTicks;

		@Override
		public String name() {
			return "attack";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 45;
			Perception.Seen s = null;
			Perception observed = Perception.look(32);
			for (Perception.Seen m : observed.mobs) {
				// Up close it's a fight whatever an earlier chase found: refusing it let a zombie
				// keep hitting us while the attack failed instantly (batch 6).
				// A monster heard round a corner may still be chased (a player would); the swing
				// itself waits for a sightline below.
				if (m.type().equals(arg) && fightable(m.entity(), m.dist())) {
					s = m;
					break;
				}
			}
			if (s == null) {
				fail(Fail.NOT_FOUND, "no reachable " + arg + " in sight");
				return;
			}
			if (Tune.i("combat.target_rule") > 0 && s.hostile()) {
				List<Perception.Seen> visible = new ArrayList<>();
				List<CombatTargeting.Candidate> candidates = new ArrayList<>();
			for (Perception.Seen mob : observed.mobs) if (mob.hostile() && Mc.canSee(mob.entity())
					&& (!mob.type().equals("creeper") || canHitCreeper(mob))
						&& fightable(mob.entity(), mob.dist())) {
					int index = visible.size();
					visible.add(mob);
					candidates.add(new CombatTargeting.Candidate(index, mob.type(), mob.dist(),
							OBSERVED_HITS.getOrDefault(mob.entity(), 0), canHitCreeper(mob)));
				}
				int choice = CombatTargeting.choose(candidates, Tune.i("combat.target_rule"),
						Tune.on("combat.creeper_hit"), Tune.get("combat.creeper_priority_range"));
				if (choice >= 0) s = visible.get(choice);
			}
			target = s.entity();
			LocalPlayer pl = Mc.player();
			if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
			holdWeapon();
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			if (deadTicks >= 0) {
				// In the drill, three zombies caused 19 of 32 deaths. The 2.5 s walk to
				// drops after a kill gives the survivors free hits; finish them first.
				if (Tune.on("combat.zombie_pack") && !CombatGroups.collectDrops(zombiesNear(6))) {
					CombatFootwork.releaseMovement();
					if (Bari.pathing()) Bari.stop();
					done("killed the " + arg);
					return;
				}
				// Stand where it died for a moment so the drops get picked up.
				if (deadTicks++ == 0 && lastSeenAt != null) {
					CombatFootwork.releaseMovement();
					Bari.path(new GoalNear(lastSeenAt, 0));
				}
				if (deadTicks > 50) done("killed the " + arg);
				return;
			}
			if (!target.isAlive() || target.isRemoved()) {
				deadTicks = 0;
				return;
			}
			List<Perception.Seen> pack = List.of();
			if (Tune.on("combat.zombie_pack") && Mc.id(target).equals("zombie")) {
				pack = Perception.look(8).mobs.stream()
						.filter(m -> m.type().equals("zombie") && m.hostile() && m.dist() <= 6 && Mc.canSee(m.entity()))
						.toList();
				if (pack.size() >= 2 && CombatGroups.switchTarget(pl.distanceTo(target), pack.get(0).dist())
						&& pack.get(0).entity() != target) target = pack.get(0).entity();
			}
			lastSeenAt = target.blockPosition();
			Entity hitBox = target;
			double dist = pl.distanceTo(target);
			if (target instanceof EnderDragon dragon) {
				// Damage goes through the dragon's body parts; aim for the closest one.
				for (EnderDragonPart part : dragon.getSubEntities()) {
					double d = pl.distanceTo(part);
					if (d < dist || hitBox == target) {
						dist = d;
						hitBox = part;
					}
				}
			}
			boolean creeperTactic = Tune.on("combat.creeper_hit") && Mc.id(target).equals("creeper");
			// A started fuse can finish before we land the first hit. Let the retreat reflex take over.
			if (creeperTactic && !creeperBacking && target instanceof net.minecraft.world.entity.monster.Creeper c
					&& c.getSwellDir() > 0) {
				fail(Fail.HAZARD, "creeper started its fuse before the hit");
				return;
			}
			if (creeperTactic && creeperBacking) {
				Bari.stop();
				Mc.lookAt(target.getBoundingBox().getCenter());
				CombatFootwork.releaseMovement();
				if (dist >= Tune.get("combat.creeper_gap")) {
					creeperBacking = false;
					backingTicks = 0;
				} else if (CombatFootwork.safe(pl, -1, 0) && backingTicks++ < 40) {
					Mc.mc().options.keyDown.setDown(true);
					return;
				} else {
					fail(Fail.HAZARD, "no safe gap after hitting the creeper");
					return;
				}
			}
			if (!Mc.canSee(target) && dist > 4) {
				if (++unseen > 100) {
					fail(Fail.UNREACHABLE, "lost sight of the " + arg);
					return;
				}
			} else unseen = 0;

			if (Tune.on("combat.sprint_hit") && swings == 0 && !creeperTactic && dist <= Tune.get("combat.sprint_hit_dist")
					&& dist > 2.3 && Mc.canSee(target) && CombatFootwork.safe(pl, 1, 0)) {
				Bari.stop();
				Mc.lookAt(target.getBoundingBox().getCenter());
				CombatFootwork.releaseMovement();
				Mc.mc().options.keyUp.setDown(true);
				Mc.mc().options.keySprint.setDown(true);
				return;
			}
			if (dist > 3.0) {
				// Not getting any closer for 8 s: it's across water or down a hole. Give up on
				// this one for a minute instead of chasing it until the 45 s timeout (trials lost
				// 8 minutes to one zombie that way).
				if (dist < bestDist - 0.5) {
					bestDist = dist;
					sinceCloser = 0;
				} else if (++sinceCloser > 20 * 8) {
					UNREACHABLE.put(target.getId(), System.currentTimeMillis() + 60_000);
					fail(Fail.UNREACHABLE, "can't reach the " + arg + " (" + Math.round(dist) + " blocks away)");
					return;
				}
				if (Tune.on("combat.skeleton_approach") && Mc.id(target).equals("skeleton")
						&& Mc.canSee(target) && zigzag(pl)) return;
				if (ticks % 10 == 1) Bari.path(new GoalNear(target.blockPosition(), 1));
				// Walking up to it: the shield comes down (a raised shield slows us to a crawl), and
				// the crit jump and step back let go so they don't fight Baritone's walking.
				boolean guardedApproach = io.github.plrlr.autopilot.Tune.on("survival.danger_v2")
						&& Mc.id(target).equals("skeleton")
						&& Items2.id(pl.getOffhandItem()).equals("shield");
				Mc.mc().options.keyUse.setDown(guardedApproach);
				Mc.mc().options.keyDown.setDown(false);
				Mc.mc().options.keyLeft.setDown(false);
				Mc.mc().options.keyRight.setDown(false);
				Mc.mc().options.keySprint.setDown(false);
				Mc.mc().options.keyJump.setDown(false);
				critWait = 0;
				return;
			}
			if (Bari.pathing()) Bari.stop();
			Mc.lookAt(hitBox.getBoundingBox().getCenter());
			CombatFootwork.releaseMovement();
			// Wait for a full swing: spamming clicks does almost no damage. Between swings, hold the
			// off-hand shield up toward the target: a raised shield stops melee hits and arrows from
			// the front (batch 10: 13 of 25 deaths were melee, 6 arrows, the shield never raised).
			var keyUse = Mc.mc().options.keyUse;
			var o = Mc.mc().options;
			boolean ready = pl.getAttackStrengthScale(0.5f) >= Tune.get("combat.swing_at");
			boolean sprintStrike = Tune.on("combat.sprint_hit") && swings == 0 && !creeperTactic
					&& dist <= 2.3 && Mc.canSee(target) && CombatFootwork.safe(pl, 1, 0);
			// Like a player: a jump before the swing makes it a critical hit (1.5x) when it lands on
			// the way down; between swings, a step back takes us out of a zombie's reach.
			boolean crits = Tune.on("combat.crits") && !sprintStrike && !creeperTactic
					&& !pl.isInWater() && !pl.onClimbable() && !(target instanceof EnderDragon);
			int groupStep = !ready && pack.size() >= 2 ? groupStep(pl, pack) : -1;
			if (groupStep >= 0) {
				// Offsets 1 and 2 are (-cos, -sin) and (cos, sin) of our yaw: the player's right and left.
				o.keyDown.setDown(groupStep == 0);
				o.keyRight.setDown(groupStep == 1);
				o.keyLeft.setDown(groupStep == 2);
			} else if (!ready && dist < Tune.get("combat.keep_dist") && Tune.on("combat.backstep")
					&& CombatFootwork.safe(pl, -1, 0)) o.keyDown.setDown(true);
			else if (!ready && Tune.i("combat.strafe") > 0) {
				int side = (ticks / Tune.i("combat.strafe")) % 2 == 0 ? 1 : -1;
				if (!CombatFootwork.safe(pl, 0, side)) side = -side;
				if (CombatFootwork.safe(pl, 0, side)) {
					o.keyRight.setDown(side > 0);
					o.keyLeft.setDown(side < 0);
				}
			}
			if (sprintStrike && ready) {
				o.keyUp.setDown(true);
				o.keySprint.setDown(true);
			}
			if (ready && crits && critWait < 12) {
				critWait++;
				if (pl.onGround()) {
					o.keyJump.setDown(true);
					return;
				}
				o.keyJump.setDown(false);
				if (pl.getDeltaMovement().y >= 0) return; // still rising: strike on the way down
			}
			if (ready) {
				// The client may perceive a close mob through a wall; only swing with line of sight.
				// gameMode.attack skips the crosshair, so without this the bot could hit through walls.
				// Its own counter: the sight check above resets `unseen` whenever the mob is within 4.
				if (!Mc.canSee(hitBox)) {
					if (++blocked > 60) {
						if (Tune.on("combat.ignore_walled_mobs")) markUnreachable(target);
						fail(Fail.UNREACHABLE, "no sightline to the " + arg);
					}
					return;
				}
				blocked = 0;
				critWait = 0;
				o.keyJump.setDown(false);
				if (pl.isUsingItem()) {
					keyUse.setDown(false);
					Mc.mc().gameMode.releaseUsingItem(pl);
				}
				Mc.mc().gameMode.attack(pl, hitBox);
				Mc.swing();
				swings++;
				OBSERVED_HITS.merge(target, 1, Integer::sum);
				if (creeperTactic) creeperBacking = true;
			} else if (Items2.id(pl.getOffhandItem()).equals("shield")
					// Use goes to the main hand first: with food there it would eat instead.
					&& (Items2.id(pl.getMainHandItem()).endsWith("_sword") || Items2.id(pl.getMainHandItem()).endsWith("_axe"))) {
				keyUse.setDown(true);
			}
		}

		private int zombiesNear(double range) {
			int n = 0;
			for (Perception.Seen m : Perception.look(range).mobs)
				if (m.type().equals("zombie") && m.hostile() && m.dist() <= range) n++;
			return n;
		}

		private int groupStep(LocalPlayer pl, List<Perception.Seen> pack) {
			double yaw = Math.toRadians(pl.getYRot());
			double sin = Math.sin(yaw), cos = Math.cos(yaw);
			CombatGroups.Point[] offsets = {
					new CombatGroups.Point(0.8 * sin, -0.8 * cos),
					new CombatGroups.Point(-0.8 * cos, -0.8 * sin),
					new CombatGroups.Point(0.8 * cos, 0.8 * sin)
			};
			// Offsets: back, right (-cos, -sin), left (cos, sin); CombatFootwork's right > 0 is the player's right.
			boolean[] safe = {CombatFootwork.safe(pl, -1, 0), CombatFootwork.safe(pl, 0, 1), CombatFootwork.safe(pl, 0, -1)};
			List<CombatGroups.Point> zombies = new ArrayList<>();
			for (Perception.Seen m : pack) zombies.add(new CombatGroups.Point(
					m.entity().getX() - pl.getX(), m.entity().getZ() - pl.getZ()));
			return CombatGroups.step(zombies, offsets, safe);
		}

		/** Side-to-side approach uses only short visible steps; Baritone handles blocked terrain. */
		private boolean zigzag(LocalPlayer pl) {
			Bari.stop();
			Mc.lookAt(target.getBoundingBox().getCenter());
			int side = (ticks / Tune.i("combat.skeleton_zigzag")) % 2 == 0 ? 1 : -1;
			if (!CombatFootwork.safe(pl, 1, side)) side = -side;
			if (!CombatFootwork.safe(pl, 1, side)) side = 0;
			if (!CombatFootwork.safe(pl, 1, side)) return false;
			CombatFootwork.releaseMovement();
			var o = Mc.mc().options;
			o.keyUp.setDown(true);
			o.keyRight.setDown(side > 0);
			o.keyLeft.setDown(side < 0);
			boolean shield = Items2.id(pl.getOffhandItem()).equals("shield");
			boolean weapon = Items2.id(pl.getMainHandItem()).endsWith("_sword")
					|| Items2.id(pl.getMainHandItem()).endsWith("_axe");
			o.keyUse.setDown(shield && weapon);
			critWait = 0;
			return true;
		}
	}

	/**
	 * Yaw and pitch (Minecraft degrees) that put a fully drawn arrow on the target: arrows leave at
	 * 3 blocks/tick, then each tick move, keep 99% of their speed and drop 0.05 (vanilla AbstractArrow).
	 * Tries the pitches from low to high and keeps the flattest one that lands within half a block;
	 * for a moving target, aims where it will be after the flight time. Null if out of range.
	 */
	/**
	 * Every arc that lands on the target, flattest first: {yaw, pitch, flight ticks}. Flat shots
	 * and lobs both; the caller picks the first whose path is clear (a crystal on a wide pillar
	 * can only be hit by a lob or from far away: flat shots from nearby hit the pillar's edge).
	 */
	public static List<float[]> ballisticSolutions(Vec3 eye, Vec3 target) {
		List<float[]> out = new ArrayList<>();
		double dx = target.x - eye.x, dz = target.z - eye.z, dy = target.y - (eye.y - 0.1);
		double h = Math.hypot(dx, dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		double prevErr = Double.NaN, prevPitch = 0;
		for (double pitch = -60; pitch <= 85; pitch += 0.5) {
			double err = heightError(pitch, h, dy, null);
			// A sign change between two pitches brackets a hit: bisect it down to the exact pitch.
			if (!Double.isNaN(err) && !Double.isNaN(prevErr) && Math.signum(err) != Math.signum(prevErr)) {
				double lo = prevPitch, hi = pitch, elo = prevErr;
				for (int i = 0; i < 30; i++) {
					double mid = (lo + hi) / 2, em = heightError(mid, h, dy, null);
					if (Double.isNaN(em)) break;
					if (Math.signum(em) == Math.signum(elo)) {
						lo = mid;
						elo = em;
					} else hi = mid;
				}
				int[] ticks = new int[1];
				heightError((lo + hi) / 2, h, dy, ticks);
				out.add(new float[]{yaw, (float) -((lo + hi) / 2), ticks[0]});
			}
			prevErr = err;
			prevPitch = pitch;
		}
		return out;
	}

	/** Arrow height minus target height when the arrow reaches horizontal distance h (NaN: never). */
	private static double heightError(double pitch, double h, double dy, int[] ticksOut) {
		double rad = Math.toRadians(pitch);
		double vx = Math.cos(rad) * 3.0, vy = Math.sin(rad) * 3.0, x = 0, y = 0;
		for (int t = 1; t <= 200; t++) {
			double nx = x + vx, ny = y + vy;
			if (nx >= h) {
				// Interpolate within the tick: the arrow crosses h partway through it.
				double f = (h - x) / (nx - x);
				if (ticksOut != null) ticksOut[0] = t;
				return y + (ny - y) * f - dy;
			}
			x = nx;
			y = ny;
			vx *= 0.99;
			vy = vy * 0.99 - 0.05;
		}
		return Double.NaN;
	}

	/** True if an arrow flown on this arc reaches near the target without hitting a block first. */
	public static boolean clearPath(net.minecraft.world.level.Level level, Vec3 eye, Vec3 target, float[] rot, Entity shooter) {
		double yaw = Math.toRadians(rot[0]), pitch = Math.toRadians(-rot[1]);
		double vh = Math.cos(pitch) * 3.0;
		Vec3 v = new Vec3(-Math.sin(yaw) * vh, Math.sin(pitch) * 3.0, Math.cos(yaw) * vh);
		Vec3 p = eye.add(0, -0.1, 0);
		for (int t = 0; t < 160; t++) {
			Vec3 next = p.add(v);
			if (next.distanceTo(target) < 1.5 || p.distanceTo(target) < 1.5) return true;
			var hit = level.clip(new net.minecraft.world.level.ClipContext(p, next, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, shooter));
			if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return hit.getLocation().distanceTo(target) < 1.5;
			p = next;
			v = new Vec3(v.x * 0.99, v.y * 0.99 - 0.05, v.z * 0.99);
		}
		return false;
	}

	public static float[] ballisticAim(Vec3 eye, Vec3 target, Vec3 targetVel) {
		Vec3 aim = target;
		float[] best = null;
		for (int iter = 0; iter < 3; iter++) {
			double dx = aim.x - eye.x, dz = aim.z - eye.z, dy = aim.y - (eye.y - 0.1);
			double h = Math.hypot(dx, dz);
			best = null;
			double bestErr = 0.5;
			int bestTicks = 0;
			for (double pitch = -60; pitch <= 60; pitch += 0.25) {
				double rad = Math.toRadians(pitch);
				double vx = Math.cos(rad) * 3.0, vy = Math.sin(rad) * 3.0, x = 0, y = 0;
				for (int t = 1; t <= 120; t++) {
					x += vx;
					y += vy;
					vx *= 0.99;
					vy = vy * 0.99 - 0.05;
					if (x >= h) {
						double err = Math.abs(y - dy);
						if (err < bestErr) {
							bestErr = err;
							bestTicks = t;
							best = new float[]{(float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -pitch};
						}
						break;
					}
				}
				if (best != null && bestErr < 0.2) break; // the flattest good arc
			}
			if (best == null || targetVel == null) break;
			aim = target.add(targetVel.scale(bestTicks));
		}
		return best;
	}

	/** shoot <mob type>: bow at a target out of reach, e.g. end crystals or the dragon. */
	public static final class Shoot extends Skill {
		private Entity target;
		private int drawTicks;
		private int shots;
		private int blocked;

		@Override
		public String name() {
			return "shoot";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60;
			if (Mc.count("bow") == 0 || Mc.count("arrow") == 0) {
				fail(Fail.NEED_ITEM, "need a bow and arrows");
				return;
			}
			Perception.Seen s = Perception.look(96).nearest(arg == null ? "" : arg);
			if (s == null) {
				fail(Fail.NOT_FOUND, "no " + arg + " in sight");
				return;
			}
			target = s.entity();
			Bari.stop();
			LocalPlayer pl = Mc.player();
			if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
			Mc.holdItem(st -> Items2.id(st).equals("bow"));
		}

		@Override
		protected void tick() {
			if (!target.isAlive() || target.isRemoved()) {
				done("destroyed the " + arg + " after " + shots + " shots");
				return;
			}
			if (Mc.count("arrow") == 0 || shots >= 10) {
				fail(shots >= 10 ? Fail.USE_FAILED : Fail.NEED_ITEM, shots >= 10 ? "missed 10 shots" : "out of arrows");
				return;
			}
			if (ticks < 3) return;
			LocalPlayer pl = Mc.player();
			// Aim by simulating the arrow (the old d^2/360 guess missed every crystal on its pillar),
			// leading a moving target by where it will be when the arrow gets there.
			Vec3 aimAt = target.getBoundingBox().getCenter();
			float[] rot = null;
			if (target.getDeltaMovement().lengthSqr() > 0.01) {
				// Moving (the dragon): lead it on the flattest arc.
				rot = ballisticAim(pl.getEyePosition(), aimAt, target.getDeltaMovement());
			} else {
				// Still (a crystal): the first arc, flat or lobbed, whose path is clear of blocks.
				for (float[] r : ballisticSolutions(pl.getEyePosition(), aimAt)) {
					if (clearPath(pl.level(), pl.getEyePosition(), aimAt, r, pl)) {
						rot = r;
						break;
					}
				}
				if (rot == null) {
					// No clear arc from here (the pillar's edge is in the way): back off to ~28 blocks
					// out on the ground, where flatter shots clear it.
					Mc.mc().options.keyUse.setDown(false);
					drawTicks = 0;
					if (++blocked == 10) {
						Vec3 away = pl.position().subtract(aimAt).multiply(1, 0, 1);
						Vec3 spot = aimAt.add(away.normalize().scale(28));
						int y = pl.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) spot.x, (int) spot.z);
						Bari.path(new GoalNear(new BlockPos((int) spot.x, y, (int) spot.z), 2));
					}
					if (blocked > 20 * 30) fail(Fail.UNREACHABLE, "no clear shot at the " + arg);
					return;
				}
				if (Bari.pathing()) Bari.stop();
			}
			if (rot == null) {
				fail(Fail.UNREACHABLE, "out of bow range");
				return;
			}
			pl.setYRot(rot[0]);
			pl.setXRot(rot[1]);
			if (drawTicks < 22) {
				Mc.mc().options.keyUse.setDown(true);
				drawTicks++;
			} else {
				Mc.mc().options.keyUse.setDown(false);
				shots++;
				drawTicks = -8; // short pause before the next draw
			}
		}
	}
}
