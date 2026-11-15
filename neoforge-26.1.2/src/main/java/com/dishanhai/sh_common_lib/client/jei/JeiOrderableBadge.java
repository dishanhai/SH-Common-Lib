// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_common_lib.api.client.ulk.compat.Ae2CraftableIndex;
import com.dishanhai.sh_common_lib.api.client.ulk.compat.Ae2PatternPreview;
import mezz.jei.api.ingredients.rendering.BatchRenderElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Function;

/** Draws a 6-pixel SHBUI badge in the bottom-right corner of a JEI ingredient. */
public final class JeiOrderableBadge {
    private static final GraphicIcon ICON = resolveIcon();

    private JeiOrderableBadge() {}

    public static <F> void draw(GuiGraphicsExtractor graphics, List<BatchRenderElement<?>> ingredients,
                                Class<F> fluidType, Function<F, ? extends AEKey> fluidKey) {
        if (ICON == null) return;
        var craftables = Ae2CraftableIndex.activeKeys();
        var previewOutput = Ae2PatternPreview.activeOutput();
        if (craftables.isEmpty() && previewOutput == null) return;

        boolean startedLayer = false;
        for (var element : ingredients) {
            Object ingredient = element.ingredient();
            AEKey key = ingredient instanceof ItemStack stack ? AEItemKey.of(stack)
                    : fluidType.isInstance(ingredient) ? fluidKey.apply(fluidType.cast(ingredient)) : null;
            if (key == null || !(key.equals(previewOutput) || craftables.contains(key))) continue;

            if (!startedLayer) {
                graphics.nextStratum();
                startedLayer = true;
            }
            var pose = graphics.pose();
            pose.pushMatrix();
            try {
                pose.translate(element.x() + 10, element.y() + 10).scale(0.375f);
                ICON.draw(graphics, 0, 0, 1, 0xFFFFFFFF);
            } finally {
                pose.popMatrix();
            }
        }
    }

    private static GraphicIcon resolveIcon() {
        try {
            return GraphicIcon.AE2_ORDERABLE;
        } catch (NoSuchFieldError olderShbuiBuild) {
            return null;
        }
    }
}
