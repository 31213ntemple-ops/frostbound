package com.frostbound.items;

import com.frostbound.Frostbound;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.DispenserBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

/**
 * Frostbound's items and the entities they throw. Always registered (not a switchable
 * module), so client and server registries always match and saved items never vanish.
 */
public final class FrostboundItems {
	public static final Item ICE_BALL = Registry.register(Registries.ITEM, Frostbound.id("ice_ball"),
			new IceBallItem(new Item.Settings().maxCount(16)));

	public static final EntityType<IceBallEntity> ICE_BALL_ENTITY = Registry.register(Registries.ENTITY_TYPE,
			Frostbound.id("ice_ball"),
			EntityType.Builder.<IceBallEntity>create(IceBallEntity::new, SpawnGroup.MISC)
					.dimensions(0.25f, 0.25f)
					.maxTrackingRange(4)
					.trackingTickInterval(10)
					.build("ice_ball"));

	/** Mobs that take extra damage from ice balls (blazes, magma cubes, striders). */
	public static final TagKey<EntityType<?>> WEAK_TO_ICE =
			TagKey.of(RegistryKeys.ENTITY_TYPE, Frostbound.id("weak_to_ice"));

	private FrostboundItems() {}

	public static void init() {
		DispenserBlock.registerProjectileBehavior(ICE_BALL);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.addAfter(Items.SNOWBALL, ICE_BALL));
	}
}
