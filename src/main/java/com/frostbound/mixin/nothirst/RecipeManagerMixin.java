package com.frostbound.mixin.nothirst;

import com.frostbound.Frostbound;
import com.frostbound.nothirst.NoThirst;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Drops every recipe that makes one of Tough As Nails' thirst items, and every water purifier
 * recipe, once the recipes have loaded (so data packs' recipes for them go too).
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
	@Shadow
	@Final
	private RegistryWrapper.WrapperLookup registryLookup;
	@Shadow
	private Multimap<RecipeType<?>, RecipeEntry<?>> recipesByType;
	@Shadow
	private Map<Identifier, RecipeEntry<?>> recipesById;

	@Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V", at = @At("TAIL"))
	private void frostbound$removeThirstRecipes(Map<Identifier, JsonElement> map, ResourceManager manager, Profiler profiler, CallbackInfo ci) {
		if (!NoThirst.isActive()) {
			return;
		}
		ImmutableMultimap.Builder<RecipeType<?>, RecipeEntry<?>> byType = ImmutableMultimap.builder();
		ImmutableMap.Builder<Identifier, RecipeEntry<?>> byId = ImmutableMap.builder();
		int removed = 0;
		for (RecipeEntry<?> entry : recipesById.values()) {
			if (NoThirst.isRemoved(entry.value(), registryLookup)) {
				removed++;
			} else {
				byType.put(entry.value().getType(), entry);
				byId.put(entry.id(), entry);
			}
		}
		recipesByType = byType.build();
		recipesById = byId.build();
		if (removed > 0) {
			Frostbound.LOGGER.info("No Thirst: removed {} Tough As Nails thirst recipes", removed);
		}
	}
}
