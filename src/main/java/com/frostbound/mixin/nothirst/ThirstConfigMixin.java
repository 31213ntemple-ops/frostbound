package com.frostbound.mixin.nothirst;

import com.frostbound.nothirst.NoThirst;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tough As Nails reads config/toughasnails/thirst.toml into ThirstConfig (at start-up, and again
 * whenever it reloads the file); every thirst feature checks {@code enableThirst} on it. Forcing it
 * off after each load turns thirst off on both sides, whatever the file says. Only applied when TAN
 * is installed (FrostboundMixinPlugin), and it compiles without TAN, hence {@link Pseudo}.
 */
@Pseudo
@Mixin(targets = "toughasnails.config.ThirstConfig", remap = false)
public abstract class ThirstConfigMixin {
	@Shadow
	public boolean enableThirst;
	@Shadow
	public boolean enableHandDrinking;

	@Inject(method = "load", at = @At("TAIL"), remap = false)
	private void frostbound$noThirst(CallbackInfo ci) {
		if (NoThirst.isActive()) {
			enableThirst = false;
			enableHandDrinking = false;
		}
	}
}
