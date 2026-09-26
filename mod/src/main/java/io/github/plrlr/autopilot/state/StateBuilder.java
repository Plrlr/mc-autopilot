package io.github.plrlr.autopilot.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.TreeMap;

/**
 * Compact game state for the brains (target: under ~600 tokens). Short keys, rounded numbers,
 * game facts only: nothing personal ever goes into it.
 */
public final class StateBuilder {
	private StateBuilder() {}

	public static JsonObject build(WorldMemory memory, Perception seen, String goal, String lastSkill, boolean lastOk,
								   String lastDetail, boolean stuck, int deaths, int milestone) {
		LocalPlayer pl = Mc.player();
		JsonObject o = new JsonObject();
		o.addProperty("dim", Mc.dimension());
		JsonArray pos = new JsonArray();
		pos.add(pl.getBlockX());
		pos.add(pl.getBlockY());
		pos.add(pl.getBlockZ());
		o.add("pos", pos);
		o.addProperty("hp", Math.round(pl.getHealth()));
		o.addProperty("food", pl.getFoodData().getFoodLevel());
		o.addProperty("armor", pl.getArmorValue());
		long t = pl.level().getOverworldClockTime() % 24000L;
		o.addProperty("time", Mc.isNight() ? "night" : (t > 11000 ? "dusk" : "day"));
		if (pl.level().isRaining()) o.addProperty("rain", true);
		if (pl.isInWater()) o.addProperty("in_water", true);
		if (pl.isOnFire()) o.addProperty("on_fire", true);

		Map<String, Integer> inv = new TreeMap<>();
		for (ItemStack s : pl.getInventory().getNonEquipmentItems()) {
			if (!s.isEmpty()) inv.merge(Items2.id(s), s.getCount(), Integer::sum);
		}
		JsonObject invJ = new JsonObject();
		inv.forEach(invJ::addProperty);
		o.add("inv", invJ);
		o.addProperty("hand", Items2.id(pl.getMainHandItem()));
		JsonArray worn = new JsonArray();
		for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
			ItemStack st = pl.getItemBySlot(s);
			if (!st.isEmpty()) worn.add(Items2.id(st));
		}
		if (!worn.isEmpty()) o.add("worn", worn);

		JsonObject blocks = new JsonObject();
		memory.nearestDistances(10).forEach(blocks::addProperty);
		o.add("known_blocks_dist", blocks);

		JsonArray mobs = new JsonArray();
		int n = 0;
		for (Perception.Seen s : seen.mobs) {
			if (n++ >= 8) break;
			JsonObject m = new JsonObject();
			m.addProperty("t", s.type());
			m.addProperty("d", Math.round(s.dist()));
			if (s.hostile()) m.addProperty("hostile", true);
			mobs.add(m);
		}
		o.add("mobs", mobs);
		if (!seen.items.isEmpty()) o.addProperty("dropped_items_near", seen.items.size());

		o.addProperty("goal", goal);
		if (lastSkill != null) {
			JsonObject last = new JsonObject();
			last.addProperty("skill", lastSkill);
			last.addProperty("ok", lastOk);
			last.addProperty("detail", lastDetail);
			o.add("last", last);
		}
		if (stuck) o.addProperty("stuck", true);
		o.addProperty("deaths", deaths);
		o.addProperty("milestone", milestone);
		return o;
	}
}
