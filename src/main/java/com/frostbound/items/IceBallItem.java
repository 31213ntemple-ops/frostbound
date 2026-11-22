package com.frostbound.items;

import com.frostbound.config.FrostboundConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ProjectileItem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;
import net.minecraft.world.World;

/** Thrown like a snowball (right-click), with a short cooldown because it hurts. Dispensers fire it too. */
public class IceBallItem extends Item implements ProjectileItem {
	public IceBallItem(Item.Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW,
				SoundCategory.NEUTRAL, 0.5f, 0.3f / (world.getRandom().nextFloat() * 0.4f + 0.8f));
		if (!world.isClient) {
			IceBallEntity ball = new IceBallEntity(world, user);
			ball.setItem(stack);
			ball.setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, 1.5f, 1.0f);
			world.spawnEntity(ball);
		}
		int cooldown = FrostboundConfig.get().iceBalls.cooldownTicks;
		if (cooldown > 0) {
			user.getItemCooldownManager().set(this, cooldown);
		}
		user.incrementStat(Stats.USED.getOrCreateStat(this));
		stack.decrementUnlessCreative(1, user);
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public ProjectileEntity createEntity(World world, Position pos, ItemStack stack, Direction direction) {
		IceBallEntity ball = new IceBallEntity(world, pos.getX(), pos.getY(), pos.getZ());
		ball.setItem(stack);
		return ball;
	}
}
