package io.github.plrlr.autopilot.state;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers useful blocks the player has actually seen (exposed to air and in line of sight),
 * so skills can walk back to a crafting table or a portal. No x-ray: hidden ores are never recorded.
 */
public final class WorldMemory {
	/** Block ids worth remembering, grouped by the name the brains use. */
	private static final Map<String, String> TRACKED = new LinkedHashMap<>();

	static {
		for (String s : new String[]{"crafting_table", "furnace", "chest", "nether_portal", "end_portal_frame",
				"end_portal", "obsidian", "gravel", "sand", "lava", "water", "nether_bricks", "spawner",
				"coal_ore", "iron_ore", "gold_ore", "diamond_ore", "copper_ore"}) {
			TRACKED.put(s, s);
		}
		for (String ore : new String[]{"coal_ore", "iron_ore", "gold_ore", "diamond_ore", "copper_ore"}) {
			TRACKED.put("deepslate_" + ore, ore);
		}
		TRACKED.put("nether_gold_ore", "gold_ore");
		// Ruined portals (skills/RuinedPortal): crying obsidian in the overworld is their sign, seen from afar.
		TRACKED.put("crying_obsidian", "ruined_portal");
		TRACKED.put("stone", "stone");
		TRACKED.put("deepslate", "stone");
		TRACKED.put("cobblestone", "stone");
	}

	/** Group name for a block id, or null if it's not tracked. Logs and beds are matched by suffix. */
	public static String groupOf(String blockId) {
		if (blockId.endsWith("_log") && !blockId.startsWith("stripped_")) return "log";
		if (blockId.endsWith("_bed")) return "bed";
		return TRACKED.get(blockId);
	}

	public record Seen(BlockPos pos, String block, String dim, long tick) {}

	/** How far we'll walk back to a known crafting table or furnace before making a new one. */
	public static final int STATION_RANGE = 64;

	/** Nearest known block of the group within STATION_RANGE, or null. */
	public Seen nearestStation(String group) {
		Seen s = nearest(group);
		LocalPlayer pl = Mc.player();
		if (s == null || pl == null) return null;
		return s.pos().distSqr(pl.blockPosition()) < (double) STATION_RANGE * STATION_RANGE ? s : null;
	}

	private final Map<String, Map<BlockPos, Seen>> byGroup = new HashMap<>();
	private long scanTick;
	public String worldKey = "";
	private static final int MAX_PER_GROUP = 400;

	public void clear() {
		byGroup.clear();
		seenLand.clear();
		badLand.clear();
		badHeadings.clear();
		visited.clear();
		heading = -1;
		surfaceEntry = null;
		undergroundSince = -1;
	}

	// ---- exploring: where we've been, so explore heads into new ground instead of zigzagging ----

	/** Regions of 64x64 blocks we've stood in (per dimension), with a visit weight. */
	private final Map<String, Integer> visited = new HashMap<>();
	/** Current explore direction, 0-7 in 45 degree steps (0 = east, 2 = south); -1 = not chosen yet. */
	private int heading = -1;
	private final Map<String, int[]> badHeadings = new HashMap<>();

	private static String region(String dim, double x, double z) {
		return dim + ":" + Math.floorDiv((int) x, 64) + ":" + Math.floorDiv((int) z, 64);
	}

	private void markVisited(String dim, double x, double z, int weight) {
		visited.merge(region(dim, x, z), weight, Integer::sum);
	}

	/**
	 * The direction to explore next: keep going the same way while the ground ahead is new, and
	 * turn toward the least-visited direction otherwise. Straight lines find new chunks fastest;
	 * random directions keep walking back over the same ground.
	 */
	public int exploreHeading(double x, double z, int dist) {
		String dim = Mc.dimension();
		int best = heading < 0 ? 0 : heading;
		double bestScore = Double.MAX_VALUE;
		for (int h = 0; h < 8; h++) {
			double a = h * Math.PI / 4;
			double score = 0;
			for (int step = 1; step <= 3; step++) {
				double d = dist * step / 3.0;
				score += visited.getOrDefault(region(dim, x + Math.cos(a) * d, z + Math.sin(a) * d), 0);
			}
			if (heading >= 0) {
				int turn = Math.min(Math.abs(h - heading), 8 - Math.abs(h - heading));
				// Prefer small turns; never go straight back the way we came unless all else is worse.
				score += turn * 0.75 + (turn == 4 ? 3 : 0);
			}
			if (io.github.plrlr.autopilot.Tune.on("move.shore_first"))
				score += badHeadings.computeIfAbsent(dim, k -> new int[8])[h];
			if (score < bestScore) {
				bestScore = score;
				best = h;
			}
		}
		heading = best;
		return best;
	}

