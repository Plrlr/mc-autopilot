package io.github.plrlr.autopilot.skills;

import baritone.api.pathing.goals.GoalNear;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Facts;
import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.state.Perception;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Wave 3 of docs/skills-40.md: the Nether (0 of 33 Nether starts got rods in the loop so far).
 * Each skill is small and does what a player does there: look before stepping off the portal,
 * bridge over lava instead of walking its edge, look for fortresses from high up, hold a nook at
 * the spawner, hide from ghasts, wear gold among piglins, and farm endermen under a low roof.
 */
public final class NetherRoutes {
	private NetherRoutes() {}

	public static boolean wearingGold() {
		LocalPlayer pl = Mc.player();
		for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
			if (Items2.id(pl.getItemBySlot(s)).startsWith("golden_")) return true;
		return false;
	}

	// ------------------------------------------------------------------ 21 nether_arrival

	/**
	 * nether_arrival: stand still and look around for two seconds (fortress bricks and spawners out
	 * to 96 blocks go into memory), then lay a floor around the portal's exit wherever there's none,
	 * so the first step out isn't into lava or off a ledge.
	 */
	public static final class Arrival extends Skill {
		private int placed;

		@Override
		public String name() {
			return "nether_arrival";
		}

		@Override
		public boolean workingInPlace() {
			return true;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 15;
			io.github.plrlr.autopilot.plan.NetherPlan.arrived();
			if (!Mc.dimension().equals("the_nether")) fail(Fail.WRONG_PLACE, "not in the Nether");
			else Bari.stop();
		}

