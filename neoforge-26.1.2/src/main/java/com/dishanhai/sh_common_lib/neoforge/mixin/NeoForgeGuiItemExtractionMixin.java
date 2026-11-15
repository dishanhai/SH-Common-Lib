// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.dishanhai.sh_common_lib.client.jei.JeiAnimatedIconRendering;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GuiGraphicsExtractor.class)
abstract class NeoForgeGuiItemExtractionMixin {
    @ModifyArg(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;"
                    + "addItem(Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;)V"), index = 0, require = 0)
    private GuiItemRenderState shclib$keepJeiIconUntilNextRefresh(GuiItemRenderState state) {
        var item = state.itemStackRenderState();
        if (JeiAnimatedIconRendering.useAtlasCache(item)) {
            ((NeoForgeGuiItemStateAccessor) item).shclib$setAnimated(false);
        }
        return state;
    }
}
