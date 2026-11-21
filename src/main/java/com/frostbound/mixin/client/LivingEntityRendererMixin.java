package com.frostbound.mixin.client;

import com.frostbound.FrozenMob;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Visuals, client only:
 *  - isShaking -> the same shiver vanilla uses for mobs freezing in powder snow
 *  - tint      -> multiplies the model's colour by a pale ice-blue, no new textures needed,
 *                 so it works on every mob (including other mods' mobs) automatically.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	/** Pale ice blue (ARGB). Raise the R/G values toward FF for a subtler tint. */
	@Unique
	private static final int ICE_TINT = 0xFFB4DCFF;

	@Inject(method = "isShaking", at = @At("HEAD"), cancellable = true)
	private void frostbound$shiver(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (FrozenMob.isFrozen(entity)) {
			cir.setReturnValue(true);
		}
	}

	// require = 0: if another mod in the pack rewrites entity rendering, we lose the
	// tint instead of crashing the game. Shiver + snowflakes still work.
	@ModifyArg(
			method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"),
			index = 4,
			require = 0)
	private int frostbound$icyTint(int color, @Local(argsOnly = true) LivingEntity entity) {
		if (!FrozenMob.isFrozen(entity)) {
			return color;
		}
		return multiply(color, ICE_TINT);
	}

	/** Channel-by-channel multiply of two ARGB colours (keeps invisibility transparency intact). */
	@Unique
	private static int multiply(int a, int b) {
		int alpha = ((a >>> 24) * (b >>> 24)) / 255;
		int red = (((a >> 16) & 0xFF) * ((b >> 16) & 0xFF)) / 255;
		int green = (((a >> 8) & 0xFF) * ((b >> 8) & 0xFF)) / 255;
		int blue = ((a & 0xFF) * (b & 0xFF)) / 255;
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}
}
