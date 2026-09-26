package io.github.plrlr.autopilot;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Small helpers over Minecraft's client API. Everything here must run on the client thread. */
public final class Mc {
	private Mc() {}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static LocalPlayer player() {
		return mc().player;
	}

	public static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	public static String id(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	public static String id(Entity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
	}

	public static Item item(String path) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(path)).orElse(null);
	}

	public static Block block(String path) {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(path)).orElse(null);
	}

	/** Client-side chat line only the user sees (never sent to the world). */
	public static void say(String text) {
		if (mc().gui != null) mc().gui.chatListener().handleSystemMessage(Component.literal("[Autopilot] " + text), false);
	}

	// ---- inventory ----

	/** Count across the 36 main slots, the offhand, and the cursor-free armor slots are excluded on purpose. */
	public static int count(Predicate<ItemStack> p) {
		LocalPlayer pl = player();
		if (pl == null) return 0;
		int n = 0;
		for (ItemStack s : pl.getInventory().getNonEquipmentItems()) if (!s.isEmpty() && p.test(s)) n += s.getCount();
		ItemStack off = pl.getOffhandItem();
		if (!off.isEmpty() && p.test(off)) n += off.getCount();
		return n;
	}

	public static int count(String itemOrGroup) {
		return count(Items2.matcher(itemOrGroup));
	}

	/** Index (0-35) of the first main-inventory stack matching p, or -1. Prefers the hotbar. */
	public static int findSlot(Predicate<ItemStack> p) {
		Inventory inv = player().getInventory();
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && p.test(s)) return i;
		}
		return -1;
	}

	/** Menu slot index that shows player inventory slot invIndex (0-35) in the given menu, or -1. */
	public static int menuSlotFor(AbstractContainerMenu menu, int invIndex) {
		Inventory inv = player().getInventory();
		for (Slot s : menu.slots) if (s.container == inv && s.getContainerSlot() == invIndex) return s.index;
		return -1;
	}

	public static void click(AbstractContainerMenu menu, int slot, int button, ContainerInput input) {
		mc().gameMode.handleContainerInput(menu.containerId, slot, button, input, player());
	}

	/**
	 * Puts a stack matching p in the hotbar and selects it. Returns false if there is none.
	 * Uses the player's own inventory menu, so any other container must be closed first.
	 */
	public static boolean holdItem(Predicate<ItemStack> p) {
		LocalPlayer pl = player();
		Inventory inv = pl.getInventory();
		int slot = findSlot(p);
		if (slot < 0) return false;
		if (slot < 9) {
			inv.setSelectedSlot(slot);
			return true;
		}
		if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
		int hotbar = inv.getSelectedSlot();
		// Swap into the selected hotbar slot (button = hotbar index), like pressing 1-9 over a slot.
		int menuSlot = menuSlotFor(pl.inventoryMenu, slot);
		if (menuSlot < 0) return false;
		click(pl.inventoryMenu, menuSlot, hotbar, ContainerInput.SWAP);
		return true;
	}

	// ---- looking and using ----

	public static void lookAt(Vec3 target) {
		player().lookAt(EntityAnchorArgument.Anchor.EYES, target);
	}

	public static void swing() {
		player().swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
	}

	public static double reach() {
		return 4.5;
	}

	/** Right-clicks a block face, like a player does to open, place against, or use it. */
	public static boolean useOn(BlockPos pos, Direction face) {
		Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		lookAt(hit);
		InteractionResult r = mc().gameMode.useItemOn(player(), InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
		if (r.consumesAction()) swing();
		return r.consumesAction();
	}

	public static boolean useItem() {
		InteractionResult r = mc().gameMode.useItem(player(), InteractionHand.MAIN_HAND);
		return r.consumesAction();
	}

	public static boolean canSee(BlockPos pos) {
		LocalPlayer pl = player();
		Vec3 eye = pl.getEyePosition();
		Vec3 target = Vec3.atCenterOf(pos);
		BlockHitResult r = pl.level().clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, pl));
		return r.getType() == HitResult.Type.MISS || r.getBlockPos().equals(pos);
	}

	public static boolean canSee(Entity e) {
		LocalPlayer pl = player();
		Vec3 eye = pl.getEyePosition();
		Vec3 target = e.getBoundingBox().getCenter();
		BlockHitResult r = pl.level().clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, pl));
		return r.getType() == HitResult.Type.MISS;
	}

	public static BlockState state(BlockPos pos) {
		return player().level().getBlockState(pos);
	}

	/** A block you can stand on / place against. */
	public static boolean solid(BlockPos pos) {
		BlockState s = state(pos);
		return s.isSolid() && !s.liquid();
	}

	/** A block can't be placed where the player's own body is (the hitbox is wider than one block's center). */
	public static boolean clearOfPlayer(BlockPos pos) {
		return !player().getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos));
	}

	public static boolean free(BlockPos pos) {
		BlockState s = state(pos);
		return (s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty();
	}

	/**
	 * Places the held block into an empty spot by clicking the face of a solid neighbor.
	 * Returns true if the game accepted the click.
	 */
	public static boolean placeAt(BlockPos target) {
		for (Direction d : Direction.values()) {
			BlockPos against = target.relative(d);
			if (solid(against) && !isInteractive(against)) {
				return useOn(against, d.getOpposite());
			}
		}
		return false;
	}

	/** Clicking these opens a screen instead of placing against them. */
	public static boolean isInteractive(BlockPos pos) {
		String b = id(state(pos).getBlock());
		return b.contains("chest") || b.contains("table") || b.contains("furnace") || b.endsWith("_bed")
				|| b.contains("door") || b.contains("barrel") || b.contains("anvil") || b.contains("smoker")
				|| b.contains("shulker") || b.contains("button") || b.contains("lever") || b.contains("gate");
	}

	public static boolean isNight() {
		long t = player().level().getOverworldClockTime() % 24000L;
		return t >= 12900 && t <= 23100;
	}

	public static String dimension() {
		return player().level().dimension().identifier().getPath();
	}
}
