// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
abstract class NeoForgeLoadingOverlayMixin {
    @Shadow @Final private ReloadInstance reload;
    @Shadow private long fadeOutStart;

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void shclib$renderStartup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().getOverlay() == (Object) this) {
            ClientLoading.renderStartup(graphics, reload, fadeOutStart >= 0);
        }
    }
}
