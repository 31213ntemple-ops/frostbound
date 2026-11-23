package com.frostbound.alpha;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.passives.Passive;
import com.frostbound.passives.Passives;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Alpha Hide: the Alpha Wolf's pelt (5–7 per Alpha) and the armour made from it. It sits beside iron
 * rather than above it: a little less armour (13 points for a set against iron's 15), but fur-lined
 * and with a set bonus for fighting at low health.
 *
 *  - <b>Insulated</b> (any piece): in {@code minecraft:freeze_immune_wearables} (no freezing in powder
 *    snow, and Frozen mobs' Frostbite can't frost you over) and {@code toughasnails:heating_armor}.
 *  - <b>Pack Instinct</b> (set bonus): when your health drops below 4 hearts, 2 pieces give Regeneration
 *    and all 4 give Strength as well. Then it rests for {@code cooldownSeconds}, however often your health
 *    crosses the line, so it can't be farmed by bobbing around the threshold. The rest time is saved on
 *    the player (it survives relogging and death) and shows as a cooldown sweep on the armour icons.
 */
public final class AlphaHide {
	/** Armour with Pack Instinct (counts towards the set bonus). */
	public static final TagKey<Item> PACK_INSTINCT_ITEMS = TagKey.of(RegistryKeys.ITEM, Frostbound.id("pack_instinct"));
	/** Fur-lined armour, shown with the Insulated passive. */
	public static final TagKey<Item> INSULATED_ITEMS = TagKey.of(RegistryKeys.ITEM, Frostbound.id("insulated"));

	/** World time (ticks) at which Pack Instinct can next trigger. */
	public static final AttachmentType<Long> READY_AT = AttachmentRegistry.<Long>builder()
			.persistent(Codec.LONG)
			.copyOnDeath()
			.initializer(() -> 0L)
			.buildAndRegister(Frostbound.id("pack_instinct_ready_at"));

	public static final Item ALPHA_HIDE = register("alpha_hide", new Item(new Item.Settings()));

	/** frostbound:alpha_hide. Textures: textures/models/armor/alpha_hide_layer_{1,2}.png. */
	public static final RegistryEntry<ArmorMaterial> ARMOR_MATERIAL = Registry.registerReference(Registries.ARMOR_MATERIAL,
			Frostbound.id("alpha_hide"), new ArmorMaterial(
					new EnumMap<>(Map.of(
							ArmorItem.Type.BOOTS, 2,
							ArmorItem.Type.LEGGINGS, 4,
							ArmorItem.Type.CHESTPLATE, 5,
							ArmorItem.Type.HELMET, 2,
							ArmorItem.Type.BODY, 5)),
					15, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, () -> Ingredient.ofItems(ALPHA_HIDE),
					List.of(new ArmorMaterial.Layer(Frostbound.id("alpha_hide"))), 0.0f, 0.0f));
	/** Durability multiplier, the same as iron (helmet 165, chestplate 240, leggings 225, boots 195). */
	private static final int DURABILITY = 15;

	public static final Item HELMET = armor("alpha_hide_helmet", ArmorItem.Type.HELMET);
	public static final Item CHESTPLATE = armor("alpha_hide_chestplate", ArmorItem.Type.CHESTPLATE);
	public static final Item LEGGINGS = armor("alpha_hide_leggings", ArmorItem.Type.LEGGINGS);
	public static final Item BOOTS = armor("alpha_hide_boots", ArmorItem.Type.BOOTS);
	public static final List<Item> ARMOR = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

	public static final Passive INSULATED = Passives.register(new Passive("insulated", Formatting.WHITE, INSULATED_ITEMS,
			() -> new Object[0]));

	public static final Passive PACK_INSTINCT = Passives.register(new Passive("pack_instinct", Formatting.DARK_RED, PACK_INSTINCT_ITEMS, () -> {
		FrostboundConfig.PackInstinctOptions c = FrostboundConfig.get().packInstinct;
		return new Object[] {Passive.number(c.belowHealth / 2.0), roman(c.regenerationLevel), roman(c.strengthLevel),
				Passive.number(c.cooldownSeconds)};
	}));

	private AlphaHide() {}

	private static Item armor(String name, ArmorItem.Type type) {
		return register(name, new ArmorItem(ARMOR_MATERIAL, type, new Item.Settings().maxDamage(type.getMaxDamage(DURABILITY))));
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, Frostbound.id(name), item);
	}

	static String roman(int level) {
		return switch (level) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> Integer.toString(level);
		};
	}

	/** Pieces of Pack Instinct armour {@code player} is wearing. */
	public static int pieces(PlayerEntity player) {
		int count = 0;
		for (ItemStack stack : player.getArmorItems()) {
			if (stack.isIn(PACK_INSTINCT_ITEMS)) {
				count++;
			}
		}
		return count;
	}

	/** Ticks until Pack Instinct can trigger again (0 = ready). */
	public static long cooldownLeft(PlayerEntity player) {
		long left = player.getAttachedOrElse(READY_AT, 0L) - player.getWorld().getTime();
		long max = AlphaWolfEntity.ticks(FrostboundConfig.get().packInstinct.cooldownSeconds);
		// A saved time from another world clock (or a lowered config) can't leave it stuck.
		return left > max ? 0 : Math.max(0, left);
	}

	/** Runs every tick for every player: triggers Pack Instinct when it should. Returns whether it triggered. */
	public static boolean tick(PlayerEntity player) {
		if (player.getWorld().isClient || !player.isAlive()) {
			return false;
		}
		FrostboundConfig.PackInstinctOptions config = FrostboundConfig.get().packInstinct;
		int pieces = pieces(player);
		if (pieces < 2 || player.getHealth() >= config.belowHealth || cooldownLeft(player) > 0) {
			return false;
		}
		int regeneration = AlphaWolfEntity.ticks(config.regenerationSeconds);
		if (regeneration > 0 && config.regenerationLevel > 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, regeneration, config.regenerationLevel - 1));
		}
		int strength = AlphaWolfEntity.ticks(config.strengthSeconds);
		if (pieces >= 4 && strength > 0 && config.strengthLevel > 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, strength, config.strengthLevel - 1));
		}
		int cooldown = AlphaWolfEntity.ticks(config.cooldownSeconds);
		player.setAttached(READY_AT, player.getWorld().getTime() + cooldown);
		for (Item item : ARMOR) {
			player.getItemCooldownManager().set(item, cooldown);
		}
		if (player.getWorld() instanceof ServerWorld world) {
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 0.8f, 1.2f);
			world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getBodyY(0.9), player.getZ(), 4, 0.4, 0.3, 0.4, 0.0);
		}
		player.sendMessage(Text.translatable(pieces >= 4 ? "frostbound.pack_instinct.full" : "frostbound.pack_instinct.half")
				.formatted(Formatting.DARK_RED), true);
		return true;
	}
}
