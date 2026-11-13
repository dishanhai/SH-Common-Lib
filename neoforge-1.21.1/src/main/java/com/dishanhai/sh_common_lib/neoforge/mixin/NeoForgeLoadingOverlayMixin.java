// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NeoForgeLoadingOverlay.class, remap = false)
abstract class NeoForgeLoadingOverlayMixin {
    @Shadow @Final private ReloadInstance reload;
    @Shadow private long fadeOutStart;
    @Shadow private float currentProgress;

    @Unique private Matrix4f shclib$guiProjection;
    @Unique private VertexSorting shclib$guiSorting;

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void shclib$captureGuiProjection(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ClientLoading.animation()) {
            shclib$guiProjection = RenderSystem.getProjectionMatrix();
            shclib$guiSorting = RenderSystem.getVertexSorting();
        }
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void shclib$renderStartup(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (shclib$guiProjection != null) {
            // The loader's last draw uses a bottom-up framebuffer projection.
            RenderSystem.setProjectionMatrix(shclib$guiProjection, shclib$guiSorting);
            RenderSystem.applyModelViewMatrix();
            shclib$guiProjection = null;
        }
        if (Minecraft.getInstance().getOverlay() == (Object) this) {
            // Let additional native progress meters occupy the startup status area.
            boolean showIndicator = ClientLoading.animation() && StartupNotificationManager.getCurrentProgress().size() <= 1;
            ClientLoading.renderStartup(graphics, reload, fadeOutStart >= 0, currentProgress, showIndicator);
        }
    }
}
