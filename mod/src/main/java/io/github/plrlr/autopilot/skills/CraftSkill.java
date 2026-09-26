package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.function.Predicate;

/**
 * craft item:n. Uses the recipe book like a player clicking a recipe: the server moves the
 * ingredients into the grid, then we shift-click the result. 2x2 recipes use the inventory grid;
 * bigger ones walk to (or place) a crafting table first.
 */
public final class CraftSkill extends Skill {
	private String target;
	private int want;
	private int before;
	private Predicate<ItemStack> matches;
	private boolean needsTable;
	private Station station;
	private int step;

	@Override
	public String name() {
		return "craft";
	}

	@Override
	public boolean interruptible() {
		return false;
	}

	@Override
	protected void start() {
		target = argName();
		want = argCount(1);
		matches = Items2.matcher(target);
		before = Mc.count(matches);
		timeoutTicks = 20 * 90;
		RecipeDisplayEntry e = findRecipe(false);
		if (e == null) {
			fail(findRecipe(true) == null ? "no known recipe for " + target + " (not unlocked yet?)" : "missing ingredients for " + target);
			return;
		}
		needsTable = !fits2x2(e.display());
		if (needsTable) station = new Station("crafting_table", CraftingMenu.class, memory);
		else if (Mc.player().containerMenu != Mc.player().inventoryMenu) Mc.player().closeContainer();
	}

	@Override
	protected void tick() {
		LocalPlayer pl = Mc.player();
		if (station != null && !station.ready()) {
			station.tick();
			if (station.error != null) fail(station.error);
			return;
		}
		AbstractContainerMenu menu = needsTable ? pl.containerMenu : pl.inventoryMenu;
		// One action every few ticks leaves time for the server's answer to arrive.
		if (step++ % 4 != 0) return;
		int made = Mc.count(matches) - before;
		if (made >= want) {
			done("crafted " + made + " " + target);
			return;
		}
		Slot result = menu.getSlot(0);
		if (result.hasItem() && matches.test(result.getItem())) {
			Mc.click(menu, 0, 0, ContainerInput.QUICK_MOVE);
			return;
		}
		RecipeDisplayEntry e = findRecipe(false);
		if (e == null) {
			if (made > 0) done("crafted " + made + " " + target + " (ran out of ingredients)");
			else fail("missing ingredients for " + target);
			return;
		}
		if (!needsTable && !fits2x2(e.display())) {
			fail(target + " needs a crafting table");
			return;
		}
		Mc.mc().gameMode.handlePlaceRecipe(menu.containerId, e.id(), false);
	}

	@Override
	protected void cleanup() {
		LocalPlayer pl = Mc.player();
		if (pl != null) {
			if (pl.containerMenu != pl.inventoryMenu) {
				pl.closeContainer();
			} else {
				// The 2x2 grid keeps leftovers while no screen is open; move them back to the bag.
				for (int i = 1; i <= 4; i++) {
					if (pl.inventoryMenu.getSlot(i).hasItem()) Mc.click(pl.inventoryMenu, i, 0, ContainerInput.QUICK_MOVE);
				}
			}
		}
		super.cleanup();
	}

	/** A recipe from the player's recipe book that makes the target (craftable now unless anyCraftable). */
	private RecipeDisplayEntry findRecipe(boolean ignoreIngredients) {
		LocalPlayer pl = Mc.player();
		ClientRecipeBook book = pl.getRecipeBook();
		if (book.getCollections().isEmpty()) book.rebuildCollections();
		StackedItemContents stacked = new StackedItemContents();
		pl.getInventory().fillStackedContents(stacked);
		var ctx = SlotDisplayContext.fromLevel(pl.level());
		RecipeDisplayEntry fallback = null;
		for (RecipeCollection c : book.getCollections()) {
			for (RecipeDisplayEntry e : c.getRecipes()) {
				RecipeDisplay d = e.display();
				if (!(d instanceof ShapedCraftingRecipeDisplay) && !(d instanceof ShapelessCraftingRecipeDisplay)) continue;
				ItemStack out = d.result().resolveForFirstStack(ctx);
				if (out.isEmpty() || !matches.test(out)) continue;
				if (e.canCraft(stacked)) return e;
				if (fallback == null) fallback = e;
			}
		}
		return ignoreIngredients ? fallback : null;
	}

	private static boolean fits2x2(RecipeDisplay d) {
		if (d instanceof ShapedCraftingRecipeDisplay s) return s.width() <= 2 && s.height() <= 2;
		if (d instanceof ShapelessCraftingRecipeDisplay s) return s.ingredients().size() <= 4;
		return false;
	}
}
