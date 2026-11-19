// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.dishanhai.sh_common_lib.internal.block.FumoHeadClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomHeadLayer.class)
abstract class FumoHeadLayerMixin {
    @Inject(method = "submit", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/entity/layers/CustomHeadLayer;translateToHead(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/layers/CustomHeadLayer$Transforms;)V"))
    private void shCommonLib$bounce(PoseStack pose, SubmitNodeCollector collector, int light,
            LivingEntityRenderState state, float yaw, float pitch, CallbackInfo callback) {
        var level = Minecraft.getInstance().level;
        if (level != null && state instanceof AvatarRenderState avatar
                && level.getEntity(avatar.id) instanceof LivingEntity wearer) {
            FumoHeadClient.applyVanillaPose(pose, wearer, state.partialTick);
        }
    }
}
