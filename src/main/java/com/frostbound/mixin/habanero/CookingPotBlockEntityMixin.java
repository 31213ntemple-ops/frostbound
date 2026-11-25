package com.frostbound.mixin.habanero;

import com.frostbound.habanero.LavaPot;
import com.frostbound.habanero.LavaPots;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Farmer's Delight's cooking pot can hold lava ({@link LavaPots}). Only Farmer's Delight's own methods are hooked,
 * by name (the same in every 1.21.1 build and in dev), so Frostbound needn't compile against it:
 *
 * <ul>
 *   <li>getMatchingRecipe: in lava, only flame extract; without lava, never flame extract.</li>
 *   <li>processCooking: true when a dish is done; in lava that was flame extract, which uses the lava up.</li>
 *   <li>writeItems: what the pot sends to clients; the lava goes with it (saving to disk is in BlockEntityLavaMixin).</li>
 *   <li>animationTick (client): lava pops out of a lava pot now and then.</li>
 * </ul>
 *
 * Only applied with Farmer's Delight installed (FrostboundMixinPlugin).
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity", remap = false)
public abstract class CookingPotBlockEntityMixin implements LavaPot {
	@Unique
	private boolean frostbound$lava;

	@Override
	public boolean frostbound$hasLava() {
		return frostbound$lava;
	}

	@Override
	public void frostbound$setLava(boolean lava) {
		frostbound$lava = lava;
	}

	@Inject(method = "getMatchingRecipe", at = @At("RETURN"), cancellable = true, remap = false)
	private void frostbound$lavaRecipes(CallbackInfoReturnable<Optional<? extends RecipeEntry<?>>> cir) {
		Optional<? extends RecipeEntry<?>> recipe = cir.getReturnValue();
		if (recipe != null && recipe.isPresent() && !LavaPots.mayCook(frostbound$lava, recipe.get().id())) {
			cir.setReturnValue(Optional.empty());
		}
	}

	@Inject(method = "processCooking", at = @At("RETURN"), remap = false)
	private void frostbound$useUpLava(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && frostbound$lava) {
			BlockEntity self = (BlockEntity) (Object) this;
			if (self.getWorld() != null) {
				LavaPots.setLava(self.getWorld(), self.getPos(), false);
			}
		}
	}

	@Inject(method = "writeItems", at = @At("RETURN"), remap = false)
	private void frostbound$syncLava(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfoReturnable<NbtCompound> cir) {
		cir.getReturnValue().putBoolean(LavaPots.NBT_KEY, frostbound$lava);
	}

	@Inject(method = "animationTick", at = @At("TAIL"), remap = false)
	private static void frostbound$lavaPops(CallbackInfo ci, @Local(argsOnly = true) World world, @Local(argsOnly = true) BlockPos pos) {
		if (!LavaPots.hasLava(world, pos)) {
			return;
		}
		Random random = world.random;
		if (random.nextInt(12) == 0) {
			world.addParticle(ParticleTypes.LAVA, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.6,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.0, 0.0);
		}
		if (random.nextInt(60) == 0) {
			world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLOCK_LAVA_POP, SoundCategory.BLOCKS,
					0.3f, 0.9f + random.nextFloat() * 0.2f, false);
		}
	}
}
