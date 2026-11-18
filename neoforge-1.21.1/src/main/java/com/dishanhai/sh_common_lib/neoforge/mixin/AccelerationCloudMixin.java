// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.dishanhai.sh_common_lib.client.item.AccelerationCloudClock;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
abstract class AccelerationCloudMixin {
    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double shCommonLib$cloudPosition(double cameraX) {
        return cameraX + Math.floorMod(AccelerationCloudClock.ticks(), 102400L) * .03;
    }
}
