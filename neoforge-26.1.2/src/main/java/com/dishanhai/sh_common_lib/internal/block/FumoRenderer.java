// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Submits the plush and the model of its displayed stack. */
public final class FumoRenderer implements BlockEntityRenderer<DishanhaiFumoBlock.DanceEntity, FumoRenderer.State> {
    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blocks;
    private final ItemModelResolver items;

    public FumoRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.blockModelResolver();
        items = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
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
    public void extractRenderState(DishanhaiFumoBlock.DanceEntity plush, State state, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(plush, state, partialTick, cameraPosition, breaking);
        var level = plush.getLevel();
        var block = plush.getBlockState();
        state.model.clear();
        state.heldBlock.clear();
        state.heldItem.clear();
        state.heldItemModelIsBlock = false;
        if (level == null) return;

        state.facing = block.getValue(BlockStateProperties.HORIZONTAL_FACING);
        state.squash = 0;
        if (block.getValue(DishanhaiFumoBlock.DANCING)) {
            state.squash = FumoMotion.squash(level.getGameTime(), partialTick);
            blocks.update(state.model, block, DISPLAY_CONTEXT);
        }
        var held = plush.heldItem();
        if (held.isEmpty()) return;
        if (held.getItem() instanceof BlockItem) {
            items.updateForTopItem(state.heldItem, held, ItemDisplayContext.NONE, level, null, 0);
            if (!state.heldItem.isEmpty() && state.heldItem.usesBlockLight()) {
                state.heldItemModelIsBlock = true;
                return;
            }
        }
        var heldBlock = FumoMotion.heldBlock(held);
        if (heldBlock != null) blocks.update(state.heldBlock, heldBlock, DISPLAY_CONTEXT);
        if (state.heldBlock.isEmpty()) {
            items.updateForTopItem(state.heldItem, held, ItemDisplayContext.FIXED, level, null, 0);
        } else {
            state.heldItem.clear();
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        FumoMotion.apply(pose, state.squash);
        state.model.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        if (!state.heldBlock.isEmpty()) {
            FumoMotion.heldItemPose(pose, state.facing, true);
            pose.translate(-0.5, -0.5, -0.5);
            state.heldBlock.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        } else if (!state.heldItem.isEmpty()) {
            FumoMotion.heldItemPose(pose, state.facing, state.heldItemModelIsBlock);
            state.heldItem.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        }
        pose.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        private final BlockModelRenderState model = new BlockModelRenderState();
        private final BlockModelRenderState heldBlock = new BlockModelRenderState();
        private final ItemStackRenderState heldItem = new ItemStackRenderState();
        private Direction facing = Direction.NORTH;
        private float squash;
        private boolean heldItemModelIsBlock;
    }
}
