package com.frostbound.client.imbuement;

import com.frostbound.imbuement.FrostTier;
import com.frostbound.imbuement.FrostTiers;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.List;

/**
 * Cold, Icy and Frozen gear's tooltip: the tier's name before the item's and one, two or three stars
 * after it, each in its tier's soft blue: "Frozen Iron Chestplate ★★★", stars pale to deep.
 *
 * It also shows the extra durability: "Durability: 504 / 504 (+144)". Vanilla only shows durability with
 * advanced tooltips on a damaged item; frosty gear always shows it so the bonus can be seen. (Armour,
 * toughness and attack damage get their "(+1)" in ItemStackMixin.)
 */
public final class FrostStars {
	private static final String DURABILITY_KEY = "item.durability";

	private FrostStars() {}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			FrostTier tier = FrostTiers.tier(stack);
			if (tier == FrostTier.NONE || lines.isEmpty()) {
				return;
			}
			lines.set(0, FrostTiers.title(tier, lines.get(0)));
			addDurabilityBonus(stack, tier, lines, type.isAdvanced());
		});
	}

	/** Adds " (+N)" to the durability line, adding the line first if vanilla didn't. */
	public static void addDurabilityBonus(ItemStack stack, FrostTier tier, List<Text> lines, boolean advanced) {
		int bonus = FrostTiers.durabilityBonus(stack);
		if (bonus <= 0) {
			return;
		}
		Text bonusText = FrostTiers.bonusText(bonus, tier.starColor);
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).getContent() instanceof TranslatableTextContent content && content.getKey().equals(DURABILITY_KEY)) {
				lines.set(i, lines.get(i).copy().append(bonusText));
				return;
			}
		}
		Text line = Text.translatable(DURABILITY_KEY, stack.getMaxDamage() - stack.getDamage(), stack.getMaxDamage()).append(bonusText);
		// Where vanilla puts it: after the attributes, before the advanced tooltip's item id.
		int at = lines.size();
		if (advanced) {
			String id = Registries.ITEM.getId(stack.getItem()).toString();
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).getString().equals(id)) {
					at = i;
					break;
				}
			}
		}
		lines.add(at, line);
	}
}
