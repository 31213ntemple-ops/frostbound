package com.frostbound.client.imbuement;

import com.frostbound.Frostbound;
import com.frostbound.imbuement.FrostTier;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

/**
 * The frost on Cold, Icy and Frozen gear: drawn over the item (in the inventory, in hand, on the ground)
 * and over worn armour the way the enchantment glint is (same shaders, added on top of the colours, only
 * where the item already is), but with a frost pattern that stays put and glistens instead of sweeping:
 *
 * <ul>
 *   <li>Cold: a sprinkle of pale rime specks (textures/misc/frost_cold.png).</li>
 *   <li>Icy: small frost crystals branching across it (frost_icy.png).</li>
 *   <li>Frozen: dense crystals over a faint icy sheen, a deeper blue (frost_frozen.png).</li>
 * </ul>
 *
 * The textures are pixel art, drawn unblurred and sized so one texel is about one pixel of the item.
 */
public final class FrostRenderLayers {
	/** Width and height of the frost textures. */
	private static final float TEXTURE_SIZE = 128.0f;
	private static final Map<FrostTier, RenderLayer> ITEM = new EnumMap<>(FrostTier.class);
	private static final Map<FrostTier, RenderLayer> ARMOR = new EnumMap<>(FrostTier.class);

	/** Per tier: colour (multiplied into the texture, then squared by the glint blend), and how long a glisten takes. */
	private record Look(float r, float g, float b, float periodSeconds) {}

	private static final Map<FrostTier, Look> LOOKS = Map.of(
			FrostTier.COLD, new Look(0.62f, 0.70f, 0.76f, 4.0f),
			FrostTier.ICY, new Look(0.70f, 0.84f, 0.95f, 3.5f),
			FrostTier.FROZEN, new Look(0.72f, 0.88f, 1.0f, 3.0f));

	static {
		for (FrostTier tier : FrostTier.values()) {
			if (tier == FrostTier.NONE) {
				continue;
			}
			String name = tier.name().toLowerCase(java.util.Locale.ROOT);
			Identifier texture = Frostbound.id("textures/misc/frost_" + name + ".png");
			Look look = LOOKS.get(tier);
			// Items: block-atlas UVs, scaled to one texel per item pixel (see itemScale). Armour: its 64×32 texture's UVs, one texel per pixel.
			ITEM.put(tier, RenderLayer.of("frostbound_frost_item_" + name, VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS, 1536,
					RenderLayer.MultiPhaseParameters.builder()
							.program(RenderPhase.GLINT_PROGRAM)
							.texture(new RenderPhase.Texture(texture, false, false))
							.writeMaskState(RenderPhase.COLOR_MASK)
							.cull(RenderPhase.DISABLE_CULLING)
							.depthTest(RenderPhase.EQUAL_DEPTH_TEST)
							.transparency(RenderPhase.GLINT_TRANSPARENCY)
							.texturing(texturing("frostbound_frost_item_" + name, look, null))
							.build(false)));
			ARMOR.put(tier, RenderLayer.of("frostbound_frost_armor_" + name, VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS, 1536,
					RenderLayer.MultiPhaseParameters.builder()
							.program(RenderPhase.ARMOR_ENTITY_GLINT_PROGRAM)
							.texture(new RenderPhase.Texture(texture, false, false))
							.writeMaskState(RenderPhase.COLOR_MASK)
							.cull(RenderPhase.DISABLE_CULLING)
							.depthTest(RenderPhase.EQUAL_DEPTH_TEST)
							.transparency(RenderPhase.GLINT_TRANSPARENCY)
							.texturing(texturing("frostbound_frost_armor_" + name, look, new float[] {0.5f, 0.25f}))
							.layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
							.build(false)));
		}
	}

	private FrostRenderLayers() {}

	/** Builds the layers now (at client start) rather than on the first frosty item drawn. */
	public static void init() {}

	/** A still texture (scaled, not scrolled) whose brightness swells and fades a little: the frost glistening. Null scale: {@link #itemScale}. */
	private static RenderPhase.Texturing texturing(String name, Look look, float @Nullable [] scale) {
		return new RenderPhase.Texturing(name, () -> {
			float[] s = scale != null ? scale : itemScale();
			RenderSystem.setTextureMatrix(new Matrix4f().scale(s[0], s[1], 1.0f));
			double phase = (Util.getMeasuringTimeMs() / 1000.0) / look.periodSeconds * Math.PI * 2;
			float glisten = 0.88f + 0.12f * (float) Math.sin(phase);
			RenderSystem.setShaderColor(look.r * glisten, look.g * glisten, look.b * glisten, 1.0f);
		}, () -> {
			RenderSystem.resetTextureMatrix();
			RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		});
	}

	/**
	 * Items are drawn from the block atlas, whose size depends on the mods installed, so the frost's scale
	 * is worked out from it (via the missing-texture sprite, 16 px wide) to keep one texel per item pixel.
	 */
	private static float[] itemScale() {
		Sprite sprite = MinecraftClient.getInstance().getBakedModelManager().getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
				.getSprite(MissingSprite.getMissingSpriteId());
		float du = sprite.getMaxU() - sprite.getMinU();
		float dv = sprite.getMaxV() - sprite.getMinV();
		if (du <= 0.0f || dv <= 0.0f) {
			return new float[] {8.0f, 8.0f};
		}
		float atlasWidth = sprite.getContents().getWidth() / du;
		float atlasHeight = sprite.getContents().getHeight() / dv;
		return new float[] {atlasWidth / TEXTURE_SIZE, atlasHeight / TEXTURE_SIZE};
	}

	@Nullable
	public static RenderLayer item(FrostTier tier) {
		return ITEM.get(tier);
	}

	@Nullable
	public static RenderLayer armor(FrostTier tier) {
		return ARMOR.get(tier);
	}
}
