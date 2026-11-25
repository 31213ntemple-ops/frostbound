package com.frostbound.mixin.habanero;

import com.frostbound.habanero.LavaPot;
import com.frostbound.habanero.LavaPots;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Saves and loads a cooking pot's lava ({@link LavaPot}) as {@code frostbound:lava}, in vanilla's final createNbt
 * and read, which every save, load and client update goes through, so Farmer's Delight's own overrides needn't be
 * hooked. Does nothing for other block entities.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityLavaMixin {
	@Inject(method = "createNbt", at = @At("RETURN"))
	private void frostbound$saveLava(RegistryWrapper.WrapperLookup registries, CallbackInfoReturnable<NbtCompound> cir) {
		if (this instanceof LavaPot pot && pot.frostbound$hasLava()) {
			cir.getReturnValue().putBoolean(LavaPots.NBT_KEY, true);
		}
	}

	@Inject(method = "read", at = @At("TAIL"))
	private void frostbound$loadLava(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
		if (this instanceof LavaPot pot) {
			pot.frostbound$setLava(nbt.getBoolean(LavaPots.NBT_KEY));
		}
	}
}
