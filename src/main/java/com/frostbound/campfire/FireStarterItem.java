package com.frostbound.campfire;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Two sticks, rubbed together. Held on a campfire base (or an unlit campfire), it starts the
 * fire-starting minigame; see {@link Campfires}. Uses the brush's back-and-forth hand animation.
 */
public class FireStarterItem extends Item {
	public FireStarterItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		BlockPos pos = context.getBlockPos();
		if (player == null || !Campfires.canLight(context.getWorld().getBlockState(pos))) {
			return ActionResult.PASS;
		}
		Campfires.startSession(player, pos);
		player.setCurrentHand(context.getHand());
		return ActionResult.CONSUME;
	}

	/** Stops when the player looks away from the fire, or it can't be lit any more. */
	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(user instanceof PlayerEntity player)) {
			user.stopUsingItem();
			return;
		}
		BlockPos target = Campfires.target(player);
		HitResult hit = player.raycast(player.getBlockInteractionRange(), 0.0f, false);
		if (target == null || !(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
				|| !blockHit.getBlockPos().equals(target) || !Campfires.canLight(world.getBlockState(target))) {
			user.stopUsingItem();
		}
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (user instanceof PlayerEntity player) {
			Campfires.endSession(player);
		}
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BRUSH;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.frostbound.fire_starter.hint").formatted(Formatting.GRAY));
	}
}
