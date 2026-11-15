// Author: dishanhai
package com.dishanhai.sh_common_lib.mixin.jei_ae2;

import com.dishanhai.sh_common_lib.client.jei.JeiAnimatedIconRendering;
import com.dishanhai.sh_common_lib.client.jei.JeiSearchPreferences;
import mezz.jei.api.ingredients.rendering.BatchRenderElement;
import mezz.jei.library.render.ItemStackRenderer;
import mezz.jei.library.render.batch.IItemStackBatchRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.library.render.ItemStackRenderer", remap = false)
abstract class JeiItemRenderOptimizationMixin {
    @Redirect(method = "renderBatch", at = @At(value = "INVOKE",
            target = "Lmezz/jei/library/render/batch/IItemStackBatchRenderer;renderBatch("
                    + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lmezz/jei/library/render/ItemStackRenderer;Ljava/util/List;)V"),
            require = 0)
    private void shclib$extractIconBatch(IItemStackBatchRenderer batch, GuiGraphicsExtractor graphics,
                                       ItemStackRenderer renderer, List<BatchRenderElement<ItemStack>> entries) {
        if (!JeiSearchPreferences.current().optimizeRendering()) {
            batch.renderBatch(graphics, renderer, entries);
            return;
        }
        JeiAnimatedIconRendering.extractBatch(() -> batch.renderBatch(graphics, renderer, entries));
    }
}
