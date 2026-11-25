package com.frostbound.mixin.nothirst;

import net.minecraft.registry.tag.TagEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TagEntry.class)
public interface TagEntryAccessor {
	@Accessor("id")
	Identifier frostbound$id();

	@Accessor("tag")
	boolean frostbound$isTag();
}
