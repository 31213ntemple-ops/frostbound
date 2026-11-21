package com.frostbound.mobs;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.GameRules;

/**
 * Module: Frozen Mobs. Some hostile mobs spawn Frozen (see README).
 * Behaviour lives in com.frostbound.mixin.mobs; the mixins check {@link #isEnabled()}.
 */
public final class FrozenMobs {
	/** Which mob types can roll Frozen on spawn. Edit data/frostbound/tags/entity_type/can_be_frozen.json. */
	public static final TagKey<EntityType<?>> CAN_BE_FROZEN =
			TagKey.of(RegistryKeys.ENTITY_TYPE, Frostbound.id("can_be_frozen"));

	/** Extra drops for Frozen mobs, rolled on top of their normal loot. */
	public static final RegistryKey<LootTable> FROZEN_LOOT =
			RegistryKey.of(RegistryKeys.LOOT_TABLE, Frostbound.id("entities/frozen_mob"));

	/** % chance for an eligible mob to spawn Frozen. Change in-game: /gamerule frostboundSpawnChance 100 */
	public static GameRules.Key<GameRules.IntRule> SPAWN_CHANCE;

	/** % chance used instead when the mob spawns in a cold biome (snowy plains, frozen peaks, etc). */
	public static GameRules.Key<GameRules.IntRule> COLD_BIOME_CHANCE;

	private FrozenMobs() {}

	public static boolean isEnabled() {
		return FrostboundConfig.get().modules.frozenMobs;
	}

	public static void init() {
		SPAWN_CHANCE = GameRuleRegistry.register(
				"frostboundSpawnChance", GameRules.Category.MOBS, GameRuleFactory.createIntRule(8, 0, 100));
		COLD_BIOME_CHANCE = GameRuleRegistry.register(
				"frostboundColdBiomeChance", GameRules.Category.MOBS, GameRuleFactory.createIntRule(30, 0, 100));
	}
}
