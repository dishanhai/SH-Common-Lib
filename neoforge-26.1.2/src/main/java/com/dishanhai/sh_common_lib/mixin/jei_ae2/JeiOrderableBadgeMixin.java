// Author: dishanhai
package com.dishanhai.sh_common_lib.mixin.jei_ae2;

import com.dishanhai.sh_common_lib.client.jei.JeiOrderableBadgePlatform;
import com.dishanhai.sh_common_lib.client.jei.JeiSearchPreferences;
import mezz.jei.api.ingredients.rendering.BatchRenderElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Map;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientListRenderer", remap = false)
abstract class JeiOrderableBadgeMixin {
    @Shadow
    private void renderBatch(GuiGraphicsExtractor graphics, Map.Entry<?, List<BatchRenderElement<?>>> batch) {
        throw new AssertionError();
    }

    // Read the call operands because optimized JEI builds reuse renderBatch's parameter slots.
    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At(value = "INVOKE",
            target = "Lmezz/jei/gui/overlay/ingredients/IngredientListRenderer;renderBatch("
                    + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/Map$Entry;)V"), require = 0)
    private void shclib$drawOrderableBadges(@Coerce Object renderer, GuiGraphicsExtractor graphics,
                                          Map.Entry<?, List<BatchRenderElement<?>>> batch) {
        renderBatch(graphics, batch);
        if (JeiSearchPreferences.current().ae2Orderable() && ModList.get().isLoaded("ae2")) {
            JeiOrderableBadgePlatform.draw(graphics, batch.getValue());
        }
    }
}
