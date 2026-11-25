package com.frostbound.mixin.habanero.client;

import com.frostbound.habanero.LavaPot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

/**
 * A lava pot's menu shows a lava bucket right of the fire (heat) icon, with a tooltip saying what the lava is for.
 * Drawn from Farmer's Delight's renderHeatIndicatorTooltip (hooked by name; its fire icon sits at 47, 55, 17 × 15).
 * The pot comes from the menu's public {@code blockEntity} field, read by reflection so Frostbound needn't compile
 * against Farmer's Delight. Only applied with it installed (FrostboundMixinPlugin).
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.client.gui.CookingPotScreen", remap = false)
public abstract class CookingPotScreenMixin {
	/** Where the lava bucket goes, relative to the menu: just right of the fire icon. */
	@Unique
	private static final int LAVA_X = 65;
	@Unique
	private static final int LAVA_Y = 54;
	@Unique
	private static Field frostbound$blockEntityField;

	@Inject(method = "renderHeatIndicatorTooltip", at = @At("HEAD"), remap = false)
	private void frostbound$lavaIcon(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
		HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
		if (!(frostbound$pot(screen) instanceof LavaPot pot) || !pot.frostbound$hasLava()) {
			return;
		}
		HandledScreenAccessor at = (HandledScreenAccessor) screen;
		int x = at.frostbound$getX() + LAVA_X, y = at.frostbound$getY() + LAVA_Y;
		context.drawItem(new ItemStack(Items.LAVA_BUCKET), x, y);
		if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
			context.drawTooltip(MinecraftClient.getInstance().textRenderer, List.of(
					Text.translatable("frostbound.cooking_pot.lava").formatted(Formatting.GOLD),
					Text.translatable("frostbound.cooking_pot.lava.desc").formatted(Formatting.GRAY),
					Text.translatable("frostbound.cooking_pot.lava.empty").formatted(Formatting.GRAY)), mouseX, mouseY);
		}
	}

	@Unique
	private static Object frostbound$pot(HandledScreen<?> screen) {
		Object menu = screen.getScreenHandler();
		try {
			if (frostbound$blockEntityField == null) {
				frostbound$blockEntityField = menu.getClass().getField("blockEntity");
			}
			return frostbound$blockEntityField.get(menu);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
