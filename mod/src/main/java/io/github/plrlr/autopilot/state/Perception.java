package io.github.plrlr.autopilot.state;

import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** What the player can currently see around them: mobs and dropped items (line of sight checked). */
public final class Perception {
	public record Seen(Entity entity, String type, double dist, boolean hostile) {}

	/** Count as "Enemy" in code but only fight back if provoked; attacking them starts a brawl. */
	private static final java.util.Set<String> NEUTRAL = java.util.Set.of("enderman", "piglin", "zombified_piglin", "piglin_brute");

	public final List<Seen> mobs = new ArrayList<>();
	public final List<ItemEntity> items = new ArrayList<>();

	public static Perception look(double range) {
		Perception p = new Perception();
		LocalPlayer pl = Mc.player();
		if (pl == null || Mc.mc().level == null) return p;
		for (Entity e : Mc.mc().level.entitiesForRendering()) {
			if (e == pl || !e.isAlive()) continue;
			double d = e.distanceTo(pl);
			// The dragon is huge and flies far out; see it at any distance in the End.
			if (d > range && !(e instanceof EnderDragon)) continue;
			if (e instanceof ItemEntity it) {
				if (d <= 16 && Mc.canSee(e)) p.items.add(it);
				continue;
			}
			String type = Mc.id(e);
			boolean living = e instanceof LivingEntity;
			if (!living && !type.equals("end_crystal")) continue;
			if (d > 6 && !(e instanceof EnderDragon) && !Mc.canSee(e)) continue;
			// A monster we just failed to reach isn't a threat from afar; up close it still is.
			boolean hostile = e instanceof Enemy && !NEUTRAL.contains(type)
					&& !(d > 4 && io.github.plrlr.autopilot.skills.CombatSkills.unreachable(e));
			p.mobs.add(new Seen(e, type, d, hostile));
		}
		p.mobs.sort(Comparator.comparingDouble(Seen::dist));
		p.items.sort(Comparator.comparingDouble(i -> i.distanceTo(pl)));
		return p;
	}

	public Seen nearest(String type) {
		for (Seen s : mobs) if (s.type.equals(type)) return s;
		return null;
	}

	public Seen nearestHostile() {
		for (Seen s : mobs) if (s.hostile) return s;
		return null;
	}

	/** Hostiles within range that are actually a threat (not a peaceful enderman we haven't looked at). */
	public int hostilesWithin(double r) {
		int n = 0;
		for (Seen s : mobs) if (s.hostile && s.dist <= r) n++;
		return n;
	}
}