	/** The way ahead was blocked (open water, a cliff): count it as visited so we turn away. */
	public void markBadAhead(double x, double z, int dist) {
		if (heading < 0) return;
		double a = heading * Math.PI / 4;
		String dim = Mc.dimension();
		if (io.github.plrlr.autopilot.Tune.on("move.shore_first"))
			badHeadings.computeIfAbsent(dim, k -> new int[8])[heading] += 6;
		for (int step = 1; step <= 3; step++) {
			double d = dist * step / 3.0;
			markVisited(dim, x + Math.cos(a) * d, z + Math.sin(a) * d, 6);
		}
	}

	private static final int RH = 16, RV = 10, LAYERS_PER_TICK = 3;

	/**
	 * The (dx, dz) columns of the scan box, nearest first: the line-of-sight budget per sweep goes
	 * to what's close. Scanning from one corner let an ocean's water use it all up before a
	 * pool six blocks away was ever checked.
	 */
	private static final int[][] COLUMNS;
	/** Far surface scan radius (see scanFar). */
	private static final int FAR = 48;
	private static final int[][] FAR_COLUMNS;

	static {
		java.util.List<int[]> cols = new java.util.ArrayList<>();
		for (int dx = -RH; dx <= RH; dx++) for (int dz = -RH; dz <= RH; dz++) cols.add(new int[]{dx, dz});
		cols.sort(java.util.Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1]));
		COLUMNS = cols.toArray(new int[0][]);
		List<int[]> far = new ArrayList<>();
		for (int dx = -FAR; dx <= FAR; dx++) {
			for (int dz = -FAR; dz <= FAR; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > RH * RH && d2 <= FAR * FAR) far.add(new int[]{dx, dz});
			}
		}
		far.sort(java.util.Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1]));
		FAR_COLUMNS = far.toArray(new int[0][]);
	}

	/**
	 * Surface water and lava out to 48 blocks: a lava pool is bright and visible from far away,
	 * but the close scan only reaches 16 blocks, so explore walked past pools (batch 6 never found
	 * one). Only the top block of each column is looked at, and only if it's in line of sight.
	 */
	private int farCursor;

	private void scanFar(Level level, BlockPos c, String dim, long tick) {
		int rays = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int n = 0; n < 150; n++) {
			int[] col = FAR_COLUMNS[farCursor];
			farCursor = (farCursor + 1) % FAR_COLUMNS.length;
			int x = c.getX() + col[0], z = c.getZ() + col[1];
			p.set(x, c.getY(), z);
			if (!level.isLoaded(p)) continue;
			int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
			p.set(x, top, z);
			BlockState st = level.getBlockState(p);
			if (!st.liquid() || !st.getFluidState().isSource()) continue;
			String group = groupOf(Mc.id(st.getBlock()));
			if (group == null) continue;
			Map<BlockPos, Seen> m = byGroup.computeIfAbsent(group, k -> new LinkedHashMap<>());
			if (m.containsKey(p)) continue;
			if (++rays > 10) break;
			BlockPos pos = p.immutable();
			if (Mc.canSee(pos)) m.put(pos, new Seen(pos, Mc.id(st.getBlock()), dim, tick));
		}
	}
	/**
	 * Long-range sight: a few rays from the eyes each tick, a full sweep of the view every ~15
	 * ticks, out to 96 blocks. Each ray remembers the first block it hits if it's one we track:
	 * exactly what a player sees, no x-ray. A fortress, a lava pool or an end portal frame 90 blocks
	 * away shows up the way it would on screen (the block scan only reaches 16).
	 */
	private static final double SIGHT = 96;
	private static final int RAYS_PER_TICK = 16;
	private static final Vec3[] DIRS;
	/** A group this big stops growing (an ocean's surface is all water sources). */
	private static final int GROUP_CAP = 600;
	private int rayCursor;

	static {
		List<Vec3> dirs = new ArrayList<>();
		for (int pitch = -50; pitch <= 40; pitch += 10) {
			int steps = Math.max(8, (int) Math.round(36 * Math.cos(Math.toRadians(pitch))));
			for (int i = 0; i < steps; i++) {
				double yaw = 2 * Math.PI * i / steps + pitch * 0.07;
				double cp = Math.cos(Math.toRadians(pitch));
				dirs.add(new Vec3(Math.cos(yaw) * cp, Math.sin(Math.toRadians(pitch)), Math.sin(yaw) * cp));
			}
		}
		DIRS = dirs.toArray(new Vec3[0]);
	}

	private void scanSight(LocalPlayer pl, Level level, String dim, long tick) {
		Vec3 eye = pl.getEyePosition();
		for (int i = 0; i < RAYS_PER_TICK; i++) {
			Vec3 d = DIRS[rayCursor];
			rayCursor = (rayCursor + 1) % DIRS.length;
			BlockHitResult r = level.clip(new ClipContext(eye, eye.add(d.scale(SIGHT)), ClipContext.Block.VISUAL,
					ClipContext.Fluid.SOURCE_ONLY, pl));
			if (r.getType() != HitResult.Type.BLOCK) continue;
			BlockPos p = r.getBlockPos();
			// This ray hit the ground itself; no hidden terrain is sampled for a shore target.
			if (io.github.plrlr.autopilot.Tune.on("move.shore_first") && i % 4 == 0 && Mc.canSee(p.above())
					&& Mc.canSee(p.above(2))) rememberLand(p.above(), tick);
			String id = Mc.id(level.getBlockState(p).getBlock());
			String group = groupOf(id);
			if (group == null || group.equals("stone") || group.equals("gravel") || group.equals("sand")) continue;
			Map<BlockPos, Seen> m = byGroup.computeIfAbsent(group, k -> new LinkedHashMap<>());
			if (m.size() >= GROUP_CAP && !m.containsKey(p)) continue;
			BlockPos pos = p.immutable();
			m.put(pos, new Seen(pos, id, dim, tick));
		}
	}

	private void rememberLand(BlockPos feet, long tick) {
		Level level = Mc.player().level();
		if (!level.isLoaded(feet) || !level.isLoaded(feet.above()) || !level.isLoaded(feet.below())) return;
		if (!Mc.free(feet) || !Mc.free(feet.above()) || !Mc.solid(feet.below())) return;
		if (Mc.id(Mc.state(feet.below()).getBlock()).endsWith("_leaves")) return;
		BlockPos p = feet.immutable();
		seenLand.put(p, new Seen(p, "land", Mc.dimension(), tick));
		while (seenLand.size() > 600) seenLand.remove(seenLand.keySet().iterator().next());
	}

	/** Closest seen ground with space around it; a lone island block is not a work area. */
	public BlockPos nearestLand(java.util.Set<BlockPos> aside) {
		LocalPlayer pl = Mc.player();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos fallback = null;
		double fallbackDist = Double.MAX_VALUE;
		for (Seen s : seenLand.values()) {
			BlockPos p = s.pos();
			if (!s.dim().equals(Mc.dimension()) || aside.contains(p) || badLand.getOrDefault(p, 0L) > scanTick) continue;
			double d = p.distSqr(pl.blockPosition());
			if (landRoom(p)) {
				if (d < bestDist) { bestDist = d; best = p; }
			} else if ((pl.isInWater() || d > 9) && d < fallbackDist) {
				// A sparse sight scan may have seen only one block of shore so far. Visit it,
				// then check the room at our feet instead of assuming it is another island.
				fallbackDist = d;
				fallback = p;
			}
		}
		return best != null ? best : fallback;
	}

	/** Leave an unreachable shore aside long enough for the next attempt to choose another. */
	public void markBadLand(BlockPos p) {
		badLand.put(p, scanTick + 20 * 120);
	}

	/** Only confirmed, visible foot positions count as neighboring dry room. */
	private boolean landRoom(BlockPos p) {
		int neighbors = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			Seen n = seenLand.get(p.relative(d));
			if (n != null && n.dim().equals(Mc.dimension())) neighbors++;
		}
		return neighbors >= 2;
	}

	private int layerCursor = -RV;
	private int raycasts;

	/**
	 * Where we last stood under open sky, and since when we've been underground: the way out of
	 * a cave (goto surface walks back there) and the clock for "lost down here".
	 */
	private BlockPos surfaceEntry;
	/** Foot positions backed by ground the player saw through a sight ray or stood on. */
	private final Map<BlockPos, Seen> seenLand = new LinkedHashMap<>();
	private final Map<BlockPos, Long> badLand = new HashMap<>();
	private String surfaceDim = "";
	private long undergroundSince = -1;

	public BlockPos surfaceEntry() {
		return surfaceDim.equals(Mc.dimension()) ? surfaceEntry : null;
	}

	/** Ticks spent underground since we last saw the sky (0 on the surface). */
	public long undergroundTicks(long tick) {
		return undergroundSince < 0 ? 0 : tick - undergroundSince;
	}

	private void updateSurface(LocalPlayer pl, long tick) {
		if (io.github.plrlr.autopilot.Tune.on("move.shore_first") && pl.onGround() && !pl.isInWater()) {
			BlockPos feet = pl.blockPosition();
			rememberLand(feet, tick);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = feet.relative(d);
				if (Mc.canSee(n.below()) && Mc.canSee(n) && Mc.canSee(n.above())) rememberLand(n, tick);
			}
		}
		if (!Mc.dimension().equals("overworld")) {
			undergroundSince = -1;
			return;
		}
		if (pl.level().canSeeSky(pl.blockPosition().above())) {
			surfaceEntry = pl.blockPosition().immutable();
			surfaceDim = Mc.dimension();
			undergroundSince = -1;
		} else if (undergroundSince < 0) {
			undergroundSince = tick;
		}
	}

	/**
	 * Scans a few horizontal layers of the box around the player each tick, so the full box is
	 * covered about every 7 ticks without a frame-time spike.
	 */
	public void scan(long tick) {
		LocalPlayer pl = Mc.player();
		if (pl == null) return;
		scanTick = tick;
		if (tick % 10 == 0) updateSurface(pl, tick);
		Level level = pl.level();
		String dim = Mc.dimension();
		BlockPos c = pl.blockPosition();
		scanFar(level, c, dim, tick);
		scanSight(pl, level, dim, tick);
		if (layerCursor == -RV) raycasts = 0;
		int from = layerCursor, to = Math.min(RV, layerCursor + LAYERS_PER_TICK - 1);
		layerCursor = to >= RV ? -RV : to + 1;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = from; dy <= to; dy++) {
			for (int[] col : COLUMNS) {
				{
					p.set(c.getX() + col[0], c.getY() + dy, c.getZ() + col[1]);
					if (!level.isLoaded(p)) continue;
					BlockState st = level.getBlockState(p);
					if (st.isAir()) continue;
					String id = Mc.id(st.getBlock());
					String group = groupOf(id);
					if (group == null) continue;
					// Water and lava: only sources open to the air, the ones a bucket can take.
					// An ocean's thousands of other water blocks are no use and cost raycasts.
					if (st.liquid() && (!st.getFluidState().isSource() || !level.getBlockState(p.above()).isAir())) continue;
					BlockPos pos = p.immutable();
					Map<BlockPos, Seen> m = byGroup.computeIfAbsent(group, k -> new LinkedHashMap<>());
					if (group.equals("stone")) {
						// Stone is everywhere; only remember a little of it, no raycast needed if exposed.
						// Full: drop the oldest, so what's remembered is stone near where we are now
						// (a full list from spawn left none "seen" 200 blocks later).
						if (!m.containsKey(pos) && exposed(level, pos)) {
							if (m.size() >= 64) m.remove(m.keySet().iterator().next());
							m.put(pos, new Seen(pos, id, dim, tick));
						}
						continue;
					}
					if (m.containsKey(pos)) {
						m.put(pos, new Seen(pos, id, dim, tick));
						continue;
					}
					if (!exposed(level, pos) || raycasts >= 300) continue;
					raycasts++;
					if (Mc.canSee(pos)) m.put(pos, new Seen(pos, id, dim, tick));
				}
			}
		}
		if (layerCursor != -RV) return;
		markVisited(dim, pl.getX(), pl.getZ(), 1);
		// Once per full sweep: forget blocks that are gone (mined, burned) when we're close enough to know.
		for (Map.Entry<String, Map<BlockPos, Seen>> g : byGroup.entrySet()) {
			Map<BlockPos, Seen> m = g.getValue();
			// Marks we set ourselves (the death spot) aren't blocks; don't check them against the world.
			if (g.getKey().equals("death")) continue;
			Iterator<Map.Entry<BlockPos, Seen>> it = m.entrySet().iterator();
			while (it.hasNext()) {
				Seen s = it.next().getValue();
				if (!s.dim.equals(dim)) continue;
				if (s.pos.distManhattan(c) < 20 && level.isLoaded(s.pos)) {
					String now = Mc.id(level.getBlockState(s.pos).getBlock());
					if (!group(now, s)) it.remove();
				}
			}
			while (m.size() > MAX_PER_GROUP) m.remove(m.keySet().iterator().next());
		}
	}

	private static boolean group(String nowId, Seen s) {
		String g = groupOf(nowId);
		return g != null && g.equals(groupOf(s.block));
	}

	private static boolean exposed(Level level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			BlockState n = level.getBlockState(pos.relative(d));
			if (n.isAir() || !n.canOcclude() || n.liquid()) return true;
		}
		return false;
	}

	/** Remember a block we placed ourselves (crafting table, furnace, portal). */
	public void remember(String group, BlockPos pos, String block) {
		byGroup.computeIfAbsent(group, k -> new LinkedHashMap<>()).put(pos, new Seen(pos, block, Mc.dimension(), scanTick));
	}

	public void forget(String group, BlockPos pos) {
		Map<BlockPos, Seen> m = byGroup.get(group);
		if (m != null) m.remove(pos);
	}

	/** Nearest remembered block of a group in the current dimension, or null. */
	public Seen nearest(String group) {
		LocalPlayer pl = Mc.player();
		Map<BlockPos, Seen> m = byGroup.get(group);
		if (pl == null || m == null) return null;
		String dim = Mc.dimension();
		Vec3 me = pl.position();
		Seen best = null;
		double bd = Double.MAX_VALUE;
		for (Seen s : m.values()) {
			if (!s.dim.equals(dim)) continue;
			double d = Vec3.atCenterOf(s.pos).distanceToSqr(me);
			if (d < bd) {
				bd = d;
				best = s;
			}
		}
		return best;
	}

	public List<Seen> all(String group) {
		Map<BlockPos, Seen> m = byGroup.get(group);
		if (m == null) return List.of();
		String dim = Mc.dimension();
		List<Seen> out = new ArrayList<>();
		for (Seen s : m.values()) if (s.dim.equals(dim)) out.add(s);
		return out;
	}

	/** Distance to the nearest known block per group (for the brain's state). */
	public Map<String, Integer> nearestDistances(int limit) {
		LocalPlayer pl = Mc.player();
		Map<String, Integer> out = new LinkedHashMap<>();
		if (pl == null) return out;
		List<Map.Entry<String, Integer>> list = new ArrayList<>();
		for (String g : byGroup.keySet()) {
			if (g.equals("stone")) continue; // stone is everywhere; not worth tokens
			Seen s = nearest(g);
			if (s != null) list.add(Map.entry(g, (int) Math.round(Math.sqrt(Vec3.atCenterOf(s.pos).distanceToSqr(pl.position())))));
		}
		list.sort(Map.Entry.comparingByValue());
		for (var e : list) {
			if (out.size() >= limit) break;
			out.put(e.getKey(), e.getValue());
		}
		return out;
	}
}
