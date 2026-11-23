package com.frostbound.vessels;

import com.frostbound.Frostbound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server → client: the player's permanent hearts and frozen heart. Sent on join, on respawn and
 * whenever either changes. The client keeps it in {@link Vessels.ClientState}.
 */
public record VesselSyncPayload(int hearts, float frozenHeart) implements CustomPayload {
	public static final CustomPayload.Id<VesselSyncPayload> ID = new CustomPayload.Id<>(Frostbound.id("vessel_sync"));

	public static final PacketCodec<RegistryByteBuf, VesselSyncPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, VesselSyncPayload::hearts,
			PacketCodecs.FLOAT, VesselSyncPayload::frozenHeart,
			VesselSyncPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
