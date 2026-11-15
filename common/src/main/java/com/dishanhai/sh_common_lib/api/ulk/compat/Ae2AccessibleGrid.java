// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.compat;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartHost;
import appeng.api.storage.ITerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Finds a network the player can reach from the current position or an inventory terminal. */
public final class Ae2AccessibleGrid {
    private Ae2AccessibleGrid() {}

    public static IGrid find(ServerPlayer player, double reach, Predicate<BlockPos> reachable,
                             Predicate<ITerminalHost> usable, IntFunction<IGridNode> wirelessNode) {
        if (player.isSpectator()) return null;
        var nearby = nearbyTerminal(player, reach, reachable, usable);
        if (nearby != null) return nearby;

        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var grid = onlineGrid(wirelessNode.apply(slot));
            if (grid != null) return grid;
        }
        return null;
    }

    private static IGrid nearbyTerminal(ServerPlayer player, double reach, Predicate<BlockPos> reachable,
                                        Predicate<ITerminalHost> usable) {
        ServerLevel level = (ServerLevel) player.level();
        double range = Math.min(reach + 1, 16);
        int minX = SectionPos.blockToSectionCoord(player.getX() - range);
        int maxX = SectionPos.blockToSectionCoord(player.getX() + range);
        int minZ = SectionPos.blockToSectionCoord(player.getZ() - range);
        int maxZ = SectionPos.blockToSectionCoord(player.getZ() + range);
        IGrid nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof IPartHost host)) continue;
                    var pos = blockEntity.getBlockPos();
                    double distance = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance >= nearestDistance || !reachable.test(pos)) continue;
                    for (var side : Direction.values()) {
                        var part = host.getPart(side);
                        if (!(part instanceof ITerminalHost terminal) || !usable.test(terminal)) continue;
                        var grid = onlineGrid(part.getGridNode());
                        if (grid != null) {
                            nearest = grid;
                            nearestDistance = distance;
                            break;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private static IGrid onlineGrid(IGridNode node) {
        return node != null && node.isActive() ? node.getGrid() : null;
    }
}
