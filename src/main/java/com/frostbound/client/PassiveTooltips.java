package com.frostbound.client;

import com.frostbound.passives.Passive;
import com.frostbound.passives.Passives;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Style;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws each item's {@link Passive}s at the top of its tooltip, under the name and above the
 * vanilla lines (enchantments, attack damage, ...), with a blank line above and below each.
 */
public final class PassiveTooltips {
	/** Description width in pixels, about the width of "5% chance to deal 150% damage to any mobs". */
	private static final int WRAP_WIDTH = 220;

	private PassiveTooltips() {}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			List<Passive> passives = Passives.of(stack);
			if (passives.isEmpty() || lines.isEmpty()) {
				return;
			}
			List<Text> added = new ArrayList<>();
			for (Passive passive : passives) {
				added.add(Text.empty()); // a blank line above each passive, to set it apart
				added.add(passive.title());
				added.addAll(wrap(passive.description(stack)));
			}
			int insertAt = 1;
			// Keep one blank line between the passives and the vanilla lines (whose attribute block often starts with one).
			if (insertAt >= lines.size() || !lines.get(insertAt).getString().isEmpty()) {
				added.add(Text.empty());
			}
			lines.addAll(insertAt, added);
		});
	}

	private static List<Text> wrap(Text text) {
		List<Text> wrapped = new ArrayList<>();
		for (StringVisitable line : MinecraftClient.getInstance().textRenderer.getTextHandler().wrapLines(text, WRAP_WIDTH, Style.EMPTY)) {
			wrapped.add(Text.literal(line.getString()).formatted(Formatting.GRAY));
		}
		return wrapped;
	}
}
