// Author: dishanhai
package com.dishanhai.sh_common_lib.forge.mixin;

import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldOpenFlows.class, remap = false)
abstract class ForgeWorldOpenFlowsMixin {
    @Inject(method = {
            "doLoadLevel(Lnet/minecraft/client/gui/screens/Screen;Ljava/lang/String;ZZZ)V",
            "createFreshLevel", "m_233157_",
            "createLevelFromExistingSettings", "m_245064_"
    }, at = @At("HEAD"), remap = false)
    private void shclib$beginWorld(CallbackInfo ci) {
        ClientLoading.beginLocalWorld();
    }
}
