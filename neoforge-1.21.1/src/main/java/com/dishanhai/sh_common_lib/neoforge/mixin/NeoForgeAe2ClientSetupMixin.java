// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.dishanhai.sh_common_lib.client.loading.ModLoadingStatus;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "appeng.core.AppEngClient", remap = false)
abstract class NeoForgeAe2ClientSetupMixin {
    @Inject(method = "postClientSetup", at = @At("HEAD"), require = 0, remap = false)
    private void shclib$beginAe2Setup(CallbackInfo ci) {
        ModLoadingStatus.beginAe2(StartupNotificationManager::addModMessage);
    }

    @Inject(method = "postClientSetup", at = @At("RETURN"), require = 0, remap = false)
    private void shclib$finishAe2Setup(CallbackInfo ci) {
        ModLoadingStatus.finishAe2(StartupNotificationManager::addModMessage);
    }
}
