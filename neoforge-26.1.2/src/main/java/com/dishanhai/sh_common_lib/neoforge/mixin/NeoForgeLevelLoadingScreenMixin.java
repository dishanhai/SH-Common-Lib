// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
abstract class NeoForgeLevelLoadingScreenMixin {
    @Shadow private LevelLoadTracker loadTracker;

    @Inject(method = "tick", at = @At("HEAD"))
    private void shclib$trackWorldProgress(CallbackInfo ci) {
        ClientLoading.worldProgress(loadTracker.hasProgress());
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/LevelLoadTracker;statusView()Lnet/minecraft/server/level/progress/ChunkLoadStatusView;"),
            cancellable = true)
    private void shclib$renderChunks(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ClientLoading.renderWorldProgress(graphics, loadTracker.hasProgress(), loadTracker.serverProgress())) ci.cancel();
    }
}
