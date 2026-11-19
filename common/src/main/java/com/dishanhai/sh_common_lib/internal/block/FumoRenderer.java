// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Draws the displayed stack and the baked plush while it dances. */
public final class FumoRenderer implements BlockEntityRenderer<DishanhaiFumoBlock.DanceEntity> {
    private static final RenderType LAYER = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
    private final BlockRenderDispatcher blocks;
    private final ItemRenderer items;

    public FumoRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
        items = context.getItemRenderer();
    }

    @Override
    public boolean shouldRender(DishanhaiFumoBlock.DanceEntity plush, Vec3 cameraPosition) {
        return (plush.getBlockState().getValue(DishanhaiFumoBlock.DANCING) || !plush.heldItem().isEmpty())
                && BlockEntityRenderer.super.shouldRender(plush, cameraPosition);
    }

    @Override
    public int getViewDistance() {
        return 512;
    }

    @Override
    public void render(DishanhaiFumoBlock.DanceEntity plush, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        var level = plush.getLevel();
        var state = plush.getBlockState();
        boolean dancing = state.getValue(DishanhaiFumoBlock.DANCING);
        var held = plush.heldItem();
        if (level == null || (!dancing && held.isEmpty())) return;

        pose.pushPose();
        if (dancing) {
            FumoMotion.apply(pose, FumoMotion.squash(level.getGameTime(), partialTick));
            blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(LAYER), state,
                    blocks.getBlockModel(state), 1, 1, 1, light, overlay);
        }
        if (!held.isEmpty()) {
            var block = FumoMotion.heldBlock(held);
            var itemModel = items.getModel(held, level, null, 0);
            // Modded world models can depend on chunk render data. Use the actual stack's baked
            // 3D item model whenever available so it can submit its own render layers.
            boolean blockItemModel = held.getItem() instanceof BlockItem
                    && itemModel != items.getItemModelShaper().getModelManager().getMissingModel()
                    && (itemModel.isGui3d() || itemModel.isCustomRenderer());
            FumoMotion.heldItemPose(pose, state.getValue(BlockStateProperties.HORIZONTAL_FACING),
                    blockItemModel || block != null);
            if (blockItemModel) {
                items.render(held, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, itemModel);
            } else if (block != null) {
                pose.translate(-0.5, -0.5, -0.5);
                blocks.renderSingleBlock(block, pose, buffers, light, overlay);
            } else {
                items.render(held, ItemDisplayContext.FIXED, false, pose, buffers, light, overlay, itemModel);
            }
        }
        pose.popPose();
    }
}
