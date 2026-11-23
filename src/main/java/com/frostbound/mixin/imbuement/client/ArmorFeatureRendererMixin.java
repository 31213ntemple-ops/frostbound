package com.frostbound.mixin.imbuement.client;

import com.frostbound.client.imbuement.FrostRenderLayers;
import com.frostbound.imbuement.FrostTier;
import com.frostbound.imbuement.FrostTiers;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the frost of Cold, Icy and Frozen armour over it while it's worn (see {@link FrostRenderLayers}). */
@Mixin(ArmorFeatureRenderer.class)
abstract class ArmorFeatureRendererMixin {
	@Inject(method = "renderArmor", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasGlint()Z"))
	private void frostbound$frost(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices,
			@Local(argsOnly = true) VertexConsumerProvider vertexConsumers, @Local(argsOnly = true) int light,
			@Local(argsOnly = true) BipedEntityModel<?> model, @Local ItemStack stack) {
		FrostTier tier = FrostTiers.tier(stack);
		RenderLayer frost = tier == FrostTier.NONE ? null : FrostRenderLayers.armor(tier);
		if (frost != null) {
			model.render(matrices, vertexConsumers.getBuffer(frost), light, OverlayTexture.DEFAULT_UV);
		}
	}
}
