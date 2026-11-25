package com.frostbound.mixin.habanero.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a menu sits on screen (for drawing over Farmer's Delight's cooking pot menu). */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
	@Accessor("x")
	int frostbound$getX();

	@Accessor("y")
	int frostbound$getY();
}
