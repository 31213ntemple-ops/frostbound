package com.frostbound.client.vessels;

import com.frostbound.vessels.VesselSyncPayload;
import com.frostbound.vessels.Vessels;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** Client side of hearts and vessels: hears the server's sync, and shows the totem-style pop. */
public final class VesselClient {
	private VesselClient() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(VesselSyncPayload.ID, (payload, context) -> {
			Vessels.ClientState.hearts = payload.hearts();
			FrozenHeartHud.onChange(Vessels.ClientState.frozenHeart, payload.frozenHeart(),
					context.client().inGameHud.getTicks());
			Vessels.ClientState.frozenHeart = payload.frozenHeart();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Vessels.ClientState.reset();
			FrozenHeartHud.reset();
		});
		Vessels.clientUseEffect = (player, stack) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (player == client.player) {
				client.gameRenderer.showFloatingItem(stack);
			}
		};
	}
}
