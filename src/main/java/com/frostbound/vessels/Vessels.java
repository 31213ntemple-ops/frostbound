package com.frostbound.vessels;

import com.frostbound.Frostbound;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Hearts. Players start with {@link #START_HEARTS} and can grow to {@link #MAX_HEARTS}:
 *
 *  - Frozen Flesh (ice in rotten flesh, or dropped by Frozen zombies) makes a Frozen Vessel: one
 *    {@link FrozenHeart}, an ice heart drawn after your other hearts that takes damage first, never
 *    heals, and shatters when it breaks.
 *  - A Frozen Vessel surrounded by tougher and tougher materials makes a Revitalized Vessel, one
 *    for each permanent heart from the 6th (VI) to the 20th (XX). They have to be used in order,
 *    so the materials double as a quest book.
 *
 * Permanent hearts are saved on the player and kept through death; max health is recomputed from
 * them on join and respawn. The server tells the client its hearts and frozen heart with
 * {@link VesselSyncPayload} (for the HUD and for tooltips and use checks).
 *
 * Always registered, like the other items.
 */
public final class Vessels {
	public static final int START_HEARTS = 5;
	public static final int MAX_HEARTS = 20;

	/** Hearts gained from Revitalized Vessels (0 to 15). */
	public static final AttachmentType<Integer> BONUS_HEARTS = AttachmentRegistry.<Integer>builder()
			.persistent(Codec.intRange(0, MAX_HEARTS - START_HEARTS))
			.copyOnDeath()
			.initializer(() -> 0)
			.buildAndRegister(Frostbound.id("revitalized_hearts"));

	public static final Item FROZEN_FLESH = Registry.register(Registries.ITEM, Frostbound.id("frozen_flesh"),
			new Item(new Item.Settings()));

	public static final Item FROZEN_VESSEL = Registry.register(Registries.ITEM, Frostbound.id("frozen_vessel"),
			new FrozenVesselItem(new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));

	/** Index 0 is the vessel for the 6th heart; index 14 is the one for the 20th. */
	private static final List<Item> REVITALIZED = new ArrayList<>();

	static {
		for (int heart = START_HEARTS + 1; heart <= MAX_HEARTS; heart++) {
			// The last few (totems, netherite, nether stars) are the hardest to make.
			Rarity rarity = heart >= 18 ? Rarity.EPIC : Rarity.RARE;
			REVITALIZED.add(Registry.register(Registries.ITEM, Frostbound.id("revitalized_vessel_" + heart),
					new RevitalizedVesselItem(heart, new Item.Settings().maxCount(1).rarity(rarity))));
		}
	}

	/**
	 * What the client last heard from the server (see {@link VesselSyncPayload}). Only read on the
	 * client, for the local player; the server always reads the player's attachments.
	 */
	public static final class ClientState {
		public static int hearts = START_HEARTS;
		public static float frozenHeart = 0.0f;

		private ClientState() {}

		public static void reset() {
			hearts = START_HEARTS;
			frozenHeart = 0.0f;
		}
	}

	/**
	 * Client-only effect when the local player finishes using a vessel (the totem-style pop).
	 * Set by the client entrypoint; does nothing on a dedicated server.
	 */
	public static BiConsumer<PlayerEntity, ItemStack> clientUseEffect = (player, stack) -> {};

	private Vessels() {}

	public static Item revitalized(int heart) {
		return REVITALIZED.get(heart - START_HEARTS - 1);
	}

	public static List<Item> allRevitalized() {
		return List.copyOf(REVITALIZED);
	}

	/** Permanent hearts the player has right now (5 to 20). */
	public static int hearts(PlayerEntity player) {
		if (player.getWorld().isClient) {
			return ClientState.hearts;
		}
		return START_HEARTS + player.getAttachedOrElse(BONUS_HEARTS, 0);
	}

	/** Sets the player's permanent hearts (clamped to 5..20), updates max health and tells the client. */
	public static void setHearts(PlayerEntity player, int hearts) {
		player.setAttached(BONUS_HEARTS, Math.max(START_HEARTS, Math.min(MAX_HEARTS, hearts)) - START_HEARTS);
		applyHearts(player);
		sync(player);
	}

	/** Sets the player's max health from their hearts, and trims health that no longer fits. */
	public static void applyHearts(PlayerEntity player) {
		EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		double target = hearts(player) * 2.0;
		if (maxHealth.getBaseValue() != target) {
			maxHealth.setBaseValue(target);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Sends the player their hearts and frozen heart. Safe to call with any player, on either side. */
	public static void sync(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.networkHandler != null
				&& ServerPlayNetworking.canSend(serverPlayer, VesselSyncPayload.ID)) {
			ServerPlayNetworking.send(serverPlayer, new VesselSyncPayload(hearts(serverPlayer), FrozenHeart.get(serverPlayer)));
		}
	}

	/** 6 → "VI". Used for the vessel names. */
	public static String roman(int n) {
		int[] values = {10, 9, 5, 4, 1};
		String[] numerals = {"X", "IX", "V", "IV", "I"};
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			while (n >= values[i]) {
				out.append(numerals[i]);
				n -= values[i];
			}
		}
		return out.toString();
	}

	public static String ordinal(int n) {
		if (n % 100 >= 11 && n % 100 <= 13) {
			return n + "th";
		}
		return n + switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(VesselSyncPayload.ID, VesselSyncPayload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			applyHearts(handler.getPlayer());
			sync(handler.getPlayer());
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			applyHearts(newPlayer);
			if (!alive) {
				newPlayer.setHealth(newPlayer.getMaxHealth());
			}
			sync(newPlayer);
		});

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries ->
				entries.addAfter(Items.ROTTEN_FLESH, FROZEN_FLESH));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
			List<ItemStack> vessels = new ArrayList<>();
			vessels.add(new ItemStack(FROZEN_VESSEL));
			REVITALIZED.forEach(item -> vessels.add(new ItemStack(item)));
			entries.addAfter(Items.ENCHANTED_GOLDEN_APPLE, vessels);
		});
	}
}
