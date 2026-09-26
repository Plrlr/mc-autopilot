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
	}

	private static final int RH = 16, RV = 10, LAYERS_PER_TICK = 3;
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
		int rh = RH;
		if (layerCursor == -RV) raycasts = 0;
		int from = layerCursor, to = Math.min(RV, layerCursor + LAYERS_PER_TICK - 1);
		layerCursor = to >= RV ? -RV : to + 1;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = from; dy <= to; dy++) {
			for (int dx = -rh; dx <= rh; dx++) {
				for (int dz = -rh; dz <= rh; dz++) {
					p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (!level.isLoaded(p)) continue;
					BlockState st = level.getBlockState(p);
					if (st.isAir()) continue;
					String id = Mc.id(st.getBlock());
					String group = groupOf(id);
					if (group == null) continue;
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
		// Once per full sweep: forget blocks that are gone (mined, burned) when we're close enough to know.
		for (Map<BlockPos, Seen> m : byGroup.values()) {
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
