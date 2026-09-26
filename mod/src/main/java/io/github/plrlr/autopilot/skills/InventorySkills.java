package io.github.plrlr.autopilot.skills;

import io.github.plrlr.autopilot.Items2;
import io.github.plrlr.autopilot.Mc;
import io.github.plrlr.autopilot.plan.Planner;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Skills that mostly shuffle the inventory: eat, equip, place. */
public final class InventorySkills {
	private InventorySkills() {}

	/** eat: hold the best food and keep right-click down until it's eaten. */
	public static final class Eat extends Skill {
		private String food;
		private int before;

		@Override
		public String name() {
			return "eat";
		}

		@Override
		public boolean interruptible() {
			return false;
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 5;
			LocalPlayer pl = Mc.player();
			if (!pl.getFoodData().needsFood()) {
				done("not hungry");
				return;
			}
			ItemStack best = null;
			int bestN = -1;
			for (int i = 0; i < 36; i++) {
				ItemStack s = pl.getInventory().getItem(i);
				FoodProperties f = Items2.food(s);
				if (f == null || !Items2.isAnyFood(s)) continue;
				// Prefer good food; poisonous food only if nothing else.
				int score = f.nutrition() + (Items2.isGoodFood(s) ? 100 : 0);
				if (score > bestN) {
					bestN = score;
					best = s;
				}
			}
			if (best == null) {
				fail("no food");
				return;
			}
			food = Items2.id(best);
			before = Mc.count(food);
			Bari.stop();
			if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
			Mc.holdItem(Items2.matcher(food));
			// Right-click eats only if we're not pointing at a furnace, table or animal (that
			// would open it or feed it instead). Looking straight up avoids them.
			pl.setXRot(-90f);
		}

		@Override
		protected void tick() {
			if (Mc.count(food) < before) {
				done("ate " + food);
				return;
			}
			if (ticks < 3) return;
			// Held use key = the same as a player holding right-click.
			Mc.mc().options.keyUse.setDown(true);
		}
	}

	/** equip armor|shield|weapon|pickaxe. */
	public static final class Equip extends Skill {
		private int actions;

		@Override
		public String name() {
			return "equip";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 8;
			LocalPlayer pl = Mc.player();
			if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
			String what = arg == null ? "armor" : arg;
			switch (what) {
				case "shield" -> {
					int inv = Mc.findSlot(s -> Items2.id(s).equals("shield"));
					if (inv < 0) {
						fail("no shield");
						return;
					}
					// SWAP with button 40 moves the stack to the off hand.
					Mc.click(pl.inventoryMenu, Mc.menuSlotFor(pl.inventoryMenu, inv), 40, ContainerInput.SWAP);
					done("shield in off hand");
				}
				case "weapon", "sword" -> holdBest("sword", "axe");
				case "pickaxe" -> holdBest("pickaxe", null);
				case "axe" -> holdBest("axe", null);
				case "armor" -> {
				}
				default -> fail("unknown equip target " + what);
			}
		}

		private void holdBest(String type, String alt) {
			String best = bestOf(type);
			if (best == null && alt != null) best = bestOf(alt);
			if (best == null) {
				fail("no " + type);
				return;
			}
			String id = best;
			Mc.holdItem(s -> Items2.id(s).equals(id));
			done("holding " + id);
		}

		private static String bestOf(String type) {
			String best = null;
			int tier = -2;
			for (int i = 0; i < 36; i++) {
				String id = Items2.id(Mc.player().getInventory().getItem(i));
				if (id.endsWith("_" + type) && Items2.tier(id) > tier) {
					tier = Items2.tier(id);
					best = id;
				}
			}
			return best;
		}

		@Override
		protected void tick() {
			if (ticks % 4 != 0) return;
			LocalPlayer pl = Mc.player();
			String[] kinds = {"helmet", "chestplate", "leggings", "boots"};
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			for (int k = 0; k < 4; k++) {
				ItemStack worn = pl.getItemBySlot(slots[k]);
				int wornTier = worn.isEmpty() ? -2 : Planner.armorTier(Items2.id(worn));
				int bestInv = -1, bestTier = wornTier;
				for (int i = 0; i < 36; i++) {
					String id = Items2.id(pl.getInventory().getItem(i));
					if (id.endsWith("_" + kinds[k]) && Planner.armorTier(id) > bestTier) {
						bestTier = Planner.armorTier(id);
						bestInv = i;
					}
				}
				if (bestInv < 0) continue;
				if (++actions > 12) {
					fail("armor wouldn't move");
					return;
				}
				// Inventory menu slots 5-8 are head, chest, legs, feet.
				if (!worn.isEmpty()) {
					if (pl.getInventory().getFreeSlot() < 0) {
						fail("inventory full, can't swap armor");
						return;
					}
					Mc.click(pl.inventoryMenu, 5 + k, 0, ContainerInput.QUICK_MOVE);
				} else {
					Mc.click(pl.inventoryMenu, Mc.menuSlotFor(pl.inventoryMenu, bestInv), 0, ContainerInput.QUICK_MOVE);
				}
				return;
			}
			done("wearing the best armor");
		}
	}

	/** place <block item>: put a crafting table, furnace, chest or torch down next to the player. */
	public static final class Place extends Skill {
		private BlockPos spot;
		private String block;

		@Override
		public String name() {
			return "place";
		}

		@Override
		protected void start() {
			timeoutTicks = 20 * 5;
			block = arg == null ? "" : arg;
			if (Mc.count(block) == 0) {
				fail("no " + block + " to place");
				return;
			}
			Bari.stop();
			LocalPlayer pl = Mc.player();
			if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
			spot = Station.findSpot(pl);
			if (spot == null) {
				fail("no room to place " + block);
				return;
			}
			Mc.holdItem(Items2.matcher(block));
		}

		@Override
		protected void tick() {
			if (ticks == 2) Mc.placeAt(spot);
			if (ticks > 6) {
				String now = Mc.id(Mc.state(spot).getBlock());
				if (now.equals(block) || now.contains(block.replace("_bed", ""))) {
					memory.remember(block, spot, now);
					done("placed " + block);
				} else {
					fail("the " + block + " didn't place");
				}
			}
		}
	}
}
