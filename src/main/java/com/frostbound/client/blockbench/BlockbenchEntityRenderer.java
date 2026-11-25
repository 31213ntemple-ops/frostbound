package com.frostbound.client.blockbench;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

/**
 * Draws a mob with its Blockbench model and the texture made with it. Register it like any renderer:
 *
 * <pre>
 * EntityModelLayer layer = BlockbenchModels.registerLayer(FrostSpriteModel.ID);
 * EntityRendererRegistry.register(MY_ENTITY, ctx -&gt; new BlockbenchEntityRenderer&lt;&gt;(ctx,
 *         new FrostSpriteModel(ctx.getPart(layer)), 0.4f));
 * </pre>
 */
public class BlockbenchEntityRenderer<T extends MobEntity, M extends BlockbenchEntityModel<T>> extends MobEntityRenderer<T, M> {
	public BlockbenchEntityRenderer(EntityRendererFactory.Context context, M model, float shadowRadius) {
		super(context, model, shadowRadius);
	}

	@Override
	public Identifier getTexture(T entity) {
		return BlockbenchModels.texture(getModel().model);
	}
}
