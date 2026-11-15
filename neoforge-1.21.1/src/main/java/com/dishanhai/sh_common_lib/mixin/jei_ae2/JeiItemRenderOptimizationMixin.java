// Author: dishanhai
package com.dishanhai.sh_common_lib.mixin.jei_ae2;

import com.dishanhai.sh_common_lib.client.jei.JeiFlatItemModel;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "mezz.jei.library.render.batch.ItemStackBatchRenderer", remap = false)
abstract class JeiItemRenderOptimizationMixin {
    @ModifyVariable(method = "renderItem", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private BakedModel shclib$useFlatGeometry(BakedModel model) {
        return JeiFlatItemModel.forGui(model);
    }
}
