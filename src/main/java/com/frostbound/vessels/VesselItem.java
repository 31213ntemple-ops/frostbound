package com.frostbound.vessels;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A vessel you hold right click on. While held it plays its own effects ({@link #usingEffects});
 * letting go early cancels without using it up. When the hold finishes it's applied and used up.
 *
 * The client draws the vessel being drawn in toward you while it's used (HeldItemRendererMixin).
 */
public abstract class VesselItem extends Item {
	protected VesselItem(Item.Settings settings) {
		super(settings);
	}

	/** How long right click has to be held, in ticks. */
	public abstract int useTicks();

	/** Why the player can't use this right now (shown above the hotbar), or null if they can. */
	@Nullable
	protected abstract Text refusal(PlayerEntity player);

	/** Server side, every tick while held. {@code usedTicks} counts up from 1. */
	protected abstract void usingEffects(ServerWorld world, PlayerEntity player, int usedTicks);

	/** Server side, once the hold finishes. The stack is used up afterwards. */
	protected abstract void apply(ServerWorld world, PlayerEntity player);

	/** Whether finishing shows the vessel popping up on screen, like a totem. */
	protected boolean showsPop() {
		return false;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		Text refusal = refusal(user);
		if (refusal != null) {
			if (!world.isClient) {
				user.sendMessage(refusal, true);
			}
			return TypedActionResult.fail(stack);
		}
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.NONE;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return useTicks();
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (world instanceof ServerWorld serverWorld && user instanceof PlayerEntity player) {
			usingEffects(serverWorld, player, useTicks() - remainingUseTicks + 1);
		}
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!(user instanceof PlayerEntity player)) {
			return stack;
		}
		// Things can change during the hold (another vessel used from the other hand, damage...).
		Text refusal = refusal(player);
		if (refusal != null) {
			if (!world.isClient) {
				player.sendMessage(refusal, true);
			}
			return stack;
		}
		if (world instanceof ServerWorld serverWorld) {
			apply(serverWorld, player);
		} else if (showsPop()) {
			Vessels.clientUseEffect.accept(player, stack.copyWithCount(1));
		}
		player.incrementStat(Stats.USED.getOrCreateStat(this));
		stack.decrementUnlessCreative(1, player);
		return stack;
	}
}
