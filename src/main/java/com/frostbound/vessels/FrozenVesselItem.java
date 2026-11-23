package com.frostbound.vessels;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Hold right click to take in a {@link FrozenHeart}. The vessel crackles and sheds frost while it
 * thaws into you. Refused while your frozen heart is still whole; a cracked one is made whole again.
 */
public class FrozenVesselItem extends VesselItem {
	public FrozenVesselItem(Item.Settings settings) {
		super(settings);
	}

	@Override
	public int useTicks() {
		return 24;
	}

	@Override
	@Nullable
	protected Text refusal(PlayerEntity player) {
		return FrozenHeart.isWhole(player) ? Text.translatable("frostbound.vessel.frozen_whole") : null;
	}

	@Override
	protected void usingEffects(ServerWorld world, PlayerEntity player, int usedTicks) {
		if (usedTicks % 4 == 1) {
			// Crackling that rises in pitch as the ice gives.
			float progress = usedTicks / (float) useTicks();
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_POWDER_SNOW_STEP,
					SoundCategory.PLAYERS, 0.6f, 0.8f + progress * 0.6f);
			if (usedTicks % 8 == 1) {
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_GLASS_HIT,
						SoundCategory.PLAYERS, 0.35f, 1.4f + progress * 0.4f);
			}
		}
		if (usedTicks % 2 == 0) {
			Vec3d hand = handPos(player);
			world.spawnParticles(ParticleTypes.SNOWFLAKE, hand.x, hand.y, hand.z, 1, 0.08, 0.08, 0.08, 0.01);
		}
	}

	@Override
	protected void apply(ServerWorld world, PlayerEntity player) {
		FrozenHeart.set(player, FrozenHeart.FULL);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
				SoundCategory.PLAYERS, 1.4f, 0.7f);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_GLASS_BREAK,
				SoundCategory.PLAYERS, 0.5f, 1.6f);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE,
				SoundCategory.PLAYERS, 0.6f, 1.2f);
		world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getBodyY(0.35), player.getZ(),
				20, 0.55, 0.4, 0.55, 0.03);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ICE.getDefaultState()),
				player.getX(), player.getBodyY(0.35), player.getZ(), 16, 0.45, 0.3, 0.45, 0.1);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.frostbound.frozen_vessel.quote").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		tooltip.add(Text.empty());
		tooltip.add(Text.translatable("frostbound.vessel.when_used").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.frostbound.frozen_vessel.effect").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("item.frostbound.frozen_vessel.detail").formatted(Formatting.DARK_AQUA));
	}

	/** Roughly where the held vessel is (low, on the side of the arm holding it), for particles. */
	static Vec3d handPos(PlayerEntity player) {
		Vec3d look = player.getRotationVec(1.0f);
		float side = player.getActiveHand() == Hand.OFF_HAND ^ player.getMainArm() == Arm.LEFT ? -1.0f : 1.0f;
		Vec3d right = Vec3d.fromPolar(0.0f, player.getYaw() + 90.0f).multiply(side * 0.35);
		return player.getEyePos().add(look.multiply(0.5)).add(right).add(0.0, -0.45, 0.0);
	}
}
