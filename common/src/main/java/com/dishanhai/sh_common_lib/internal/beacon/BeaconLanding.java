// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class BeaconLanding {
    private BeaconLanding() {}

    public static boolean safe(ServerLevel level, ServerPlayer player, Vec3 feet, boolean supported) {
        var pos = BlockPos.containing(feet);
        if (!level.hasChunkAt(pos) || !level.isInWorldBounds(pos) || !level.isInWorldBounds(pos.above())) return false;
        var bounds = player.getBoundingBox().move(feet.subtract(player.position()));
        if (!level.getWorldBorder().isWithinBounds(bounds) || !level.noCollision(player, bounds)
                || level.getFluidState(pos).is(FluidTags.LAVA)) return false;
        var floor = level.getBlockState(pos.below());
        if (floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)
                || level.getBlockState(pos).is(Blocks.FIRE) || level.getBlockState(pos).is(Blocks.SOUL_FIRE)) return false;
        return !supported || floor.isFaceSturdy(level, pos.below(), Direction.UP);
    }
}
