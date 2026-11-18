// Author: dishanhai
package com.dishanhai.sh_common_lib.forge.mixin;

import com.dishanhai.sh_common_lib.client.item.AccelerationCloudClock;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
abstract class AccelerationCloudMixin {
    // Explicit development/SRG selectors avoid relying on a Forge refmap for this new hook.
    @ModifyVariable(method = {"renderClouds", "m_253054_"}, at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private double shCommonLib$cloudPosition(double cameraX) {
        // Offset only cloud geometry; keep partialTick in [0,1] for weather/color interpolation.
        return cameraX + Math.floorMod(AccelerationCloudClock.ticks(), 102400L) * .03;
    }
}
