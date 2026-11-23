package com.frostbound.vessels;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Hold right click for one permanent heart. There's one of these per heart (VI to XX), used in
 * order. While held it beats, faster and faster; when it finishes it pops up on screen like a totem.
 */
public class RevitalizedVesselItem extends VesselItem {
	/** Ticks into the hold when the vessel beats (sound here, pulse in HeldItemRendererMixin). */
	public static final int[] BEATS = {1, 12, 21, 27, 31};

	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.85f, 0.1f, 0.15f), 0.6f);

	private final int heart;

	public RevitalizedVesselItem(int heart, Item.Settings settings) {
		super(settings);
		this.heart = heart;
	}

	/** Which heart this vessel gives (6 to 20). */
	public int getHeart() {
		return heart;
	}

	@Override
	public int useTicks() {
		return 32;
	}

	@Override
	protected boolean showsPop() {
		return true;
	}

	@Override
	@Nullable
	protected Text refusal(PlayerEntity player) {
		int have = Vessels.hearts(player);
		if (have >= heart) {
			return Text.translatable("frostbound.vessel.already", Vessels.ordinal(heart));
		}
		if (have + 1 < heart) {
			return Text.translatable("frostbound.vessel.need_previous", Vessels.ordinal(heart - 1),
					Vessels.roman(heart - 1));
		}
		return null;
	}

	@Override
	protected void usingEffects(ServerWorld world, PlayerEntity player, int usedTicks) {
		for (int beat = 0; beat < BEATS.length; beat++) {
			if (BEATS[beat] == usedTicks) {
				float progress = beat / (float) (BEATS.length - 1);
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
						SoundCategory.PLAYERS, 0.8f + progress * 0.6f, 0.9f + progress * 0.35f);
				Vec3d hand = FrozenVesselItem.handPos(player);
				world.spawnParticles(BLOOD, hand.x, hand.y, hand.z, 1 + beat / 2, 0.06, 0.06, 0.06, 0.0);
			}
		}
	}

	@Override
	protected void apply(ServerWorld world, PlayerEntity player) {
		Vessels.setHearts(player, heart);
		// The new heart arrives full.
		player.heal(2.0f);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_TOTEM_USE,
				SoundCategory.PLAYERS, 0.35f, 1.5f);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
				SoundCategory.PLAYERS, 1.0f, 1.2f);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
				SoundCategory.PLAYERS, 1.2f, 1.0f);
		// Kept around and above the player, out of their own view in first person.
		world.spawnParticles(ParticleTypes.HEART, player.getX(), player.getY() + player.getHeight() + 0.5, player.getZ(),
				5, 0.6, 0.15, 0.6, 0.0);
		world.spawnParticles(BLOOD, player.getX(), player.getBodyY(0.25), player.getZ(),
				14, 0.55, 0.3, 0.55, 0.0);
		world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getBodyY(0.3), player.getZ(),
				12, 0.6, 0.35, 0.6, 0.04);
		player.sendMessage(Text.translatable("frostbound.vessel.gained", Vessels.ordinal(heart))
				.formatted(Formatting.RED), true);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.frostbound.revitalized_vessel.quote").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		tooltip.add(Text.empty());
		tooltip.add(Text.translatable("frostbound.vessel.when_used").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.frostbound.revitalized_vessel.effect", Vessels.ordinal(heart))
				.formatted(Formatting.RED));
		// Tooltips are only drawn on the client, where this is the local player's hearts.
		int have = Vessels.ClientState.hearts;
		if (have >= heart) {
			tooltip.add(Text.translatable("item.frostbound.revitalized_vessel.claimed").formatted(Formatting.DARK_GRAY));
		} else if (have + 1 < heart) {
			tooltip.add(Text.translatable("item.frostbound.revitalized_vessel.locked", Vessels.roman(heart - 1))
					.formatted(Formatting.DARK_RED));
		} else {
			tooltip.add(Text.translatable("item.frostbound.revitalized_vessel.ready").formatted(Formatting.GREEN));
		}
	}
}
