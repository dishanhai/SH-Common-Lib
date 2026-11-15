// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.item;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

public final class CosmicArtisanConstruction {
    private static final int BUILD_LIMIT = 64;
    private static final int ANGEL_LIMIT = 16;
    private static final int DESTRUCTION_LIMIT = 81;
    private static final int RADIUS = 8;

    private CosmicArtisanConstruction() {}

    public static InteractionResult useOn(UseOnContext context, int flags) {
        var player = context.getPlayer();
        if (!Mode.CONSTRUCTION_WAND.enabled(flags) || player == null || player.isShiftKeyDown()) return InteractionResult.PASS;
        var pos = context.getClickedPos();
        var face = context.getClickedFace();
        if (Mode.DESTRUCTION_CORE.enabled(flags)) {
            if (player instanceof ServerPlayer owner && context.getLevel() instanceof ServerLevel level) {
                for (BlockPos target : plane(level, owner, pos, face, DESTRUCTION_LIMIT)) {
                    owner.gameMode.destroyBlock(target);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (!(player.getOffhandItem().getItem() instanceof BlockItem)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer owner && context.getLevel() instanceof ServerLevel level) {
            if (Mode.ANGEL_CORE.enabled(flags)) placeLine(owner, level, pos, face);
            else for (BlockPos anchor : plane(level, owner, pos, face, BUILD_LIMIT)) place(owner, level, anchor, face);
        }
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult useInAir(Player player, int flags) {
        if (!Mode.CONSTRUCTION_WAND.enabled(flags) || !Mode.ANGEL_CORE.enabled(flags) || player.isShiftKeyDown()
                || !(player.getOffhandItem().getItem() instanceof BlockItem)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer owner && player.level() instanceof ServerLevel level) {
            var face = player.getDirection();
            var start = player.blockPosition().below().relative(face);
            placeLine(owner, level, start, face);
        }
        return InteractionResult.SUCCESS;
    }

    private static void placeLine(ServerPlayer player, ServerLevel level, BlockPos source, Direction face) {
        for (int i = 0; i < ANGEL_LIMIT; i++) {
            if (!place(player, level, source.relative(face, i), face)) break;
        }
    }

    private static boolean place(ServerPlayer player, ServerLevel level, BlockPos anchor, Direction face) {
        var stack = player.getOffhandItem();
        var target = anchor.relative(face);
        if (!(stack.getItem() instanceof BlockItem) || !canEdit(level, player, anchor) || !canEdit(level, player, target)
                || !level.getBlockState(target).canBeReplaced()) return false;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(anchor), face, anchor, false);
        return player.gameMode.useItemOn(player, level, stack, InteractionHand.OFF_HAND, hit).consumesAction();
    }

    private static List<BlockPos> plane(ServerLevel level, ServerPlayer player, BlockPos origin, Direction face, int limit) {
        var result = new ArrayList<BlockPos>();
        var pending = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        Block block = level.getBlockState(origin).getBlock();
        pending.add(origin.immutable());
        while (!pending.isEmpty() && result.size() < limit) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos) || !origin.closerThan(pos, RADIUS + 1) || !canEdit(level, player, pos)
                    || !level.getBlockState(pos).is(block)) continue;
            result.add(pos);
            for (Direction direction : Direction.values()) {
                if (direction.getAxis() != face.getAxis()) pending.addLast(pos.relative(direction));
            }
        }
        return result;
    }

    private static boolean canEdit(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos);
    }
}
