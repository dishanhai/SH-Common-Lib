// Author: dishanhai
package com.dishanhai.sh_common_lib.mixin.jei_ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.dishanhai.sh_common_lib.api.client.ulk.compat.Ae2PatternPreview;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.container.pattern.ContainerPattern", remap = false)
abstract class ExtendedAePatternPreviewMixin implements Ae2PatternPreview {
    @Shadow @Final protected IPatternDetails details;

    @Override
    public AEKey shclib$primaryOutput() {
        if (details == null || details.getOutputs().isEmpty()) return null;
        return details.getPrimaryOutput().what();
    }
}
