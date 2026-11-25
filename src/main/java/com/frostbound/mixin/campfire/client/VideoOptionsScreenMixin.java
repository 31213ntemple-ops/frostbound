package com.frostbound.mixin.campfire.client;

import com.frostbound.client.campfire.WarmthRing;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

/** Adds the Campfire Warmth Ring switch to the end of Options → Video Settings (with Tough As Nails). */
@Mixin(VideoOptionsScreen.class)
public abstract class VideoOptionsScreenMixin {
	@ModifyReturnValue(method = "getOptions", at = @At("RETURN"))
	private static SimpleOption<?>[] frostbound$campfireRing(SimpleOption<?>[] options) {
		if (!WarmthRing.available()) {
			return options;
		}
		SimpleOption<?>[] more = Arrays.copyOf(options, options.length + 1);
		more[options.length] = WarmthRing.option();
		return more;
	}
}
