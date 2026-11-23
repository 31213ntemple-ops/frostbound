package com.frostbound.campfire;

import com.frostbound.Frostbound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client → server: one click in the fire-starting minigame, and whether it landed in the green. */
public record FireStarterAttemptPayload(boolean hit) implements CustomPayload {
	public static final CustomPayload.Id<FireStarterAttemptPayload> ID =
			new CustomPayload.Id<>(Frostbound.id("fire_starter_attempt"));

	public static final PacketCodec<RegistryByteBuf, FireStarterAttemptPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOL, FireStarterAttemptPayload::hit,
			FireStarterAttemptPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
