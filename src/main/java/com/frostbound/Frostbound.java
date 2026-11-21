package com.frostbound;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entrypoint. Holds the shared constants (tag, loot table, game rules).
 * All the actual behaviour lives in the mixins under com.frostbound.mixin.
 */
public class Frostbound implements ModInitializer {
	public static final String MOD_ID = "frostbound";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Which mob types can roll Frozen on spawn. Edit data/frostbound/tags/entity_type/can_be_frozen.json. */
	public static final TagKey<EntityType<?>> CAN_BE_FROZEN =
			TagKey.of(RegistryKeys.ENTITY_TYPE, id("can_be_frozen"));

	/** Extra drops for Frozen mobs, rolled on top of their normal loot. */
	public static final RegistryKey<LootTable> FROZEN_LOOT =
			RegistryKey.of(RegistryKeys.LOOT_TABLE, id("entities/frozen_mob"));

	/** % chance for an eligible mob to spawn Frozen. Change in-game: /gamerule frostboundSpawnChance 100 */
	public static final GameRules.Key<GameRules.IntRule> SPAWN_CHANCE = GameRuleRegistry.register(
			"frostboundSpawnChance", GameRules.Category.MOBS, GameRuleFactory.createIntRule(8, 0, 100));

	/** % chance used instead when the mob spawns in a cold biome (snowy plains, frozen peaks, etc). */
	public static final GameRules.Key<GameRules.IntRule> COLD_BIOME_CHANCE = GameRuleRegistry.register(
			"frostboundColdBiomeChance", GameRules.Category.MOBS, GameRuleFactory.createIntRule(30, 0, 100));

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Frostbound loaded — stay warm.");
	}
}
