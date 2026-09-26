package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.TechTree;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * smelt output:n. Opens a furnace (known, or placed from the inventory), loads the input and
 * enough fuel with normal slot clicks, waits, and takes the output.
 */
public final class SmeltSkill extends Skill {
	private String output;
	private int want;
	private int before;
	private Predicate<ItemStack> input;
	private Station station;
	private boolean loaded;
	private int lastOut;
	private float lastArrow = -1;
	private int idleTicks;

	@Override
	public String name() {
		return "smelt";
	}

	@Override
	public boolean interruptible() {
		return false;
	}

	@Override
	protected void start() {
		output = argName();
		want = argCount(1);
		String in = TechTree.SMELT.get(output);
		if (in == null) {
			fail("don't know how to smelt " + output);
			return;
		}
		input = Items2.matcher(in);
		int have = Mc.count(input);
		if (have == 0) {
			fail("no " + in + " to smelt");
			return;
		}
		want = Math.min(want, have);
		before = Mc.count(output);
		timeoutTicks = 20 * (40 + want * 11);
		station = new Station("furnace", AbstractFurnaceMenu.class, memory);
	}

	@Override
	protected void tick() {
		if (!station.ready()) {
			station.tick();
			if (station.error != null) fail(station.error);
			return;
		}
		LocalPlayer pl = Mc.player();
		AbstractFurnaceMenu menu = (AbstractFurnaceMenu) pl.containerMenu;
		if (!loaded) {
			// Leave a tick between loading and reading so the server's slot updates arrive.
			if (ticks % 5 != 0) return;
			// Leftovers of a different item from an earlier smelt would be smelted instead of ours.
			ItemStack inSlot = menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem();
			if (!inSlot.isEmpty() && !input.test(inSlot)) {
				Mc.click(menu, AbstractFurnaceMenu.INGREDIENT_SLOT, 0, ContainerInput.QUICK_MOVE);
				return;
			}
			if (menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT).hasItem()) {
				Mc.click(menu, AbstractFurnaceMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);
				return;
			}
			int alreadyIn = inSlot.getCount();
			if (alreadyIn < want && !moveInto(menu, input, AbstractFurnaceMenu.INGREDIENT_SLOT, want - alreadyIn)) {
				fail("couldn't load the furnace");
				return;
			}
			if (!loadFuel(menu, want)) {
				fail("no fuel");
				return;
			}
			loaded = true;
			return;
		}
		if (ticks % 20 != 0) return;
		if (menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT).hasItem()) {
			Mc.click(menu, AbstractFurnaceMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);
		}
		int made = Mc.count(output) - before;
		if (made >= want) {
			done("smelted " + made + " " + output);
			return;
		}
		boolean inputLeft = menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).hasItem();
		// Progress = more output or the arrow moving. (The client's "lit" flag proved unreliable.)
		float arrow = menu.getBurnProgress();
		if (made == lastOut && arrow == lastArrow) idleTicks += 20;
		else idleTicks = 0;
		lastOut = made;
		lastArrow = arrow;
		if (!inputLeft && !menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT).hasItem()) {
			if (made > 0) done("smelted " + made + " " + output);
			else if (idleTicks > 40) fail("furnace stopped (out of fuel?)");
		} else if (inputLeft && idleTicks >= 40 && !menu.getSlot(AbstractFurnaceMenu.FUEL_SLOT).hasItem()) {
			// Ran dry with items left: top it up with whatever fuel we still carry.
			int left = menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem().getCount();
			if (loadFuel(menu, left)) idleTicks = 0;
			else if (made > 0) done("smelted " + made + " " + output + " (out of fuel)");
			else fail("out of fuel");
		} else if (idleTicks > 20 * 15) {
			fail("furnace isn't burning (no progress for 15 s)");
		}
	}

	/**
	 * Coal/charcoal smelt 8 items, planks and logs 1.5, sticks 0.5. Picks the first fuel we carry
	 * enough of for the whole load; if none is enough, the one that goes furthest (the furnace
	 * gets topped up as it runs dry). Loading one plank for five items stalled trial runs.
	 */
	private boolean loadFuel(AbstractFurnaceMenu menu, int items) {
		ItemStack inFuel = menu.getSlot(AbstractFurnaceMenu.FUEL_SLOT).getItem();
		String[][] options = {{"coal", "8"}, {"planks", "1.5"}, {"log", "1.5"}, {"stick", "0.5"}};
		Predicate<ItemStack> best = null;
		int bestNeed = 0;
		double bestCover = 0;
		for (String[] o : options) {
			if (o[0].equals("log") && output.equals("charcoal")) continue;
			Predicate<ItemStack> fuel = Items2.matcher(o[0]);
			if (!inFuel.isEmpty() && !fuel.test(inFuel)) continue;
			double per = Double.parseDouble(o[1]);
			int need = (int) Math.ceil(items / per) - inFuel.getCount();
			if (need <= 0) return true;
			int have = Mc.count(fuel);
			if (have == 0) continue;
			if (have >= need) return moveInto(menu, fuel, AbstractFurnaceMenu.FUEL_SLOT, need);
			if (have * per > bestCover) {
				bestCover = have * per;
				best = fuel;
				bestNeed = have;
			}
		}
		if (best != null) return moveInto(menu, best, AbstractFurnaceMenu.FUEL_SLOT, bestNeed);
		// Fuel already present counts even if we have none left in the bag.
		return !inFuel.isEmpty();
	}

	/** Pick up a stack, right-click `count` single items into the target slot, put the rest back. */
	private static boolean moveInto(AbstractFurnaceMenu menu, Predicate<ItemStack> what, int target, int count) {
		int moved = 0;
		for (int inv = 0; inv < 36 && moved < count; inv++) {
			ItemStack s = Mc.player().getInventory().getItem(inv);
			if (s.isEmpty() || !what.test(s)) continue;
			int slot = Mc.menuSlotFor(menu, inv);
			if (slot < 0) continue;
			int take = Math.min(count - moved, s.getCount());
			Mc.click(menu, slot, 0, ContainerInput.PICKUP);
			for (int i = 0; i < take; i++) Mc.click(menu, target, 1, ContainerInput.PICKUP);
			if (!menu.getCarried().isEmpty()) Mc.click(menu, slot, 0, ContainerInput.PICKUP);
			moved += take;
		}
		return moved > 0;
	}

	@Override
	protected void cleanup() {
		LocalPlayer pl = Mc.player();
		if (pl != null && pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
		super.cleanup();
	}
}
