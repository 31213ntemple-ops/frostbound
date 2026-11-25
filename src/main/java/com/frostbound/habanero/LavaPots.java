package com.frostbound.habanero;

import com.frostbound.Frostbound;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Lava in a Farmer's Delight cooking pot:
 *
 * <ul>
 *   <li>Right-click a pot with a lava bucket and the lava goes in (the bucket comes back empty) instead of the
 *       pot's menu opening. An empty bucket takes it back out.</li>
 *   <li>While it holds lava the pot only cooks flame extract (three habaneros, recipe
 *       {@code frostbound:cooking/flame_extract}), and flame extract only cooks in lava. Each flame extract uses the
 *       lava up.</li>
 *   <li>The pot's menu shows a lava bucket next to its fire icon (mixin/habanero/client/CookingPotScreenMixin),
 *       and lava pops out of the pot now and then.</li>
 * </ul>
 *
 * The lava is saved with the pot and sent to clients with its other contents (mixin/habanero). Breaking the pot
 * loses it. Without Farmer's Delight none of this applies (FrostboundMixinPlugin).
 */
public final class LavaPots {
	/** The cooking pot recipe that needs lava (and the only one a lava pot cooks). */
	public static final Identifier FLAME_EXTRACT_RECIPE = Frostbound.id("cooking/flame_extract");
	/** Where the pot keeps its lava in its NBT (saved and sent to clients). */
	public static final String NBT_KEY = "frostbound:lava";

	private LavaPots() {}

	public static void init() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> use(player, world, hand, hit.getBlockPos()));
	}

	/** The pot at {@code pos}, if there's a cooking pot there (and Farmer's Delight is installed). */
	@Nullable
	public static LavaPot pot(World world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof LavaPot pot ? pot : null;
	}

	public static boolean hasLava(World world, BlockPos pos) {
		LavaPot pot = pot(world, pos);
		return pot != null && pot.frostbound$hasLava();
	}

	/** Puts lava in or takes it out of the pot, saving it and telling clients. */
	public static void setLava(World world, BlockPos pos, boolean lava) {
		LavaPot pot = pot(world, pos);
		if (pot == null || pot.frostbound$hasLava() == lava) {
			return;
		}
		pot.frostbound$setLava(lava);
		((BlockEntity) pot).markDirty();
		BlockState state = world.getBlockState(pos);
		world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
	}

	/** Whether a pot with (or without) lava may cook the recipe {@code id}: lava only cooks flame extract, and flame extract only cooks in lava. */
	public static boolean mayCook(boolean lava, Identifier id) {
		return lava == FLAME_EXTRACT_RECIPE.equals(id);
	}

	private static ActionResult use(PlayerEntity player, World world, Hand hand, BlockPos pos) {
		if (player.isSpectator()) {
			return ActionResult.PASS;
		}
		LavaPot pot = pot(world, pos);
		if (pot == null) {
			return ActionResult.PASS;
		}
		ItemStack held = player.getStackInHand(hand);
		boolean lava = pot.frostbound$hasLava();
		if (held.isOf(Items.LAVA_BUCKET) && !lava) {
			if (!world.isClient) {
				player.setStackInHand(hand, ItemUsage.exchangeStack(held, player, new ItemStack(Items.BUCKET)));
				setLava(world, pos, true);
				world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY_LAVA, SoundCategory.BLOCKS, 1.0f, 1.0f);
				world.emitGameEvent(player, GameEvent.FLUID_PLACE, pos);
			}
			return ActionResult.success(world.isClient);
		}
		if (held.isOf(Items.BUCKET) && lava) {
			if (!world.isClient) {
				player.setStackInHand(hand, ItemUsage.exchangeStack(held, player, new ItemStack(Items.LAVA_BUCKET)));
				setLava(world, pos, false);
				world.playSound(null, pos, SoundEvents.ITEM_BUCKET_FILL_LAVA, SoundCategory.BLOCKS, 1.0f, 1.0f);
				world.emitGameEvent(player, GameEvent.FLUID_PICKUP, pos);
			}
			return ActionResult.success(world.isClient);
		}
		return ActionResult.PASS;
	}
}
