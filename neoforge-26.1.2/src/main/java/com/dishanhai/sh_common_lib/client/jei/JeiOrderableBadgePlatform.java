// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import appeng.api.stacks.AEFluidKey;
import mezz.jei.api.ingredients.rendering.BatchRenderElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/** Links AE2 only after the JEI mixin has checked that it is installed. */
public final class JeiOrderableBadgePlatform {
    private JeiOrderableBadgePlatform() {}

    public static void draw(GuiGraphicsExtractor graphics, List<BatchRenderElement<?>> ingredients) {
        JeiOrderableBadge.draw(graphics, ingredients, FluidStack.class, AEFluidKey::of);
    }
}
