// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public final class FumoCurioRenderer implements ICurioRenderer {
    public static void register(Item item) {
        ICurioRenderer.register(item, FumoCurioRenderer::new);
    }

    @Override
    public <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void render(ItemStack stack,
            SlotContext slot, PoseStack pose, SubmitNodeCollector collector, int light, S state,
            RenderLayerParent<S, M> parent, EntityRendererProvider.Context context, float yaw, float pitch) {
        if (!slot.identifier().equals("head") || !(parent.getModel() instanceof HeadedModel model)) return;
        var wearer = slot.entity();
        var helmet = wearer.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.getItem() instanceof FumoHeadItem) return;

        var display = new ItemStackRenderState();
        context.getItemModelResolver().updateForLiving(display, stack, ItemDisplayContext.HEAD, wearer);
        pose.pushPose();
        parent.getModel().root().translateAndRotate(pose);
        model.translateToHead(pose);
        if (!helmet.isEmpty()) pose.translate(0, -0.05, 0);
        CustomHeadLayer.translateToHead(pose, CustomHeadLayer.Transforms.DEFAULT);
        FumoHeadClient.applyPose(pose, wearer, state.partialTick);
        display.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }
}
