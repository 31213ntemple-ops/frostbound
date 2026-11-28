package com.frostbound.client.mushrooms;

import com.frostbound.mushrooms.GiantMushrooms;
import com.frostbound.mushrooms.MushroomSproutBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * The countdown over a mushroom sprout ("1.6"), facing the camera like a name tag, yellow turning red
 * as it runs out, and seen through blocks (faintly) so a sprout behind you still shows.
 */
public class SproutCountdownRenderer implements BlockEntityRenderer<MushroomSproutBlockEntity> {
	private static final float SCALE = 0.03f;
	private final TextRenderer text;

	public SproutCountdownRenderer(BlockEntityRendererFactory.Context context) {
		this.text = context.getTextRenderer();
	}

	public static void init() {
		BlockEntityRendererFactories.register(GiantMushrooms.SPROUT_ENTITY, SproutCountdownRenderer::new);
	}

	@Override
	public void render(MushroomSproutBlockEntity sprout, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (sprout.getWorld() == null || sprout.burstAt() == 0) {
			return;
		}
		double left = sprout.burstAt() - (sprout.getWorld().getTime() + tickDelta);
		String label = MushroomSproutBlockEntity.countdownText(left);
		float urgency = MathHelper.clamp(1.0f - (float) left / 40.0f, 0.0f, 1.0f);
		int green = (int) MathHelper.lerp(urgency, 220, 40);
		int color = 0xFF0000 | (green << 8) | 0x20;

		matrices.push();
		matrices.translate(0.5, 0.8, 0.5);
		matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
		float pulse = 1.0f + 0.15f * urgency * (float) Math.abs(Math.sin(left * 0.6));
		matrices.scale(SCALE * pulse, -SCALE * pulse, SCALE * pulse);
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float x = -text.getWidth(label) / 2.0f;
		int background = (int) (MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;
		text.draw(label, x, 0, 0x60000000 | (color & 0xFFFFFF), false, matrix, vertexConsumers,
				TextRenderer.TextLayerType.SEE_THROUGH, background, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		text.draw(label, x, 0, 0xFF000000 | color, false, matrix, vertexConsumers,
				TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		matrices.pop();
	}
}
