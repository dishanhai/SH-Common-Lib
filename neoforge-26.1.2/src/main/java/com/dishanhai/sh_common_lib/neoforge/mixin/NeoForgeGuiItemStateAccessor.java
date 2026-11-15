// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface NeoForgeGuiItemStateAccessor {
    @Accessor("animated")
    void shclib$setAnimated(boolean animated);
}
