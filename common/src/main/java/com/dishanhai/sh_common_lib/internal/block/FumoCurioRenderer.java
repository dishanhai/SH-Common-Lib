// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public final class FumoCurioRenderer implements ICurioRenderer {
    public static void register(Item item) {
        CuriosRendererRegistry.register(item, FumoCurioRenderer::new);
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slot,
            PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float limbSwing, float limbSwingAmount, float partialTick, float age, float yaw, float pitch) {
        if (!slot.identifier().equals("head") || !(parent.getModel() instanceof HeadedModel model)) return;
        var wearer = slot.entity();
        var helmet = wearer.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.getItem() instanceof FumoHeadItem) return;

        pose.pushPose();
        model.getHead().translateAndRotate(pose);
        if (!helmet.isEmpty()) pose.translate(0, -0.05, 0);
        CustomHeadLayer.translateToHead(pose, false);
        FumoHeadClient.applyPose(pose, wearer, partialTick);
        Minecraft.getInstance().getItemRenderer().renderStatic(wearer, stack, ItemDisplayContext.HEAD,
                false, pose, buffers, wearer.level(), light, OverlayTexture.NO_OVERLAY, wearer.getId());
        pose.popPose();
    }
}
