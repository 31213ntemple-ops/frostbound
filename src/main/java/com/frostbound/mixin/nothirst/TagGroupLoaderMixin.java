package com.frostbound.mixin.nothirst;

import com.frostbound.nothirst.NoThirst;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * Tough As Nails puts its canteen-only Water Cleansing enchantment in #minecraft:on_random_loot,
 * #tradeable and #treasure. Tags only add up across data packs, so it's taken back out here, as
 * the enchantment tag files are read.
 */
@Mixin(TagGroupLoader.class)
public abstract class TagGroupLoaderMixin {
	@Shadow
	@Final
	private String dataType;

	@Inject(method = "loadTags", at = @At("RETURN"))
	private void frostbound$removeWaterCleansing(ResourceManager manager,
			CallbackInfoReturnable<Map<Identifier, List<TagGroupLoader.TrackedEntry>>> cir) {
		if (!RegistryKeys.getTagPath(RegistryKeys.ENCHANTMENT).equals(dataType) || !NoThirst.isActive()) {
			return;
		}
		cir.getReturnValue().values().forEach(entries -> entries.removeIf(tracked -> {
			TagEntryAccessor entry = (TagEntryAccessor) tracked.entry();
			return !entry.frostbound$isTag() && NoThirst.WATER_CLEANSING.equals(entry.frostbound$id());
		}));
	}
}
