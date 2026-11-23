package com.frostbound.mixin.imbuement.client;

import com.frostbound.client.imbuement.FrostRenderLayers;
import com.frostbound.imbuement.FrostTier;
import com.frostbound.imbuement.FrostTiers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Draws the frost of Cold, Icy and Frozen gear over the item, wherever it's shown (see {@link FrostRenderLayers}). */
@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
	@WrapOperation(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/item/ItemStack;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V"))
	private void frostbound$frost(ItemRenderer renderer, BakedModel model, ItemStack stack, int light, int overlay, MatrixStack matrices,
			VertexConsumer vertices, Operation<Void> original, @Local(argsOnly = true) VertexConsumerProvider provider) {
		FrostTier tier = FrostTiers.tier(stack);
		RenderLayer frost = tier == FrostTier.NONE ? null : FrostRenderLayers.item(tier);
		if (frost != null) {
			vertices = VertexConsumers.union(provider.getBuffer(frost), vertices);
		}
		original.call(renderer, model, stack, light, overlay, matrices, vertices);
	}
}
