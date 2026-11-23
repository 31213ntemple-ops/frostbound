package com.frostbound.client.alpha;

import com.frostbound.Frostbound;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * The Alpha Hide Hood: the Alpha's own head worn as a hood. The usual helmet (layer 1 of the
 * alpha_hide armour texture) plus two upright ears and the upper jaw jutting out over the brow, teeth
 * and all, from textures/models/armor/alpha_hood.png.
 */
public class AlphaHoodRenderer implements ArmorRenderer {
	private static final Identifier ARMOR = Frostbound.id("textures/models/armor/alpha_hide_layer_1.png");
	private static final Identifier EXTRAS = Frostbound.id("textures/models/armor/alpha_hood.png");

	private BipedEntityModel<LivingEntity> armorModel;
	private ModelPart extras;

	/** Ears and snout, in the head's space (the helmet box runs from y −9 to 1 and z −5 to 5). */
	public static TexturedModelData extrasModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData hood = root.addChild("hood", ModelPartBuilder.create(), ModelTransform.NONE);
		hood.addChild("right_ear", ModelPartBuilder.create().uv(0, 0).cuboid(-1.0f, -3.0f, -0.5f, 2.0f, 3.0f, 1.0f),
				ModelTransform.of(-2.7f, -8.6f, -1.6f, -0.12f, 0.0f, -0.22f));
		hood.addChild("left_ear", ModelPartBuilder.create().uv(6, 0).cuboid(-1.0f, -3.0f, -0.5f, 2.0f, 3.0f, 1.0f),
				ModelTransform.of(2.7f, -8.6f, -1.6f, -0.12f, 0.0f, 0.22f));
		hood.addChild("snout", ModelPartBuilder.create().uv(0, 4).cuboid(-2.0f, -1.0f, -4.0f, 4.0f, 2.0f, 4.0f, new Dilation(0.05f)),
				ModelTransform.of(0.0f, -6.4f, -5.0f, 0.1f, 0.0f, 0.0f));
		return TexturedModelData.of(data, 32, 16);
	}

	private void setUp() {
		if (this.armorModel == null) {
			this.armorModel = new BipedEntityModel<>(MinecraftClient.getInstance().getEntityModelLoader()
					.getModelPart(EntityModelLayers.PLAYER_OUTER_ARMOR));
			this.extras = extrasModelData().createModel().getChild("hood");
		}
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ItemStack stack, LivingEntity entity, EquipmentSlot slot,
			int light, BipedEntityModel<LivingEntity> contextModel) {
		this.setUp();
		contextModel.copyBipedStateTo(this.armorModel);
		this.armorModel.setVisible(false);
		this.armorModel.head.visible = true;
		this.armorModel.hat.visible = true;
		ArmorRenderer.renderPart(matrices, vertexConsumers, light, stack, this.armorModel, ARMOR);

		this.extras.copyTransform(this.armorModel.head);
		VertexConsumer consumer = ItemRenderer.getArmorGlintConsumer(vertexConsumers, RenderLayer.getArmorCutoutNoCull(EXTRAS), stack.hasGlint());
		this.extras.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
	}
}
