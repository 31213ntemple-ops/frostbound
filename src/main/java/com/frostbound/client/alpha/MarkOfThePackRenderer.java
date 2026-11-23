package com.frostbound.client.alpha;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.client.TrinketRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/** The Mark of the Pack hangs on the wearer's chest, over any armour, swinging with the body. */
public class MarkOfThePackRenderer implements TrinketRenderer {
	@Override
	@SuppressWarnings("unchecked")
	public void render(ItemStack stack, SlotReference slotReference, EntityModel<? extends LivingEntity> contextModel, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, float limbAngle, float limbDistance, float tickDelta,
			float animationProgress, float headYaw, float headPitch) {
		if (!(entity instanceof AbstractClientPlayerEntity player) || !(contextModel instanceof PlayerEntityModel<?> model)) {
			return;
		}
		matrices.push();
		TrinketRenderer.translateToChest(matrices, (PlayerEntityModel<AbstractClientPlayerEntity>) model, player);
		// Up to the collar, just in front of a chestplate, cord at the top.
		matrices.translate(0.0f, -0.17f, -0.03f);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
		matrices.scale(0.42f, 0.42f, 0.42f);
		MinecraftClient.getInstance().getItemRenderer().renderItem(stack, ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV,
				matrices, vertexConsumers, entity.getWorld(), 0);
		matrices.pop();
	}
}
