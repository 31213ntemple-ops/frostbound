package com.frostbound.vessels;

import com.frostbound.Frostbound;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;

/**
 * The frozen heart from a Frozen Vessel: one extra heart (2 health) that sits outside your other
 * hearts. It takes damage before anything else (golden-apple absorption included), never heals,
 * and shatters when it breaks. It's lost on death.
 *
 * It's its own pool, not absorption, so golden apples keep working (and stay gold) and their
 * leftover absorption can never turn into a free frozen heart.
 */
public final class FrozenHeart {
	/** A whole frozen heart, in health points. */
	public static final float FULL = 2.0f;

	/** Below this it counts as gone (floating-point leftovers from fractional damage). */
	private static final float EPSILON = 0.001f;

	public static final AttachmentType<Float> FROZEN_HEART = AttachmentRegistry.<Float>builder()
			.persistent(Codec.floatRange(0.0f, FULL))
			.initializer(() -> 0.0f)
			.buildAndRegister(Frostbound.id("frozen_heart"));

	private FrozenHeart() {}

	/** How much of the frozen heart is left (0 to 2). On the client, only correct for the local player. */
	public static float get(PlayerEntity player) {
		if (player.getWorld().isClient) {
			return Vessels.ClientState.frozenHeart;
		}
		return player.getAttachedOrElse(FROZEN_HEART, 0.0f);
	}

	public static boolean isWhole(PlayerEntity player) {
		return get(player) >= FULL - EPSILON;
	}

	/** Sets the frozen heart (server side) and tells the client. */
	public static void set(PlayerEntity player, float amount) {
		float clamped = amount < EPSILON ? 0.0f : Math.min(FULL, amount);
		if (clamped == 0.0f) {
			player.removeAttached(FROZEN_HEART);
		} else {
			player.setAttached(FROZEN_HEART, clamped);
		}
		Vessels.sync(player);
	}

	/**
	 * Lets the frozen heart soak up damage (after armour and resistance, before absorption and
	 * health). Returns what's left over. Called from PlayerEntityMixin on the server.
	 */
	public static float absorb(PlayerEntity player, DamageSource source, float amount) {
		if (amount <= 0.0f || player.getWorld().isClient || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		float ice = get(player);
		if (ice <= 0.0f) {
			return amount;
		}
		float taken = Math.min(ice, amount);
		float left = ice - taken;
		set(player, left);
		player.increaseStat(Stats.DAMAGE_ABSORBED, Math.round(taken * 10.0f));
		if (left < EPSILON) {
			shatterEffects(player);
		} else {
			crackEffects(player);
		}
		return amount - taken;
	}

	private static void crackEffects(PlayerEntity player) {
		if (!(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_GLASS_HIT,
				SoundCategory.PLAYERS, 1.0f, 1.3f);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT,
				SoundCategory.PLAYERS, 1.2f, 0.7f);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ICE.getDefaultState()),
				player.getX(), player.getBodyY(0.6), player.getZ(), 10, 0.25, 0.3, 0.25, 0.05);
	}

	private static void shatterEffects(PlayerEntity player) {
		if (!(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		float pitch = 0.9f + player.getRandom().nextFloat() * 0.2f;
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_GLASS_BREAK,
				SoundCategory.PLAYERS, 1.0f, pitch);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK,
				SoundCategory.PLAYERS, 1.0f, 0.6f);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ICE.getDefaultState()),
				player.getX(), player.getBodyY(0.6), player.getZ(), 36, 0.3, 0.4, 0.3, 0.15);
		world.spawnParticles(ParticleTypes.SNOWFLAKE,
				player.getX(), player.getBodyY(0.6), player.getZ(), 14, 0.3, 0.4, 0.3, 0.06);
	}
}
