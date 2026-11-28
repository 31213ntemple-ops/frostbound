package com.frostbound.client.mushrooms;

import com.frostbound.Frostbound;
import com.frostbound.mushrooms.Spores;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The spore counter: the spore icon and your count in the bottom-right corner, once you have any, with a
 * "+6" that rises off it as you gain them (adding up while you keep harvesting, fading a moment after you stop).
 * Icon: textures/gui/sprites/hud/spores.png (from tools/MushroomTextures.java).
 */
public final class SporeCounterHud {
	private static final Identifier ICON = Frostbound.id("hud/spores");
	/** How long (ticks) the "+n" stays after the last gain, and how long it fades out at the end. */
	private static final int GAIN_TICKS = 40, FADE_TICKS = 15;
	private static final int TEXT = 0xF0D2BA, GAIN = 0xE8B49A;

	private static int gained;
	private static int gainedAt = -1000;

	private SporeCounterHud() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Spores.SyncPayload.ID, (payload, context) -> {
			Spores.ClientState.count = payload.count();
			if (payload.gained() > 0) {
				int now = context.client().inGameHud.getTicks();
				gained = now - gainedAt > GAIN_TICKS ? payload.gained() : gained + payload.gained();
				gainedAt = now;
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Spores.ClientState.count = 0;
			gained = 0;
			gainedAt = -1000;
		});
		HudRenderCallback.EVENT.register((context, tickCounter) -> render(context, tickCounter.getTickDelta(false)));
	}

	private static void render(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		int count = Spores.ClientState.count;
		if (client.player == null || client.options.hudHidden || count <= 0) {
			return;
		}
		String label = String.format("%,d", count);
		int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
		int textX = width - 4 - client.textRenderer.getWidth(label);
		int iconX = textX - 18, iconY = height - 20;
		context.drawGuiTexture(ICON, iconX, iconY, 16, 16);
		context.drawTextWithShadow(client.textRenderer, label, textX, iconY + 5, TEXT);

		float age = client.inGameHud.getTicks() - gainedAt + tickDelta;
		if (gained > 0 && age < GAIN_TICKS) {
			float alpha = MathHelper.clamp((GAIN_TICKS - age) / FADE_TICKS, 0.0f, 1.0f);
			int rise = (int) Math.min(6, age);
			String plus = "+" + gained;
			int a = Math.max(8, (int) (alpha * 255));
			context.drawTextWithShadow(client.textRenderer, plus, width - 4 - client.textRenderer.getWidth(plus),
					iconY - 6 - rise, a << 24 | GAIN);
		}
	}
}
