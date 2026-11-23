package com.frostbound.client.alpha;

import com.frostbound.Frostbound;
import com.frostbound.alpha.AlphaWolfEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.WolfEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.model.WolfEntityModel;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.util.Identifier;

/**
 * Pack wolves and the Alpha: the vanilla wolf, always in its coat's angry texture (red eyes, bared
 * teeth). The Alpha (a black wolf, made bigger by its scale attribute) also gets eyes that burn
 * full-bright in the dark (textures/entity/alpha_wolf_eyes.png).
 */
public class PackWolfRenderer extends WolfEntityRenderer {
	private static final RenderLayer ALPHA_EYES = RenderLayer.getEyes(Frostbound.id("textures/entity/alpha_wolf_eyes.png"));

	public PackWolfRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.addFeature(new EyesFeatureRenderer<WolfEntity, WolfEntityModel<WolfEntity>>(this) {
			@Override
			public void render(net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider vertices,
					int light, WolfEntity wolf, float limbAngle, float limbDistance, float tickDelta, float animationProgress,
					float headYaw, float headPitch) {
				if (wolf instanceof AlphaWolfEntity) {
					super.render(matrices, vertices, light, wolf, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
				}
			}

			@Override
			public RenderLayer getEyesTexture() {
				return ALPHA_EYES;
			}
		});
	}

	@Override
	public Identifier getTexture(WolfEntity wolf) {
		return wolf.getVariant().value().getAngryTextureId();
	}
}
