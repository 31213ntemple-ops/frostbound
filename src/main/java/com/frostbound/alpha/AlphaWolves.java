package com.frostbound.alpha;

import com.frostbound.Frostbound;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The Alpha Wolf boss and everything it brings: the Alpha and its pack ({@link AlphaWolfEntity},
 * {@link PackWolfEntity}), Draining Bite ({@link DrainingBite}), its den (den.WolfDens), and its rewards: Alpha Hide armour ({@link AlphaHide}), Wolf Teeth for Life Steal books ({@link LifeSteal})
 * and the Mark of the Pack necklace ({@link MarkOfThePack}, with the wolves it calls, {@link SummonedWolfEntity}).
 *
 * Always registered (not a module), like the other items, so saved worlds never lose them. Balance is in
 * {@code alphaWolf}, {@code packInstinct}, {@code lifeSteal} and {@code markOfThePack} in config/frostbound.json.
 */
public final class AlphaWolves {
	public static final EntityType<AlphaWolfEntity> ALPHA_WOLF = Registry.register(Registries.ENTITY_TYPE, Frostbound.id("alpha_wolf"),
			EntityType.Builder.create(AlphaWolfEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 0.85f) // a wolf's; GENERIC_SCALE makes it bigger
					.eyeHeight(0.68f)
					.maxTrackingRange(10)
					.build("alpha_wolf"));

	public static final EntityType<PackWolfEntity> PACK_WOLF = Registry.register(Registries.ENTITY_TYPE, Frostbound.id("pack_wolf"),
			EntityType.Builder.<PackWolfEntity>create(PackWolfEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 0.85f)
					.eyeHeight(0.68f)
					.maxTrackingRange(10)
					.build("pack_wolf"));

	public static final EntityType<SummonedWolfEntity> SUMMONED_WOLF = Registry.register(Registries.ENTITY_TYPE, Frostbound.id("summoned_wolf"),
			EntityType.Builder.<SummonedWolfEntity>create(SummonedWolfEntity::new, SpawnGroup.MISC)
					.dimensions(0.6f, 0.85f)
					.eyeHeight(0.68f)
					.maxTrackingRange(10)
					.build("summoned_wolf"));

	/** Draining Bite's effect: max health down for a while. */
	public static final RegistryEntry<StatusEffect> DRAINED = Registry.registerReference(Registries.STATUS_EFFECT,
			Frostbound.id("drained"), new DrainingBite.DrainedEffect());

	public static final Item WOLF_TOOTH = Registry.register(Registries.ITEM, Frostbound.id("wolf_tooth"), new Item(new Item.Settings()));

	public static final Item ALPHA_WOLF_SPAWN_EGG = Registry.register(Registries.ITEM, Frostbound.id("alpha_wolf_spawn_egg"),
			new SpawnEggItem(ALPHA_WOLF, 0x1C1A1D, 0xC0182A, new Item.Settings()));

	private AlphaWolves() {}

	public static void init() {
		// Load the item classes (their static fields register the items, armour, passives and attachments).
		Item hide = AlphaHide.ALPHA_HIDE;
		Item mark = MarkOfThePack.MARK_OF_THE_PACK;

		FabricDefaultAttributeRegistry.register(ALPHA_WOLF, AlphaWolfEntity.createAlphaAttributes());
		FabricDefaultAttributeRegistry.register(PACK_WOLF, PackWolfEntity.createPackWolfAttributes());
		FabricDefaultAttributeRegistry.register(SUMMONED_WOLF, SummonedWolfEntity.createSummonedWolfAttributes());

		DrainingBite.init();
		LifeSteal.init();
		MarkOfThePack.init();
		com.frostbound.alpha.den.WolfDens.init();
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			for (ServerPlayerEntity player : world.getPlayers()) {
				AlphaHide.tick(player);
			}
		});

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.addAfter(Items.WOLF_SPAWN_EGG, ALPHA_WOLF_SPAWN_EGG));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.addAfter(Items.RABBIT_HIDE, hide, WOLF_TOOTH));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.addAfter(Items.CHAINMAIL_BOOTS, AlphaHide.HELMET, AlphaHide.CHESTPLATE, AlphaHide.LEGGINGS, AlphaHide.BOOTS);
			entries.addAfter(Items.TOTEM_OF_UNDYING, mark);
		});
	}
}
