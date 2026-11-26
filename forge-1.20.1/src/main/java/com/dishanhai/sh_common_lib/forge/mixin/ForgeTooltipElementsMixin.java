// Author: dishanhai
package com.dishanhai.sh_common_lib.forge.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(GuiGraphics.class)
abstract class ForgeTooltipElementsMixin {
    @ModifyVariable(method = "renderComponentTooltipFromElements", at = @At("HEAD"),
            argsOnly = true, ordinal = 0, remap = false)
    private List<Either<FormattedText, TooltipComponent>> shclib$copyTooltipElements(
            List<Either<FormattedText, TooltipComponent>> elements) {
        // Forge exposes this list to GatherComponents listeners, which may add or remove entries.
        return new ArrayList<>(elements);
    }
}
