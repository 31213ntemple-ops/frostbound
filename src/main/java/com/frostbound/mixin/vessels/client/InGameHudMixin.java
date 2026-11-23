package com.frostbound.mixin.vessels.client;

import com.frostbound.client.alpha.DrainedHeartsHud;
import com.frostbound.client.vessels.FrozenHeartHud;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the frozen heart in its own slot just after the last heart (after golden hearts too).
 *
 * Vanilla works out how many rows of hearts there are (and so where the armour bar goes) from
 * health plus absorption, so the frozen heart is counted there as one more absorption heart. The
 * real absorption is then handed to renderHealthBar, so golden hearts draw exactly as usual, and
 * the frozen heart is drawn afterwards in the slot that was kept for it.
 *
 * Draining Bite's drained hearts (DrainedHeartsHud) get slots the same way, after the frozen heart.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Shadow
	@Final
	private Random random;

	@Shadow
	private int ticks;

	@Unique
	private int frostbound$realAbsorption;

	@ModifyExpressionValue(method = "renderStatusBars", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/player/PlayerEntity;getAbsorptionAmount()F"))
	private float frostbound$keepFrozenHeartSlot(float absorption, @Local PlayerEntity player) {
		this.frostbound$realAbsorption = MathHelper.ceil(absorption);
		int extra = (FrozenHeartHud.isShown(this.ticks) ? 1 : 0) + DrainedHeartsHud.count(player);
		if (extra == 0) {
			return absorption;
		}
		// Golden hearts rounded up to whole hearts, then one more slot each for the frozen and drained hearts.
		return MathHelper.ceil(absorption / 2.0f) * 2.0f + extra * 2.0f;
	}

	@ModifyArg(method = "renderStatusBars", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHealthBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIIIFIIIZ)V"),
			index = 9)
	private int frostbound$realAbsorption(int absorption) {
		return this.frostbound$realAbsorption;
	}

	@Inject(method = "renderHealthBar", at = @At("TAIL"))
	private void frostbound$drawFrozenHeart(DrawContext context, PlayerEntity player, int x, int y, int lines,
			int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking,
			CallbackInfo ci) {
		int slot = MathHelper.ceil(maxHealth / 2.0f) + MathHelper.ceil(absorption / 2.0f);
		boolean shake = lastHealth + absorption <= 4;
		if (FrozenHeartHud.isShown(this.ticks)) {
			int heartX = x + (slot % 10) * 8;
			int heartY = y - (slot / 10) * lines;
			// Shake with the rest of the row when health is low.
			if (shake) {
				heartY += this.random.nextInt(2);
			}
			FrozenHeartHud.draw(context, this.ticks, heartX, heartY, player.getWorld().getLevelProperties().isHardcore());
			slot++;
		}
		for (int i = DrainedHeartsHud.count(player); i > 0; i--, slot++) {
			DrainedHeartsHud.draw(context, this.ticks, x + (slot % 10) * 8, y - (slot / 10) * lines + (shake ? this.random.nextInt(2) : 0));
		}
	}
}
