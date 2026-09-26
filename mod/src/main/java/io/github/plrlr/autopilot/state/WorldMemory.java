package io.github.plrlr.autopilot.state;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
		visited.clear();
		heading = -1;
	}

	// ---- exploring: where we've been, so explore heads into new ground instead of zigzagging ----

	/** Regions of 64x64 blocks we've stood in (per dimension), with a visit weight. */
	private final Map<String, Integer> visited = new HashMap<>();
	/** Current explore direction, 0-7 in 45 degree steps (0 = east, 2 = south); -1 = not chosen yet. */
	private int heading = -1;

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
	private static final int FAR = 48;
	private static final int[][] FAR_COLUMNS;
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
	private int layerCursor = -RV;
	private int raycasts;

	/**
	 * Scans a few horizontal layers of the box around the player each tick, so the full box is
	 * covered about every 7 ticks without a frame-time spike.
	 */
	public void scan(long tick) {
		LocalPlayer pl = Mc.player();
		if (pl == null) return;
		scanTick = tick;
		Level level = pl.level();
		String dim = Mc.dimension();
		BlockPos c = pl.blockPosition();
		scanFar(level, c, dim, tick);
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
						if (m.size() < 64 && exposed(level, pos)) m.put(pos, new Seen(pos, id, dim, tick));
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
