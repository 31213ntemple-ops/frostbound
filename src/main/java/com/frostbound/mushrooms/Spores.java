package com.frostbound.mushrooms;

import com.frostbound.Frostbound;
import com.frostbound.config.FrostboundConfig;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;

/**
 * Spores: a count kept on each player rather than an item. Every cap or stem block of a giant mushroom a
 * player harvests adds {@code sporesMin}–{@code sporesMax} (5–7), except cap blocks a burst grew back
 * ({@link GiantCapBlock#REGROWN}) and anything broken in creative. Saved on the player and kept through
 * death; the client shows it in the corner of the screen ({@code client/mushrooms/SporeCounterHud}). No use yet.
 */
public final class Spores {
	private static final FrostboundConfig.GiantMushroomOptions OPTIONS = FrostboundConfig.get().giantMushrooms;

	public static final AttachmentType<Integer> COUNT = AttachmentRegistry.<Integer>builder()
			.persistent(Codec.INT)
			.copyOnDeath()
			.initializer(() -> 0)
			.buildAndRegister(Frostbound.id("spores"));

	/** The rose-taupe of the spore icon, for the puff where spores come off a block. */
	private static final DustParticleEffect PUFF = new DustParticleEffect(new Vector3f(0.85f, 0.62f, 0.52f), 0.9f);

	/** Server → client: the player's spores, and how many they just gained (0 when it's only a sync). */
	public record SyncPayload(int count, int gained) implements CustomPayload {
		public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(Frostbound.id("spores"));
		public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, SyncPayload::count,
				PacketCodecs.VAR_INT, SyncPayload::gained,
				SyncPayload::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** What the client knows of its own player's spores. */
	public static final class ClientState {
		public static int count;

		private ClientState() {}
	}

	private Spores() {}

	public static int get(PlayerEntity player) {
		return player.getAttachedOrElse(COUNT, 0);
	}

	public static void set(PlayerEntity player, int count) {
		player.setAttached(COUNT, Math.max(0, count));
		sync(player, 0);
	}

	/** Adds {@code amount} spores (never past Integer.MAX_VALUE). Returns the new count. */
	public static int add(PlayerEntity player, int amount) {
		int count = (int) Math.min(Integer.MAX_VALUE, (long) get(player) + Math.max(0, amount));
		player.setAttached(COUNT, count);
		sync(player, amount);
		return count;
	}

	/** Spores in a block of a giant mushroom: 5–7 for any cap or stem block, none for one a burst grew back (or anything else). */
	public static boolean yields(BlockState state) {
		return state.getBlock() instanceof GiantCapBlock ? !state.get(GiantCapBlock.REGROWN) : state.isOf(GiantMushrooms.STEM);
	}

	/**
	 * {@code breaker} harvested the mushroom block {@code state} at {@code pos}: a player not in creative
	 * gets its spores, with a puff of them off the block. Returns how many.
	 */
	public static int harvest(ServerWorld world, BlockPos pos, BlockState state, Entity breaker) {
		if (!(breaker instanceof PlayerEntity player) || player.isCreative() || player.isSpectator() || !yields(state)) {
			return 0;
		}
		int min = Math.max(0, OPTIONS.sporesMin);
		int max = Math.max(min, OPTIONS.sporesMax);
		int amount = min + world.random.nextInt(max - min + 1);
		add(player, amount);
		world.spawnParticles(PUFF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
		return amount;
	}

	/** Sends the player their count. Safe to call with any player, on either side. */
	public static void sync(PlayerEntity player, int gained) {
		if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.networkHandler != null
				&& ServerPlayNetworking.canSend(serverPlayer, SyncPayload.ID)) {
			ServerPlayNetworking.send(serverPlayer, new SyncPayload(get(serverPlayer), gained));
		}
	}

	static void init() {
		PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer(), 0));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer, 0));
	}
}
