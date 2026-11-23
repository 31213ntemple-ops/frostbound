package com.frostbound.client.imbuement;

import com.frostbound.imbuement.FrostTier;
import com.frostbound.imbuement.FrostTiers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;

/**
 * Players in Icy armour now and then shed a snowflake; in Frozen armour, a little more often, with the odd
 * glint of ice. Cold armour sheds none. Client only, purely for looks, and not around your own body in
 * first person so it never gets in the way.
 */
public final class FrostParticles {
	/** Chance per tick per piece. Four Frozen pieces: a flake about every second. */
	private static final float ICY_CHANCE = 0.006f;
	private static final float FROZEN_CHANCE = 0.014f;

	private FrostParticles() {}

	public static void init() {
		ClientTickEvents.END_WORLD_TICK.register(FrostParticles::tick);
	}

	private static void tick(ClientWorld world) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.isPaused()) {
			return;
		}
		Random random = world.getRandom();
		for (AbstractClientPlayerEntity player : world.getPlayers()) {
			if (player.isInvisible() || (player == client.player && client.options.getPerspective().isFirstPerson())) {
				continue;
			}
			for (ItemStack stack : player.getArmorItems()) {
				FrostTier tier = FrostTiers.tier(stack);
				float chance = tier == FrostTier.FROZEN ? FROZEN_CHANCE : tier == FrostTier.ICY ? ICY_CHANCE : 0.0f;
				if (chance > 0.0f && random.nextFloat() < chance) {
					double x = player.getX() + (random.nextDouble() - 0.5) * player.getWidth() * 1.2;
					double y = player.getY() + random.nextDouble() * player.getHeight() * 0.9;
					double z = player.getZ() + (random.nextDouble() - 0.5) * player.getWidth() * 1.2;
					world.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0.0, -0.02, 0.0);
					if (tier == FrostTier.FROZEN && random.nextFloat() < 0.3f) {
						world.addParticle(ParticleTypes.WAX_OFF, x, y, z, 0.0, 0.0, 0.0);
					}
				}
			}
		}
	}
}
