package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.Tune;
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
	/** No recipe-book entry (locked until an unlock trigger): lay the pattern out by hand. */
	private Manual manual;
	private int manualWait;
	/** Each pattern letter's ingredient; planks narrowed to one wood (a boat needs 5 of the same). */
	private final java.util.Map<Character, Predicate<ItemStack>> manualWant = new java.util.HashMap<>();

	/**
	 * Hand-laid recipes for the route's crafts whose recipe-book entry may still be locked (a boat
	 * unlocks on first entering water; the stronghold test failed "no known recipe for
	 * blaze_powder"). Rows of a 3x3 grid; each letter is an item group (Items2.matcher).
	 */
	record Manual(String[] rows, java.util.Map<Character, String> keys) {
		int needed(char k) {
			int n = 0;
			for (String r : rows) for (char c : r.toCharArray()) if (c == k) n++;
			return n;
		}
	}

	private static final java.util.Map<String, Manual> MANUAL = java.util.Map.of(
			"boat", new Manual(new String[]{"P.P", "PPP"}, java.util.Map.of('P', "planks")),
			"blaze_powder", new Manual(new String[]{"R"}, java.util.Map.of('R', "blaze_rod")),
			"ender_eye", new Manual(new String[]{"EB"}, java.util.Map.of('E', "ender_pearl", 'B', "blaze_powder")),
			"gold_ingot", new Manual(new String[]{"NNN", "NNN", "NNN"}, java.util.Map.of('N', "gold_nugget")),
			"golden_helmet", new Manual(new String[]{"GGG", "G.G"}, java.util.Map.of('G', "gold_ingot")));
	private static final Manual TORCH = new Manual(new String[]{"C", "S"}, java.util.Map.of('C', "coal", 'S', "stick"));

	private static boolean haveFor(Manual m) {
		for (var e : m.keys().entrySet()) if (Mc.count(Items2.matcher(e.getValue())) < m.needed(e.getKey())) return false;
		return true;
	}

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
		Manual fallback = target.equals("torch") && Tune.on("cave.torch_recipe") ? TORCH : MANUAL.get(target);
		if (e == null && findRecipe(true) == null && fallback != null && haveFor(fallback)) {
			// Locked in the recipe book: lay out the vanilla pattern with the player's hands.
			manual = fallback;
			for (var k : manual.keys().entrySet()) {
				Predicate<ItemStack> m = Items2.matcher(k.getValue());
				if (k.getValue().equals("planks")) {
					String most = null;
					for (int i = 0; i < 36; i++) {
						String id = Items2.id(Mc.player().getInventory().getItem(i));
						if (id.endsWith("_planks") && (most == null || Mc.count(id) > Mc.count(most))) most = id;
					}
					if (most == null || Mc.count(most) < manual.needed(k.getKey())) {
						fail(Fail.NEED_ITEM, "need " + manual.needed(k.getKey()) + " planks of one wood for a " + target);
						return;
					}
					String one = most;
					m = st -> Items2.id(st).equals(one);
				}
				manualWant.put(k.getKey(), m);
			}
			needsTable = !target.equals("torch");
			if (needsTable) station = new Station("crafting_table", CraftingMenu.class, memory);
			else if (Mc.player().containerMenu != Mc.player().inventoryMenu) Mc.player().closeContainer();
			return;
		}
		if (e == null) {
			fail(findRecipe(true) == null ? Fail.NO_RECIPE : Fail.NEED_ITEM, findRecipe(true) == null ? "no known recipe for " + target + " (not unlocked yet?)" : "missing ingredients for " + target);
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
			if (station.error != null) fail(station.errorCode, station.error);
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
		if (manual != null) {
			manualStep(menu, made);
			return;
		}
		RecipeDisplayEntry e = findRecipe(false);
		if (e == null) {
			if (made > 0) done("crafted " + made + " " + target + " (ran out of ingredients)");
			else fail(Fail.NEED_ITEM, "missing ingredients for " + target);
			return;
		}
		if (!needsTable && !fits2x2(e.display())) {
			fail(Fail.NEED_ITEM, target + " needs a crafting table");
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

	/**
	 * One click of laying the pattern out by hand: pick up a stack of the next missing ingredient,
	 * right-click one into its grid slot (table slots 1-9, row by row), put the rest back; when
	 * the grid is full, the result appears in slot 0 and the caller takes it.
	 */
	private void manualStep(AbstractContainerMenu menu, int made) {
		ItemStack carried = menu.getCarried();
		for (int r = 0; r < manual.rows().length; r++) {
			String row = manual.rows()[r];
			for (int c = 0; c < row.length(); c++) {
				char k = row.charAt(c);
				if (k == '.') continue;
				int slot = 1 + r * (needsTable ? 3 : 2) + c;
				if (menu.getSlot(slot).hasItem()) continue;
				Predicate<ItemStack> want = manualWant.get(k);
				if (!carried.isEmpty() && want.test(carried)) {
					Mc.click(menu, slot, 1, ContainerInput.PICKUP); // right click: one item
					return;
				}
				if (!carried.isEmpty()) {
					putBack(menu, needsTable);
					return;
				}
				for (Slot s : menu.slots) {
					if (s.index >= (needsTable ? 10 : 9) && s.hasItem() && want.test(s.getItem())) {
						Mc.click(menu, s.index, 0, ContainerInput.PICKUP);
						return;
					}
				}
				if (made > 0) done("crafted " + made + " " + target + " by hand (ran out)");
				else fail(Fail.NEED_ITEM, "missing " + manual.keys().get(k) + " for " + target);
				return;
			}
		}
		if (!carried.isEmpty()) {
			putBack(menu, needsTable);
			return;
		}
		// Grid full: the result shows up once the server has it.
		if (++manualWait > 30) fail(Fail.USE_FAILED, "laid out " + target + " by hand but no result appeared");
	}

	/** Carried leftovers back into the bag: onto a matching stack, else an empty slot. */
	private static void putBack(AbstractContainerMenu menu, boolean table) {
		ItemStack carried = menu.getCarried();
		for (Slot s : menu.slots)
			if (s.index >= (table ? 10 : 9) && s.hasItem() && ItemStack.isSameItemSameComponents(s.getItem(), carried)
					&& s.getItem().getCount() < s.getItem().getMaxStackSize()) {
				Mc.click(menu, s.index, 0, ContainerInput.PICKUP);
				return;
			}
		for (Slot s : menu.slots)
			if (s.index >= (table ? 10 : 9) && !s.hasItem()) {
				Mc.click(menu, s.index, 0, ContainerInput.PICKUP);
				return;
			}
	}

	private static boolean fits2x2(RecipeDisplay d) {
		if (d instanceof ShapedCraftingRecipeDisplay s) return s.width() <= 2 && s.height() <= 2;
		if (d instanceof ShapelessCraftingRecipeDisplay s) return s.ingredients().size() <= 4;
		return false;
	}
}
