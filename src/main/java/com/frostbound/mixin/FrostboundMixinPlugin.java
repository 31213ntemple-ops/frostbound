package com.frostbound.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Leaves out the snow-in-plants mixins when another mod does that job (Snow! Real Magic),
 * so the two don't fight over snow placement and plant block states, the Tough As Nails
 * mixins (thirst config, campfire warmth) when TAN isn't installed, and the cooking pot mixins
 * (lava pots) without Farmer's Delight.
 *
 * Must not touch Minecraft classes: it runs before they're allowed to load.
 */
public final class FrostboundMixinPlugin implements IMixinConfigPlugin {
	/** Keep in step with SnowyPlants.ENABLED. */
	private static final boolean SNOWY_PLANTS = !FabricLoader.getInstance().isModLoaded("snowrealmagic");
	private static final String PACKAGE = "com.frostbound.mixin.";

	/** Mixins that only exist for snow in plants. */
	private static final Set<String> SNOWY_PLANT_MIXINS = Set.of(
			PACKAGE + "environment.BlockMixin",
			PACKAGE + "environment.FreezeTopLayerFeatureMixin",
			PACKAGE + "environment.ServerWorldMixin",
			PACKAGE + "environment.SnowyBlockMixin");

	/** Target Tough As Nails classes, so only applied when TAN is installed. */
	private static final Set<String> TAN_MIXINS = Set.of(
			PACKAGE + "nothirst.ThirstConfigMixin",
			PACKAGE + "campfire.TemperatureHelperImplMixin");

	/** Target Farmer's Delight's cooking pot (a pot can hold lava), so only applied when it's installed. */
	private static final Set<String> FARMERS_DELIGHT_MIXINS = Set.of(
			PACKAGE + "habanero.CookingPotBlockEntityMixin",
			PACKAGE + "habanero.client.CookingPotScreenMixin");

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (FARMERS_DELIGHT_MIXINS.contains(mixinClassName)) {
			return FabricLoader.getInstance().isModLoaded("farmersdelight");
		}
		if (TAN_MIXINS.contains(mixinClassName)) {
			return FabricLoader.getInstance().isModLoaded("toughasnails");
		}
		return SNOWY_PLANTS || !SNOWY_PLANT_MIXINS.contains(mixinClassName);
	}

	@Override
	public void onLoad(String mixinPackage) {}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