		@Override
		protected void tick() {
			if (ticks <= 48) {
				NetherSkills.look(memory, ticks * 8, 8, 96);
				return;
			}
			if (ticks % 3 != 0) return;
			BlockPos feet = Mc.player().blockPosition();
			for (int dx = -1; dx <= 1; dx++)
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos floor = feet.offset(dx, -1, dz);
					if ((dx != 0 || dz != 0) && !Mc.solid(floor) && !Mc.id(Mc.state(floor).getBlock()).equals("nether_portal")
							&& Act.place(floor)) {
						placed++;
						return;
					}
				}
			memory.remember("nether_portal", feet, "nether_portal");
			Facts.report("nether_base");
			Facts.report("portal_known");
			done("looked around; laid " + placed + " floor blocks at the portal");
		}
	}

	// ------------------------------------------------------------------ 22 nether_bridge

	/**
	 * nether_bridge [x z]: walk straight toward a point (default: the nearest fortress block seen),
	 * bridging with blocks where the ground ends, crouched at the edge like a player, and walling
	 * the lava side at foot height so neither we nor the lava go where they shouldn't.
	 */
	public static final class Bridge extends Skill {
		private BlockPos target;
		private int placedBlocks, stuck;
		private Vec3 lastPos;

		@Override
		public String name() {
			return "nether_bridge";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 90;
			if (arg != null && arg.trim().split("\\s+").length == 2) {
				String[] a = arg.trim().split("\\s+");
				target = new BlockPos(Integer.parseInt(a[0]), Mc.player().getBlockY(), Integer.parseInt(a[1]));
			} else {
				WorldMemory.Seen b = memory.nearest("nether_bricks");
				if (b == null) {
					fail(Fail.NOT_FOUND, "nowhere to bridge to");
					return;
				}
				target = b.pos();
			}
			if (Mc.count(Items2.matcher("throwaway")) < 8) fail(Fail.NEED_ITEM, "need blocks to bridge");
			Bari.stop();
		}

		@Override
		protected void tick() {
			LocalPlayer pl = Mc.player();
			var o = Mc.mc().options;
			CombatFootwork.releaseMovement();
			o.keyShift.setDown(false);
			if (Act.flatDist(target) < 4) {
				done("across, by " + placedBlocks + " bridge blocks");
				return;
			}
			if (Mc.count(Items2.matcher("throwaway")) == 0) {
				fail(Fail.NEED_ITEM, "ran out of blocks mid-bridge");
				return;
			}
			// Face the target along the nearer axis, so the bridge is a straight line of blocks.
			double dx = target.getX() + 0.5 - pl.getX(), dz = target.getZ() + 0.5 - pl.getZ();
			Direction dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
			BlockPos feet = pl.blockPosition();
			BlockPos ahead = feet.relative(dir);
			if (!Mc.free(ahead) || !Mc.free(ahead.above())) {
				fail(Fail.UNREACHABLE, "wall in the way of the bridge");
				return;
			}
			// Lava beside the next step at foot height: a rail block first.
			for (Direction side : new Direction[]{dir.getClockWise(), dir.getCounterClockWise()}) {
				BlockPos s = ahead.relative(side);
				if (Mc.state(s).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
					Act.place(s);
					return;
				}
			}
			if (!Mc.solid(ahead.below())) {
				// Crouch at the edge (can't fall off crouched), look down behind, place under the next step.
				o.keyShift.setDown(true);
				pl.setYRot(dir.toYRot());
				if (ticks % 4 == 0 && Act.place(ahead.below())) placedBlocks++;
				if (ticks % 4 == 0 && !Mc.solid(ahead.below())) o.keyUp.setDown(true); // creep to the edge
				return;
			}
			pl.setYRot(dir.toYRot());
			o.keyUp.setDown(true);
			Vec3 now = pl.position();
			if (lastPos != null && now.distanceTo(lastPos) < 0.01 && ++stuck > 60) fail(Fail.STUCK, "not moving on the bridge");
			if (lastPos == null || now.distanceTo(lastPos) >= 0.01) stuck = 0;
			lastPos = now;
		}
	}

	// ------------------------------------------------------------------ 23 fortress_scout

	/**
	 * fortress_scout: from a vantage 8 blocks up (a pillar), sweep the view out to 128 blocks for
	 * fortress bricks; seen: done. Not seen: come down and run the fortress search's long legs, then
	 * look again from the next vantage. Fortresses are huge and dark: from above they show far off.
	 */
	public static final class FortressScout extends Composite {
		private enum Phase {RISE, LOOK, SEARCH}

		private Phase phase = Phase.RISE;
		private int baseY, phaseTicks, rounds, tries;

		@Override
		public String name() {
			return "fortress_scout";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 10;
			maxChildFails = 3;
			if (!Mc.dimension().equals("the_nether")) fail(Fail.WRONG_PLACE, "fortresses are in the Nether");
			baseY = Mc.player().getBlockY();
			Bari.stop();
		}

		private boolean found() {
			return memory.nearest("nether_bricks") != null || memory.nearest("spawner") != null;
		}

		@Override
		protected boolean ownTick() {
			if (found()) {
				Mc.mc().options.keyJump.setDown(false);
				Facts.report("fortress_known");
				done("fortress seen");
				return true;
			}
			LocalPlayer pl = Mc.player();
			phaseTicks++;
			switch (phase) {
				case RISE -> {
					BlockPos feet = pl.blockPosition();
					boolean high = feet.getY() >= baseY + 8;
					if (high || phaseTicks > 20 * 12 || Mc.count(Items2.matcher("throwaway")) < 2 || !Mc.free(feet.above(2))) {
						Mc.mc().options.keyJump.setDown(false);
						phase = Phase.LOOK;
						phaseTicks = 0;
						return true;
					}
					Mc.mc().options.keyJump.setDown(true);
					if (!pl.onGround() && Mc.free(feet.below()) && Mc.clearOfPlayer(feet.below()) && phaseTicks % 3 == 0) {
						pl.setXRot(90f);
						Act.place(feet.below());
					}
					return true;
				}
				case LOOK -> {
					if (phaseTicks <= 64) {
						NetherSkills.look(memory, phaseTicks * 8, 8, 128);
						return true;
					}
					phase = Phase.SEARCH;
					return false;
				}
				default -> {
					return false;
				}
			}
		}

		@Override
		protected Option next() {
			if (phase != Phase.SEARCH) return WAIT;
			if (lastResult() != null && lastOption() != null && lastOption().skill().equals("fortress")) {
				// Back from a leg: look again from a new vantage.
				if (++rounds >= 3) return null;
				baseY = Mc.player().getBlockY();
				phase = Phase.RISE;
				phaseTicks = 0;
				return WAIT;
			}
			if (tries++ >= 4) return null;
			return new Option("fortress", "find", "walk a long leg looking for fortress bricks");
		}

		@Override
		protected void finish() {
			if (found()) done("fortress seen");
			else fail(Fail.NOT_FOUND, "no fortress after 3 vantages");
		}
	}

	// ------------------------------------------------------------------ 24 blaze_farm

	/**
	 * blaze_farm[:n]: at the spawner, a nook of blocks on three sides and above, open toward the
	 * spawner; blazes that come to the opening get hit, fire on us means step back and eat,
	 * health under 10 means seal the front until healed. Rods on the ground with no blaze near:
	 * go pick them up and come back.
	 */
	public static final class BlazeFarm extends Composite {
		private enum Phase {WALK, BUILD, HOLD}

		private Phase phase = Phase.WALK;
		private BlockPos spawner, nook;
		private Direction front;
		private int want, buildTries;
		private boolean sealed, collecting;

		@Override
		public String name() {
			return "blaze_farm";
		}

		@Override
		public boolean ownsSafety() {
			return phase != Phase.WALK;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 8;
			maxChildFails = 4;
			want = argCount(8);
			WorldMemory.Seen s = memory.nearest("spawner");
			if (s == null) {
				fail(Fail.NOT_FOUND, "no blaze spawner seen");
				return;
			}
			spawner = s.pos();
		}

		/** Rods count as blaze_rod + powder/2 + eyes/2, like the goal ladder. */
		private int rods() {
			return io.github.plrlr.autopilot.plan.Goal.have("blaze_rod");
		}

		@Override
		protected boolean ownTick() {
			LocalPlayer pl = Mc.player();
			if (rods() >= want) return false;
			switch (phase) {
				case WALK -> {
					if (Act.flatDist(spawner) <= 4 && pl.onGround()) {
						Bari.stop();
						nook = pl.blockPosition();
						Vec3 d = Vec3.atCenterOf(spawner).subtract(pl.position());
						front = Direction.getApproximateNearest(d.x, 0, d.z);
						phase = Phase.BUILD;
						return true;
					}
					if (!Bari.pathing() && ticks % 40 == 1) Bari.path(new GoalNear(spawner, 3));
					if (ticks > 20 * 90) fail(Fail.UNREACHABLE, "couldn't reach the spawner");
					return true;
				}
				case BUILD -> {
					if (ticks % 3 != 0) return true;
					for (BlockPos p : nookCells(false)) {
						if (Mc.free(p)) {
							if (!Act.place(p) && ++buildTries > 25) phase = Phase.HOLD; // fight from what we have
							return true;
						}
					}
					phase = Phase.HOLD;
					return true;
				}
				case HOLD -> {
					if (collecting) return false;
					return hold(pl);
				}
			}
			return false;
		}

		/** The nook: three sides at feet and head, the roof; with `front`, the front two cells too (sealed). */
		private java.util.List<BlockPos> nookCells(boolean withFront) {
			java.util.List<BlockPos> out = new java.util.ArrayList<>();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (d == front && !withFront) continue;
				out.add(nook.relative(d));
				out.add(nook.above().relative(d));
			}
			out.add(nook.above(2));
			return out;
		}

		private boolean hold(LocalPlayer pl) {
			if (!pl.blockPosition().equals(nook)) {
				if (!Bari.pathing()) Bari.path(new baritone.api.pathing.goals.GoalBlock(nook));
				return true;
			}
			Bari.stop();
			float hp = pl.getHealth();
			if (hp < 10 && !sealed) {
				for (BlockPos p : new BlockPos[]{nook.relative(front), nook.above().relative(front)}) if (Mc.free(p)) Act.place(p);
				sealed = true;
			}
			if (sealed) {
				Act.eatTick();
				if (hp >= 18) {
					// Open up again: break the two front blocks.
					for (BlockPos p : new BlockPos[]{nook.relative(front), nook.above().relative(front)})
						if (!Mc.free(p)) {
							new Act.Breaker().tick(p);
							return true;
						}
					sealed = false;
				}
				return true;
			}
			if (pl.isOnFire() && hp < 16 && Act.eatTick()) return true;
			Perception seen = Perception.look(16);
			Perception.Seen blaze = seen.nearest("blaze");
			if (blaze != null) {
				Mc.lookAt(blaze.entity().getBoundingBox().getCenter());
				if (!Act.strike(blaze.entity())) Act.shield(true);
				return true;
			}
			Act.shield(false);
			// Nothing to fight: rods lying about?
			for (ItemEntity it : seen.items)
				if (Items2.id(it.getItem()).equals("blaze_rod") && it.distanceTo(pl) < 10) {
					collecting = true;
					return false;
				}
			return true;
		}

		@Override
		protected Option next() {
			if (rods() >= want) return null;
			if (collecting) {
				if (lastOption() != null && lastOption().skill().equals("pickup") && lastResult() != null) {
					collecting = false;
					return WAIT;
				}
				return new Option("pickup", null, "pick up the blaze rods");
			}
			return WAIT;
		}

		@Override
		protected void finish() {
			Act.shield(false);
			done("have " + rods() + " blaze rods");
		}
	}

	// ------------------------------------------------------------------ 25 ghast_defense

	/**
	 * ghast_defense: a ghast in sight in the open. Hit its fireball back when it's close (charged
	 * swing), shoot the ghast if we carry a bow, else break line of sight with two blocks between.
	 */
	public static final class GhastDefense extends Composite {
		private Entity ghast;
		private int hidden, walls;
		private boolean shot;

		@Override
		public String name() {
			return "ghast_defense";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 30;
			maxChildFails = 1;
			Perception.Seen g = Perception.look(64).nearest("ghast");
			if (g == null) {
				fail(Fail.NOT_FOUND, "no ghast in sight");
				return;
			}
			ghast = g.entity();
			Bari.stop();
		}

		@Override
		protected boolean ownTick() {
			LocalPlayer pl = Mc.player();
			if (!ghast.isAlive()) {
				Facts.report("threat_cleared");
				done("ghast gone");
				return true;
			}
			for (Entity e : Mc.mc().level.entitiesForRendering()) {
				if (e instanceof net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball fb && fb.distanceTo(pl) < 4.5
						&& fb.getDeltaMovement().dot(pl.position().subtract(fb.position())) > 0) {
					Mc.lookAt(fb.getBoundingBox().getCenter());
					Mc.mc().gameMode.attack(pl, fb);
					Mc.swing();
					return true;
				}
			}
			if (!Mc.canSee(ghast)) {
				if (++hidden > 60) done("out of the ghast's sight");
				return true;
			}
			hidden = 0;
			if (!shot && Mc.count("bow") > 0 && Mc.count("arrow") > 0) return false;
			// Two blocks between us and it, at feet and head height, on the side it's on.
			Vec3 d = ghast.position().subtract(pl.position());
			Direction toward = Direction.getApproximateNearest(d.x, 0, d.z);
			BlockPos feet = pl.blockPosition();
			if (ticks % 4 == 0 && walls < 6) {
				for (BlockPos p : new BlockPos[]{feet.relative(toward), feet.above().relative(toward), feet.above(2).relative(toward)})
					if (Mc.free(p) && Act.place(p)) {
						walls++;
						break;
					}
			}
			return true;
		}

		@Override
		protected Option next() {
			if (shot) return WAIT;
			shot = true;
			return new Option("shoot", "ghast", "shoot the ghast");
		}

		@Override
		protected void finish() {
			done("dealt with the ghast");
		}
	}

	// ------------------------------------------------------------------ 26 gold_armor

	/** gold_armor: one gold piece worn (piglins leave a player in gold alone): mine nuggets, make ingots, craft, wear. */
	public static final class GoldArmor extends Composite {
		private String piece;
		private int steps;

		@Override
		public String name() {
			return "gold_armor";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 5;
			maxChildFails = 4;
			if (wearingGold()) {
				done("already wearing gold");
				return;
			}
			LocalPlayer pl = Mc.player();
			piece = pl.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? "golden_helmet"
					: pl.getItemBySlot(EquipmentSlot.FEET).isEmpty() ? "golden_boots" : null;
			if (piece == null) fail(Fail.NO_ROOM, "head and feet already armored");
		}

		private int ingotsNeeded() {
			return piece.equals("golden_helmet") ? 5 : 4;
		}

		@Override
		protected Option next() {
			if (wearingGold() || steps++ > 20) return null;
			if (Mc.count(piece) > 0) {
				Mc.holdItem(s -> Items2.id(s).equals(piece));
				Mc.useItem();
				return WAIT;
			}
			if (Mc.count("gold_ingot") >= ingotsNeeded()) return new Option("craft", piece + ":1", "gold armor keeps piglins calm");
			if (Mc.count("gold_nugget") >= 9) return new Option("craft", "gold_ingot:" + Math.min(ingotsNeeded(), Mc.count("gold_nugget") / 9), "nuggets into ingots");
			if (Mc.count("raw_gold") > 0) return new Option("smelt", "gold_ingot:" + Mc.count("raw_gold"), "smelt the raw gold");
			return new Option("collect", "gold_nugget:" + (9 * (ingotsNeeded() - Mc.count("gold_ingot")) - Mc.count("gold_nugget")), "mine nether gold ore");
		}

		@Override
		protected void finish() {
			if (wearingGold()) done("wearing " + piece);
			else fail(Fail.NO_PROGRESS, "couldn't get gold armor on");
		}
	}

	// ------------------------------------------------------------------ 27 barter_loop

	/** barter_loop[:n]: gold on, then trade gold ingots with piglins round after round until n pearls. */
	public static final class BarterLoop extends Composite {
		private int want, rounds;

		@Override
		public String name() {
			return "barter_loop";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 10;
			maxChildFails = 4;
			want = argCount(12);
			if (!Mc.dimension().equals("the_nether")) fail(Fail.WRONG_PLACE, "piglins are in the Nether");
		}

		private int pearls() {
			return io.github.plrlr.autopilot.plan.Goal.have("ender_pearl");
		}

		@Override
		protected Option next() {
			if (pearls() >= want || rounds++ > 12) return null;
			if (!wearingGold()) return new Option("gold_armor", null, "gold on before bartering");
			if (Mc.count("gold_ingot") == 0) {
				if (Mc.count("gold_nugget") >= 9) return new Option("craft", "gold_ingot:" + Mc.count("gold_nugget") / 9, "nuggets into ingots");
				return new Option("collect", "gold_nugget:36", "gold to trade");
			}
			if (lastOption() != null && lastOption().skill().equals("barter")) return new Option("pickup", null, "pick up what the piglins threw");
			return new Option("barter", null, "trade gold with piglins");
		}

		@Override
		protected void finish() {
			if (pearls() >= want) done("have " + pearls() + " pearls");
			else fail(Fail.NO_PROGRESS, "bartered to " + pearls() + " of " + want + " pearls");
		}
	}

	// ------------------------------------------------------------------ 28 portal_return

	/** portal_return: back through our Nether portal; if it's gone and we carry 10 obsidian, build one here. */
	public static final class PortalReturn extends Composite {
		private boolean built, entered;

		@Override
		public String name() {
			return "portal_return";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 4;
			maxChildFails = 2;
			if (!Mc.dimension().equals("the_nether")) done("already out of the Nether");
		}

		@Override
		protected Option next() {
			if (!Mc.dimension().equals("the_nether")) return null;
			if (memory.nearest("nether_portal") != null && !entered) {
				entered = true;
				return new Option("enter_portal", "overworld", "back through our portal");
			}
			if (!built && Mc.count("obsidian") >= 10 && Mc.count("flint_and_steel") > 0) {
				built = true;
				entered = false;
				return new Option("build_portal", "placed", "our portal is gone: build one here");
			}
			return null;
		}

		@Override
		protected void finish() {
			if (Mc.dimension().equals("overworld")) {
				Facts.report("overworld");
				done("back in the overworld");
			} else fail(Fail.NOT_FOUND, "no way back found");
		}
	}

	// ------------------------------------------------------------------ 29 enderman_warped

	/**
	 * enderman_warped[:n]: endermen are 3 blocks tall and can't get under a 2-high roof. Wall in at
	 * head height with the roof on and the foot-level sides open, look at an enderman to provoke it,
	 * hit its legs through the gap when it comes. Pick up the pearls between kills.
	 */
	public static final class EndermanWarped extends Composite {
		private BlockPos hut;
		private int want, buildTries;
		private boolean built, collecting;

		@Override
		public String name() {
			return "enderman_warped";
		}

		@Override
		public boolean ownsSafety() {
			return built;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 60 * 6;
			maxChildFails = 3;
			want = argCount(4);
			if (Mc.dimension().equals("the_end")) {
				fail(Fail.WRONG_PLACE, "the End's endermen swarm; not here");
				return;
			}
			if (Mc.count(Items2.matcher("throwaway")) < 5) {
				fail(Fail.NEED_ITEM, "need 5 blocks for the hut");
				return;
			}
			hut = Mc.player().blockPosition();
			Bari.stop();
		}

		private int pearls() {
			return io.github.plrlr.autopilot.plan.Goal.have("ender_pearl");
		}

		@Override
		protected boolean ownTick() {
			if (pearls() >= want) return false;
			LocalPlayer pl = Mc.player();
			if (!built) {
				if (ticks % 3 != 0) return true;
				// Head-height walls and the roof; the foot level stays open to hit through.
				for (BlockPos p : new BlockPos[]{hut.above(2), hut.above().north(), hut.above().south(), hut.above().east(), hut.above().west()})
					if (Mc.free(p)) {
						if (!Act.place(p) && ++buildTries > 20) fail(Fail.PLACE_FAILED, "couldn't build the hut");
						return true;
					}
				built = true;
				return true;
			}
			if (collecting) return false;
			if (!pl.blockPosition().equals(hut)) {
				if (!Bari.pathing()) Bari.path(new baritone.api.pathing.goals.GoalBlock(hut));
				return true;
			}
			Bari.stop();
			Perception seen = Perception.look(48);
			Perception.Seen em = seen.nearest("enderman");
			if (em != null) {
				Entity e = em.entity();
				if (em.dist() <= 3.2) {
					// Its legs: aim low, through the foot-level gap.
					Mc.lookAt(e.position().add(0, 0.4, 0));
					Act.strike(e);
				} else if (ticks % 40 == 0) {
					Mc.lookAt(e.getEyePosition()); // provoke: a look at its face
				}
				return true;
			}
			for (ItemEntity it : seen.items)
				if (Items2.id(it.getItem()).equals("ender_pearl") && it.distanceTo(pl) < 8) {
					collecting = true;
					return false;
				}
			if (ticks > 20 * 60 * 5) fail(Fail.NOT_FOUND, "no endermen came");
			return true;
		}

		@Override
		protected Option next() {
			if (pearls() >= want) return null;
			if (collecting) {
				if (lastOption() != null && lastOption().skill().equals("pickup") && lastResult() != null) {
					collecting = false;
					return WAIT;
				}
				return new Option("pickup", null, "pick up the pearl");
			}
			return WAIT;
		}

		@Override
		protected void finish() {
			done("have " + pearls() + " pearls");
		}
	}
}
