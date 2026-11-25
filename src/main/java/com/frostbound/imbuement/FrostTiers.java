package com.frostbound.imbuement;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.AnimalArmorItem;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.dynamic.Codecs;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Cold, Icy and Frozen armour and weapons. The tier is stored on the stack ({@link #TIER}, {@code frostbound:frost_tier});
 * its stat boosts are worked out from the config whenever the game asks for them (mixin.imbuement.ItemStackMixin),
 * not written into the stack, so they follow config changes and survive smithing a diamond piece into netherite.
 *
 * <ul>
 *   <li>Cold (ringed by 8 ice): +0.5 armour or attack damage, +25% durability.</li>
 *   <li>Icy (Cold ringed by 8 × 4 packed ice): +15% more durability and its bonus ({@link FrostBonuses}).</li>
 *   <li>Frozen (Icy ringed by 8 blue ice): +0.5 armour and toughness or attack damage, and the stronger bonus.</li>
 * </ul>
 *
 * In the tooltip the bonuses aren't merged
 * into the vanilla lines but shown after them, "+8 Armor (+1)", in the tier's colour ({@link #forTooltip}).
 */
public final class FrostTiers {
	public static final ComponentType<Integer> TIER = Registry.register(Registries.DATA_COMPONENT_TYPE, Frostbound.id("frost_tier"),
			ComponentType.<Integer>builder().codec(Codecs.rangedInt(0, 3)).packetCodec(PacketCodecs.VAR_INT).build());

	/**
	 * One upgrade at the imbuement table: armour of tier {@code from} in the centre, {@code count} of
	 * {@code ring} in each of the eight slots around it, gives the armour back at tier {@code to}.
	 */
	public record Step(FrostTier from, FrostTier to, Item ring, int count) {}

	/** Weapons the table takes besides armour (swords, axes, the mace and the trident). */
	public static final TagKey<Item> IMBUABLE_WEAPONS = TagKey.of(RegistryKeys.ITEM, Frostbound.id("imbuable_weapons"));

	public static final String STAR = "\u2605";

	public static final List<Step> STEPS = List.of(
			new Step(FrostTier.NONE, FrostTier.COLD, Items.ICE, 1),
			new Step(FrostTier.COLD, FrostTier.ICY, Items.PACKED_ICE, 4),
			new Step(FrostTier.ICY, FrostTier.FROZEN, Items.BLUE_ICE, 1));

	private FrostTiers() {}

	public static FrostTier tier(ItemStack stack) {
		return FrostTier.of(stack.getOrDefault(TIER, 0));
	}

	public static void setTier(ItemStack stack, FrostTier tier) {
		if (tier == FrostTier.NONE) {
			stack.remove(TIER);
		} else {
			stack.set(TIER, tier.level());
		}
	}

	/** What can go in the table's centre slot: armour you wear (not horse or wolf armour) and weapons. */
	public static boolean canImbue(ItemStack stack) {
		return isArmor(stack) || isWeapon(stack);
	}

	public static boolean isArmor(ItemStack stack) {
		return stack.getItem() instanceof ArmorItem && !(stack.getItem() instanceof AnimalArmorItem);
	}

	public static boolean isWeapon(ItemStack stack) {
		return stack.isIn(IMBUABLE_WEAPONS);
	}


	/**
	 * The step that {@code armor} in the centre and {@code ring} around it (the eight outer slots, in any
	 * order) make, or null if they don't make one. Every outer slot needs at least the step's count of its
	 * item and nothing else.
	 */
	@Nullable
	public static Step match(ItemStack armor, List<ItemStack> ring) {
		if (armor.isEmpty() || armor.getCount() != 1 || !canImbue(armor) || ring.size() != 8) {
			return null;
		}
		FrostTier tier = tier(armor);
		for (Step step : STEPS) {
			if (step.from == tier && ring.stream().allMatch(s -> s.isOf(step.ring) && s.getCount() >= step.count)) {
				return step;
			}
		}
		return null;
	}

	/** The upgraded armour {@code armor} and {@code ring} make, or {@link ItemStack#EMPTY}. Doesn't change its inputs. */
	public static ItemStack upgrade(ItemStack armor, List<ItemStack> ring) {
		Step step = match(armor, ring);
		if (step == null) {
			return ItemStack.EMPTY;
		}
		ItemStack result = armor.copyWithCount(1);
		setTier(result, step.to);
		return result;
	}

	/** Armour points a piece of this tier gets on top of its normal armour. */
	public static double armorBonus(FrostTier tier) {
		FrostboundConfig.FrostTierOptions c = FrostboundConfig.get().frostTiers;
		return (tier.atLeast(FrostTier.COLD) ? c.coldArmor : 0.0) + (tier.atLeast(FrostTier.FROZEN) ? c.frozenArmor : 0.0);
	}

	public static double toughnessBonus(FrostTier tier) {
		return tier.atLeast(FrostTier.FROZEN) ? FrostboundConfig.get().frostTiers.frozenToughness : 0.0;
	}

	/** Attack damage a weapon of this tier gets on top of its normal damage. */
	public static double attackDamageBonus(FrostTier tier) {
		FrostboundConfig.FrostTierOptions c = FrostboundConfig.get().frostTiers;
		return (tier.atLeast(FrostTier.COLD) ? c.coldAttackDamage : 0.0) + (tier.atLeast(FrostTier.FROZEN) ? c.frozenAttackDamage : 0.0);
	}

	/** Durability multiplier of this tier (1.25 for Cold, 1.4 for Icy and Frozen). */
	public static double durabilityMultiplier(FrostTier tier) {
		FrostboundConfig.FrostTierOptions c = FrostboundConfig.get().frostTiers;
		return 1.0 + (tier.atLeast(FrostTier.COLD) ? c.coldDurability : 0.0) + (tier.atLeast(FrostTier.ICY) ? c.icyDurability : 0.0);
	}

	/** {@code baseMaxDamage} (the stack's normal durability) with its tier's boost. */
	public static int maxDamage(ItemStack stack, int baseMaxDamage) {
		FrostTier tier = tier(stack);
		if (tier == FrostTier.NONE || baseMaxDamage <= 0) {
			return baseMaxDamage;
		}
		return Math.max(1, (int) Math.round(baseMaxDamage * durabilityMultiplier(tier)));
	}

	/**
	 * Wraps a consumer of the stack's attribute modifiers so the piece's own armour, toughness and attack
	 * damage modifiers (the first flat ones) arrive with the tier's bonus added. Used when the gear is
	 * worn or held, so the bonus counts.
	 */
	public static BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> withBonus(ItemStack stack,
			BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> consumer) {
		BonusFinder finder = BonusFinder.of(stack);
		if (finder == null) {
			return consumer;
		}
		return (attribute, modifier) -> {
			double bonus = finder.next(attribute, modifier);
			consumer.accept(attribute, bonus == 0.0 ? modifier
					: new EntityAttributeModifier(modifier.id(), modifier.value() + bonus, modifier.operation()));
		};
	}

	/**
	 * The tooltip's version of {@link #withBonus}: the modifier stays as it is, so vanilla writes its usual
	 * line ("+8 Armor"), and while it does the bonus waits in {@link #TOOLTIP_BONUS} for
	 * {@link #withTooltipBonus} to add " (+1)" after it.
	 */
	public static BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> forTooltip(ItemStack stack,
			BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> consumer) {
		BonusFinder finder = BonusFinder.of(stack);
		if (finder == null) {
			return consumer;
		}
		return (attribute, modifier) -> {
			double bonus = finder.next(attribute, modifier);
			if (bonus == 0.0) {
				consumer.accept(attribute, modifier);
				return;
			}
			TOOLTIP_BONUS.set(new TooltipBonus(bonus, finder.tier.starColor));
			try {
				consumer.accept(attribute, modifier);
			} finally {
				TOOLTIP_BONUS.remove();
			}
		};
	}

	/** The bonus of the modifier line being written right now, if it has one (see {@link #forTooltip}). */
	private static final ThreadLocal<TooltipBonus> TOOLTIP_BONUS = new ThreadLocal<>();

	private record TooltipBonus(double amount, int color) {}

	/** A vanilla attribute line, with " (+1)" in the tier's colour added if it's the line of a frost bonus. */
	public static Object withTooltipBonus(Object line) {
		TooltipBonus bonus = TOOLTIP_BONUS.get();
		if (bonus == null || !(line instanceof Text text)) {
			return line;
		}
		return text.copy().append(bonusText(bonus.amount, bonus.color));
	}

	/** " (+1)", " (+0.5)": a frost bonus after a vanilla number, in the tier's colour. */
	public static Text bonusText(double amount, int color) {
		return Text.literal(" (+" + AttributeModifiersComponent.DECIMAL_FORMAT.format(amount) + ")")
				.setStyle(Style.EMPTY.withItalic(false).withColor(color));
	}

	/** Durability this stack has on top of its normal durability, from its tier (0 if none). */
	public static int durabilityBonus(ItemStack stack) {
		int base = stack.getOrDefault(DataComponentTypes.MAX_DAMAGE, 0);
		return base > 0 ? maxDamage(stack, base) - base : 0;
	}

	/** Hands out each bonus once, to the first flat modifier of its attribute (armour, toughness, attack damage). */
	private static final class BonusFinder {
		final FrostTier tier;
		private double armor;
		private double toughness;
		private double attack;

		private BonusFinder(FrostTier tier, double armor, double toughness, double attack) {
			this.tier = tier;
			this.armor = armor;
			this.toughness = toughness;
			this.attack = attack;
		}

		@Nullable
		static BonusFinder of(ItemStack stack) {
			FrostTier tier = tier(stack);
			if (tier == FrostTier.NONE) {
				return null;
			}
			boolean armor = isArmor(stack);
			boolean weapon = isWeapon(stack);
			if (!armor && !weapon) {
				return null;
			}
			return new BonusFinder(tier, armor ? armorBonus(tier) : 0.0, armor ? toughnessBonus(tier) : 0.0,
					weapon ? attackDamageBonus(tier) : 0.0);
		}

		double next(RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier) {
			if (modifier.operation() != EntityAttributeModifier.Operation.ADD_VALUE) {
				return 0.0;
			}
			double bonus = 0.0;
			if (attribute.equals(EntityAttributes.GENERIC_ARMOR)) {
				bonus = armor;
				armor = 0.0;
			} else if (attribute.equals(EntityAttributes.GENERIC_ARMOR_TOUGHNESS)) {
				bonus = toughness;
				toughness = 0.0;
			} else if (attribute.equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
				bonus = attack;
				attack = 0.0;
			}
			return bonus;
		}
	}

	/** " ★★" for Icy: a space, then one star per tier up to this one, each in that tier's colour (the tooltip appends it to the name). */
	public static Text stars(FrostTier tier) {
		MutableText stars = Text.literal(tier == FrostTier.NONE ? "" : " ").setStyle(Style.EMPTY.withItalic(false));
		for (int level = 1; level <= tier.level(); level++) {
			stars.append(Text.literal(STAR).setStyle(Style.EMPTY.withItalic(false).withColor(FrostTier.of(level).starColor)));
		}
		return stars;
	}

	/** "Frozen ": the tier's name and a space, in its star colour (the tooltip puts it before the item's name). Empty for no tier. */
	public static Text prefix(FrostTier tier) {
		if (tier == FrostTier.NONE) {
			return Text.empty();
		}
		return Text.translatable("frostbound.frost_tier." + tier.name().toLowerCase(java.util.Locale.ROOT))
				.append(" ").setStyle(Style.EMPTY.withItalic(false).withColor(tier.starColor));
	}

	/** The tooltip title of frosty gear: "Frozen Iron Chestplate ★★★", the prefix and stars in their tier colours. */
	public static Text title(FrostTier tier, Text name) {
		if (tier == FrostTier.NONE) {
			return name;
		}
		return Text.empty().append(prefix(tier)).append(name).append(stars(tier));
	}

	static void init() {
		// Loading the class registers TIER.
	}
}
