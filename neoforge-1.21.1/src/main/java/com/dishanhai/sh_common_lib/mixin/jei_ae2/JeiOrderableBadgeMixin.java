// Author: dishanhai
package com.dishanhai.sh_common_lib.mixin.jei_ae2;

import appeng.api.stacks.AEFluidKey;
import com.dishanhai.sh_common_lib.client.jei.JeiOrderableBadge;
import com.dishanhai.sh_common_lib.client.jei.JeiSearchPreferences;
import mezz.jei.api.ingredients.rendering.BatchRenderElement;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.fluids.FluidStack;
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
    private void renderBatch(GuiGraphics graphics, Map.Entry<?, List<BatchRenderElement<?>>> batch) {
        throw new AssertionError();
    }

    // Read the call operands because optimized JEI builds reuse renderBatch's parameter slots.
    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At(value = "INVOKE",
            target = "Lmezz/jei/gui/overlay/ingredients/IngredientListRenderer;renderBatch("
                    + "Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/Map$Entry;)V"), require = 0)
    private void shclib$drawOrderableBadges(@Coerce Object renderer, GuiGraphics graphics,
                                          Map.Entry<?, List<BatchRenderElement<?>>> batch) {
        renderBatch(graphics, batch);
        if (JeiSearchPreferences.current().ae2Orderable() && ModList.get().isLoaded("ae2")) {
            JeiOrderableBadge.draw(graphics, batch.getValue(), FluidStack.class, AEFluidKey::of);
        }
    }
}
