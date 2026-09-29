package io.github.plrlr.autopilot;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Keeps room in the bag, the way a player tosses junk with Q. A long dig for diamonds fills all 36
 * slots with stone (trial diamond-a, 2026-09-29: 326 cobblestone, 285 cobbled deepslate, andesite,
 * diorite, granite): then crafting times out (the output has nowhere to go; craft torch failed 90 s
 * after 90 s for the last 5 minutes), and diamonds or obsidian can't be picked up.
 *
 * With fewer than FREE_WANTED empty slots, one stack a second goes: pure junk first, then stone
 * beyond KEEP_BLOCKS (pillars, bridges, the frame's corners and a shelter need some).
 */
public final class Tidy {
	private Tidy() {}

	static final int FREE_WANTED = 4, KEEP_BLOCKS = 128;

	/** Nothing the route uses. Stone-like junk also counts as throwaway, so it goes before cobblestone. */
	static final Set<String> JUNK = Set.of("andesite", "diorite", "granite", "tuff", "calcite", "cinnabar", "dripstone_block",
			"pointed_dripstone", "amethyst_block", "amethyst_shard", "rotten_flesh", "wheat_seeds", "poisonous_potato",
			"moss_block", "clay_ball", "sandstone", "red_sand", "glow_lichen", "sculk", "smooth_basalt");

	/** An item on the ground not worth walking to: junk, or stone while we carry plenty (pickup skips it). */
	public static boolean unwanted(ItemStack s) {
		String id = Items2.id(s);
		if (JUNK.contains(id)) return true;
		return Items2.THROWAWAY.contains(id) && Mc.count("throwaway") >= KEEP_BLOCKS;
	}

	/** Called each second from the main loop (client thread). True if a stack was thrown. */
	static boolean tick() {
		LocalPlayer pl = Mc.player();
		if (pl == null || pl.containerMenu != pl.inventoryMenu || Mc.mc().gui.screen() != null) return false;
		var inv = pl.getInventory();
		int free = 0, blocks = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) free++;
			else if (Items2.THROWAWAY.contains(Items2.id(s))) blocks += s.getCount();
		}
		if (free >= FREE_WANTED) return false;
		int pick = -1;
		// Pure junk first (never the held slot: it may be mid-use), largest stack first.
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || i == inv.getSelectedSlot() || !JUNK.contains(Items2.id(s))) continue;
			if (pick < 0 || s.getCount() > inv.getItem(pick).getCount()) pick = i;
		}
		// Then spare building blocks: the smallest stack whose loss still leaves KEEP_BLOCKS.
		if (pick < 0) {
			for (int i = 0; i < 36; i++) {
				ItemStack s = inv.getItem(i);
				if (s.isEmpty() || i == inv.getSelectedSlot() || !Items2.THROWAWAY.contains(Items2.id(s))) continue;
				if (blocks - s.getCount() < KEEP_BLOCKS) continue;
				if (pick < 0 || s.getCount() < inv.getItem(pick).getCount()) pick = i;
			}
		}
		if (pick < 0) return false;
		int menuSlot = Mc.menuSlotFor(pl.inventoryMenu, pick);
		if (menuSlot < 0) return false;
		// Button 1 with THROW: the whole stack (ctrl+Q over the slot).
		Mc.click(pl.inventoryMenu, menuSlot, 1, ContainerInput.THROW);
		return true;
	}
}
