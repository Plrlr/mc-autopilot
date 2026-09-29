package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Moving stacks between an open chest and the inventory with shift-clicks, one per call, like a
 * player does (the menu's quick-move). Callers do one move per tick so the server keeps up.
 */
final class ChestOps {
	private ChestOps() {}

	/** Number of the chest's own slots (27 or 54); the player's inventory comes after them. */
	static int chestSlots(ChestMenu m) {
		return m.getRowCount() * 9;
	}

	/** Shift-clicks one chest stack matching `what` into the inventory. False when none is left. */
	static boolean takeOne(ChestMenu m, Predicate<ItemStack> what) {
		for (int i = 0; i < chestSlots(m); i++) {
			ItemStack s = m.getSlot(i).getItem();
			if (!s.isEmpty() && what.test(s)) {
				Mc.click(m, i, 0, ContainerInput.QUICK_MOVE);
				return true;
			}
		}
		return false;
	}

	/** Shift-clicks one inventory stack matching `what` into the chest. False when none is left. */
	static boolean putOne(ChestMenu m, Predicate<ItemStack> what) {
		for (int inv = 9; inv < 36; inv++) { // main inventory rows first; the hotbar keeps our tools
			ItemStack s = Mc.player().getInventory().getItem(inv);
			if (s.isEmpty() || !what.test(s)) continue;
			int slot = Mc.menuSlotFor(m, inv);
			if (slot < 0) continue;
			Mc.click(m, slot, 0, ContainerInput.QUICK_MOVE);
			return true;
		}
		return false;
	}

	/** What a ruined-portal chest holds that's worth taking: everything but rotten flesh. */
	static boolean loot(ItemStack s) {
		return !Items2.id(s).equals("rotten_flesh");
	}
}
