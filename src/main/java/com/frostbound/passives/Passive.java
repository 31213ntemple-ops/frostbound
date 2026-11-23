package com.frostbound.passives;

import com.frostbound.Frostbound;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A named bonus an item has, such as copper's Conductivity. Every item in {@link #items} gets it, and
 * its tooltip shows it right under the item's name, before the vanilla lines:
 *
 * <pre>
 * Copper Sword
 *
 * CONDUCTIVITY                          (name, bold in {@link #color})
 * 5% chance to deal 150% damage to ...  (description, light grey, wrapped)
 *
 * When in Main Hand: ...                (vanilla)
 * </pre>
 *
 * Lang keys: {@code passive.frostbound.<id>} (the name, written in capitals) and
 * {@code passive.frostbound.<id>.desc} (the description; {@link #descriptionArgs} fill its %s, so
 * numbers come from the config). The effect itself lives with the feature; this only describes it.
 *
 * Most passives use the tag constructor; the canonical one is for passives that depend on more than the
 * item, such as the frost bonuses (only on Icy or Frozen armour, with stronger numbers when Frozen).
 *
 * @param id              short name, used for the lang keys
 * @param color           colour of the name (pick one that suits the material: copper is gold/orange)
 * @param filter          which stacks have it (usually an item tag, so a data pack can add more)
 * @param descriptionArgs values for the description's %s for a given stack, read each time the tooltip is drawn
 */
public record Passive(String id, Formatting color, Predicate<ItemStack> filter, Function<ItemStack, Object[]> descriptionArgs) {
	/** A passive every item in the tag {@code items} has, with the same numbers on every stack. */
	public Passive(String id, Formatting color, TagKey<Item> items, Supplier<Object[]> descriptionArgs) {
		this(id, color, stack -> stack.isIn(items), stack -> descriptionArgs.get());
	}

	public boolean appliesTo(ItemStack stack) {
		return filter.test(stack);
	}

	/** "CONDUCTIVITY": the name, bold in its colour. */
	public Text title() {
		return Text.translatable("passive." + Frostbound.MOD_ID + "." + id).formatted(color, Formatting.BOLD);
	}

	/** The description, unwrapped and unstyled (the tooltip wraps it and colours it light grey). */
	public Text description(ItemStack stack) {
		return Text.translatable("passive." + Frostbound.MOD_ID + "." + id + ".desc", descriptionArgs.apply(stack));
	}

	/** {@link #description(ItemStack)} for passives whose numbers don't depend on the stack. */
	public Text description() {
		return description(ItemStack.EMPTY);
	}

	/** 1.5 → "1.5", 150.0 → "150": for numbers in descriptions. */
	public static String number(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(Math.round(value * 100) / 100.0);
	}
}
