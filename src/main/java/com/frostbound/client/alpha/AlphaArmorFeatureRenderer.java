package com.frostbound.client.alpha;

import com.frostbound.Frostbound;
import com.frostbound.alpha.AlphaWolfEntity;
import com.frostbound.alpha.PackWolfEntity;
import com.frostbound.client.blockbench.BlockbenchModels;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * The Alpha's spiked armour: blackened iron plates rimed with frost (a brow plate and muzzle guard, a
 * riveted collar, pauldrons, a strapped saddle down its back, bracers on its forelegs) and ice-tipped
 * spikes on the cheeks, shoulders, spine and forelegs. The ruff on its shoulders stays bare.
 *
 * Made in Blockbench: src/main/blockbench/entity/alpha_wolf_armor.bbmodel. Its bones are named after the
 * wolf model's parts and pivot where they do (the wolf itself is in the project as reference cubes with
 * Export off), so each frame every bone simply takes its part's pose ({@link PackWolfModel#copyPoseTo})
 * and the armour follows every pose the Alpha strikes, asleep or mid-Pounce.
 */
public class AlphaArmorFeatureRenderer extends FeatureRenderer<PackWolfEntity, PackWolfModel> {
	public static final Identifier MODEL = Frostbound.id("alpha_wolf_armor");
	/** Registered at client start (model layers must be known before models load). */
	public static EntityModelLayer layer;

	private final ModelPart armor;

	public AlphaArmorFeatureRenderer(FeatureRendererContext<PackWolfEntity, PackWolfModel> context, ModelPart armor) {
		super(context);
		this.armor = armor;
	}

	public static void registerLayer() {
		layer = BlockbenchModels.registerLayer(MODEL);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PackWolfEntity wolf, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (!(wolf instanceof AlphaWolfEntity) || wolf.isInvisible()) {
			return;
		}
		this.getContextModel().copyPoseTo(this.armor);
		this.armor.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(BlockbenchModels.texture(MODEL))), light,
				LivingEntityRenderer.getOverlay(wolf, 0.0f));
	}
}
