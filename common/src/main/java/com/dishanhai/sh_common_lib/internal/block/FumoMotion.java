// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

final class FumoMotion {
    private static final int SQUASH_TICKS = 10;

    private FumoMotion() {}

    static float squash(long tick, float partialTick) {
        double beat = (Math.floorMod(tick, SQUASH_TICKS) + partialTick) / SQUASH_TICKS;
        return (float) ((1 - Math.cos(beat * Math.PI * 2)) * 0.5);
    }

    static void apply(PoseStack pose, float squash) {
        pose.translate(0.5, 0.08F * squash, 0.5);
        pose.scale(1 + 0.18F * squash, 1 - 0.27F * squash, 1 + 0.12F * squash);
        pose.translate(-0.5, 0, -0.5);
    }

    static void applyHead(PoseStack pose, float jump) {
        // The seated model's head display transform puts its feet just above this pivot.
        float feet = 0.42F;
        float squash = (1 - jump) * (1 - jump);
        pose.translate(0, feet + 0.22F * jump, 0);
        pose.scale(1 + 0.18F * squash, 1 - 0.27F * squash, 1 + 0.12F * squash);
        pose.translate(0, -feet, 0);
    }

    static BlockState heldBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        var state = item.getBlock().defaultBlockState();
        return state.getRenderShape() == RenderShape.INVISIBLE ? null : state;
    }

    static void heldItemPose(PoseStack pose, Direction facing, boolean block) {
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(0, 4.2 / 16, 3.7 / 16 - 0.5);
        if (block) {
            pose.scale(0.32F, 0.32F, 0.32F);
        } else {
            pose.mulPose(Axis.XP.rotationDegrees(20));
            pose.scale(0.36F, 0.36F, 0.36F);
        }
    }
}
