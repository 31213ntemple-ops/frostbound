package com.frostbound.client.alpha;

import com.frostbound.alpha.AlphaWolves;
import com.frostbound.alpha.MarkOfThePack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.WolfEntityRenderer;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Client side of the Alpha Wolf: renderers, and the Mark of the Pack key (J by default, under Controls → Frostbound). */
public final class AlphaWolfClient {
	public static final KeyBinding MARK_OF_THE_PACK = new KeyBinding("key.frostbound.mark_of_the_pack",
			InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.frostbound");

	private AlphaWolfClient() {}

	public static void init() {
		EntityRendererRegistry.register(AlphaWolves.ALPHA_WOLF, PackWolfRenderer::new);
		EntityRendererRegistry.register(AlphaWolves.PACK_WOLF, PackWolfRenderer::new);
		EntityRendererRegistry.register(AlphaWolves.SUMMONED_WOLF, WolfEntityRenderer::new);

		KeyBindingHelper.registerKeyBinding(MARK_OF_THE_PACK);
		MarkOfThePack.keyName = MARK_OF_THE_PACK::getBoundKeyLocalizedText;
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (MARK_OF_THE_PACK.wasPressed()) {
				if (client.player != null && ClientPlayNetworking.canSend(MarkOfThePack.ActivatePayload.ID)) {
					ClientPlayNetworking.send(new MarkOfThePack.ActivatePayload());
				}
			}
		});
	}
}
