// Author: dishanhai
package com.dishanhai.sh_common_lib.forge.mixin;

import com.dishanhai.sh_common_lib.internal.block.FumoHeadClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomHeadLayer.class)
abstract class FumoHeadLayerMixin {
    // Forge uses SRG names in the packaged game and Mojang names in development.
    @Group(name = "fumoHeadPose", min = 1, max = 1)
    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/entity/layers/CustomHeadLayer;translateToHead(Lcom/mojang/blaze3d/vertex/PoseStack;Z)V"),
            remap = false, require = 0)
    private void shCommonLib$bounce(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity wearer,
            float swing, float amount, float partialTick, float age, float yaw, float pitch, CallbackInfo callback) {
        FumoHeadClient.applyVanillaPose(pose, wearer, partialTick);
    }

    @Group(name = "fumoHeadPose")
    @Inject(method = "m_6494_", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/entity/layers/CustomHeadLayer;m_174483_(Lcom/mojang/blaze3d/vertex/PoseStack;Z)V"),
            remap = false, require = 0)
    private void shCommonLib$bounceSrg(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity wearer,
            float swing, float amount, float partialTick, float age, float yaw, float pitch, CallbackInfo callback) {
        FumoHeadClient.applyVanillaPose(pose, wearer, partialTick);
    }
}
