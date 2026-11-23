package com.frostbound.client.alpha;

import com.frostbound.Frostbound;
import com.frostbound.alpha.DrainingBite;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Draining Bite in the health bar: each heart a bite took off your max health is drawn as a hollow,
 * dark heart (textures/gui/sprites/hud/heart/drained.png) after your other hearts, pulsing slowly, so
 * it reads as "locked away for now" rather than damage. Positioning is in mixin.vessels.client.InGameHudMixin.
 */
public final class DrainedHeartsHud {
	private static final Identifier SPRITE = Frostbound.id("hud/heart/drained");

	private DrainedHeartsHud() {}

	/** Hearts the player is drained of (read from the synced max health modifier). */
	public static int count(PlayerEntity player) {
		return DrainingBite.drainedHearts(player);
	}

	public static void draw(DrawContext context, int ticks, int x, int y) {
		RenderSystem.enableBlend();
		float pulse = 0.7f + 0.3f * (float) Math.sin(ticks * 0.15);
		context.setShaderColor(1.0f, 1.0f, 1.0f, pulse);
		context.drawGuiTexture(SPRITE, x, y, 9, 9);
		context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.disableBlend();
	}
}
