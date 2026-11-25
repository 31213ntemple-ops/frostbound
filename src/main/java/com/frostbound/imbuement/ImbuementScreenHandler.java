package com.frostbound.imbuement;

import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The imbuement table's menu: a 3×3 grid whose centre slot only takes armour and weapons, and a result
 * slot. It upgrades them to Cold, Icy and Frozen ({@link FrostTiers#STEPS}); taking the result uses up the
 * piece and the step's count from each outer slot. The grid is emptied back to the player on close.
 *
 * Slots: 0 result, 1–9 grid (row by row, 5 is the centre), 10–36 inventory, 37–45 hotbar.
 */
public class ImbuementScreenHandler extends ScreenHandler {
	public static final int RESULT = 0;
	public static final int GRID_START = 1;
	/** Grid index (0–8) of the centre slot. */
	public static final int CENTER = 4;
	public static final int CENTER_SLOT = GRID_START + CENTER;
	private static final int INVENTORY_START = 10;
	private static final int HOTBAR_START = 37;
	private static final int END = 46;

	/** Item positions in the screen: outer slots sit 20 px from the centre (18 px slots + a 2 px gap). */
	public static final int CENTER_X = 48;
	public static final int CENTER_Y = 39;
	public static final int GRID_PITCH = 20;
	public static final int RESULT_X = 124;
	public static final int RESULT_Y = 39;
	/** Where the player's inventory starts (under the ice recess with the grid and the tier ladder). */
	public static final int INVENTORY_Y = 120;

	private final SimpleInventory grid = new SimpleInventory(9);
	private final CraftingResultInventory result = new CraftingResultInventory();
	private final ScreenHandlerContext context;

	/** Client side. */
	public ImbuementScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
	}

	public ImbuementScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
		super(Imbuement.SCREEN_HANDLER, syncId);
		this.context = context;
		grid.addListener(this::onContentChanged);

		addSlot(new ResultSlot(RESULT_X, RESULT_Y));
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				int index = col + row * 3;
				int x = CENTER_X + (col - 1) * GRID_PITCH;
				int y = CENTER_Y + (row - 1) * GRID_PITCH;
				addSlot(index == CENTER ? new ArmorSlot(grid, index, x, y) : new Slot(grid, index, x, y));
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, INVENTORY_Y + 58));
		}
	}

	/** The eight outer stacks, in grid order. */
	private List<ItemStack> ring() {
		List<ItemStack> ring = new ArrayList<>(8);
		for (int i = 0; i < 9; i++) {
			if (i != CENTER) {
				ring.add(grid.getStack(i));
			}
		}
		return ring;
	}

	@Override
	public void onContentChanged(Inventory inventory) {
		// Server only (the client's context is empty); the result slot syncs to the client like any slot.
		context.run((world, pos) -> {
			result.setStack(0, FrostTiers.upgrade(grid.getStack(CENTER), ring()));
			sendContentUpdates();
		});
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		context.run((world, pos) -> dropInventory(player, grid));
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return canUse(context, player, Imbuement.TABLE);
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return slot.inventory != result && super.canInsertIntoSlot(stack, slot);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (slot == null || !slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		if (index == RESULT) {
			if (!insertItem(stack, INVENTORY_START, END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickTransfer(stack, original);
		} else if (index >= INVENTORY_START) {
			// Armour goes to the centre, anything else to the outer slots (the centre turns it away), else across the inventory.
			boolean moved = FrostTiers.canImbue(stack)
					? insertItem(stack, CENTER_SLOT, CENTER_SLOT + 1, false)
					: insertItem(stack, GRID_START, INVENTORY_START, false);
			if (!moved) {
				if (index < HOTBAR_START ? !insertItem(stack, HOTBAR_START, END, false) : !insertItem(stack, INVENTORY_START, HOTBAR_START, false)) {
					return ItemStack.EMPTY;
				}
			}
		} else if (!insertItem(stack, INVENTORY_START, END, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTakeItem(player, stack);
		if (index == RESULT) {
			player.dropItem(stack, false);
		}
		return original;
	}

	/** The centre: one piece of armour or a weapon, darker in the screen, with an empty-chestplate outline when empty. */
	public static class ArmorSlot extends Slot {
		public ArmorSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return FrostTiers.canImbue(stack);
		}

		@Override
		public int getMaxItemCount() {
			return 1;
		}

		@Override
		public Pair<Identifier, Identifier> getBackgroundSprite() {
			return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE);
		}
	}

	/** Shows the upgraded armour; taking it uses up the inputs. */
	private class ResultSlot extends Slot {
		ResultSlot(int x, int y) {
			super(result, 0, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return false;
		}

		@Override
		public void onTakeItem(PlayerEntity player, ItemStack stack) {
			FrostTiers.Step step = FrostTiers.match(grid.getStack(CENTER), ring());
			if (step != null) {
				for (int i = 0; i < 9; i++) {
					grid.removeStack(i, i == CENTER ? 1 : step.count());
				}
				context.run((world, pos) -> {
					world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1.0f, 0.8f + 0.2f * step.to().level());
					world.playSound(null, pos, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.BLOCKS, 0.6f, 1.6f);
					if (world instanceof ServerWorld server) {
						// A puff of frost over the table, bigger for each tier.
						server.spawnParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
								10 * step.to().level(), 0.35, 0.25, 0.35, 0.03);
					}
				});
			}
			super.onTakeItem(player, stack);
		}
	}
}
