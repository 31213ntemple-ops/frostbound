package com.frostbound.mushrooms;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

/** A sprout's fuse: the world time it bursts at, synced to clients for the countdown. */
public class MushroomSproutBlockEntity extends BlockEntity {
	/** World time it bursts at; 0 until {@link #light} sets it (a sprout placed by hand bursts at once). */
	private long burstAt;

	public MushroomSproutBlockEntity(BlockPos pos, BlockState state) {
		super(GiantMushrooms.SPROUT_ENTITY, pos, state);
	}

	public void light(long now, int fuseTicks) {
		burstAt = now + fuseTicks;
		markDirty();
	}

	public long burstAt() {
		return burstAt;
	}

	public long ticksLeft() {
		return world == null ? 0 : burstAt - world.getTime();
	}

	/** "1.6": seconds left, one decimal, never below 0. */
	public static String countdownText(double ticksLeft) {
		return String.format(Locale.ROOT, "%.1f", Math.max(0.0, ticksLeft) / 20.0);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putLong("BurstAt", burstAt);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		burstAt = nbt.getLong("BurstAt");
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}
}
