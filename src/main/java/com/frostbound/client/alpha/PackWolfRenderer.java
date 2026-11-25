package com.frostbound.client.alpha;

import com.frostbound.Frostbound;
import com.frostbound.alpha.AlphaWolfEntity;
import com.frostbound.alpha.PackWolfEntity;
import com.frostbound.alpha.WolfAction;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Pack wolves and the Alpha, posed by {@link PackWolfModel}.
 *
 *  - Awake, a pack wolf wears its coat's angry texture (red eyes, bared teeth); asleep, its calm coat
 *    with the eyes shut (textures/entity/wolf_sleeping/).
 *  - The Alpha has a coat of its own (textures/entity/alpha_wolf.png: the black wolf with a
 *    frost-tipped ruff and a scarred eye) and eyes that burn full-bright in the dark, which snap open
 *    as it wakes. Bloodied, embers drift from them (see AlphaWolfEntity's client tick).
 *  - The Alpha wears spiked armour over its coat ({@link AlphaArmorFeatureRenderer}, made in Blockbench).
 */
public class PackWolfRenderer extends MobEntityRenderer<PackWolfEntity, PackWolfModel> {
	private static final RenderLayer ALPHA_EYES = RenderLayer.getEyes(Frostbound.id("textures/entity/alpha_wolf_eyes.png"));
	private static final Identifier ALPHA = Frostbound.id("textures/entity/alpha_wolf.png");
	private static final Identifier ALPHA_SLEEPING = Frostbound.id("textures/entity/alpha_wolf_sleeping.png");

	public PackWolfRenderer(EntityRendererFactory.Context context) {
		super(context, new PackWolfModel(context.getPart(EntityModelLayers.WOLF)), 0.5f);
		this.addFeature(new AlphaArmorFeatureRenderer(this, context.getPart(AlphaArmorFeatureRenderer.layer)));
		this.addFeature(new EyesFeatureRenderer<>(this) {
			@Override
			public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, PackWolfEntity wolf, float limbAngle,
					float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
				if (wolf instanceof AlphaWolfEntity && eyesOpen(wolf, tickDelta)) {
					super.render(matrices, vertices, light, wolf, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
				}
			}

			@Override
			public RenderLayer getEyesTexture() {
				return ALPHA_EYES;
			}
		});
	}

	/** Shut while asleep; they open a few ticks into waking up. */
	static boolean eyesOpen(PackWolfEntity wolf, float tickDelta) {
		WolfAction action = wolf.getAction();
		return action != WolfAction.SLEEPING && !(action == WolfAction.WAKING && wolf.actionProgress(tickDelta) < 6.0f);
	}

	@Override
	protected float getAnimationProgress(PackWolfEntity wolf, float tickDelta) {
		return wolf.getTailAngle();
	}

	@Override
	public Identifier getTexture(PackWolfEntity wolf) {
		boolean open = eyesOpen(wolf, 0.0f);
		if (wolf instanceof AlphaWolfEntity) {
			return open ? ALPHA : ALPHA_SLEEPING;
		}
		if (!open) {
			Identifier wild = wolf.getVariant().value().getWildTextureId();
			if (Identifier.DEFAULT_NAMESPACE.equals(wild.getNamespace())) {
				String file = wild.getPath().substring(wild.getPath().lastIndexOf('/') + 1);
				return Frostbound.id("textures/entity/wolf_sleeping/" + file);
			}
			return wild;
		}
		return wolf.getVariant().value().getAngryTextureId();
	}
}
