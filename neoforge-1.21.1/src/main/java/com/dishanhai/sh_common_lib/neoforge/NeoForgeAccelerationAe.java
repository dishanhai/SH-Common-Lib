// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.parts.IPartHost;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Collections;
import java.util.IdentityHashMap;

/** Loaded only when AE2 is present. */
final class NeoForgeAccelerationAe {
    private NeoForgeAccelerationAe() {}

    static boolean tick(BlockEntity entity) {
        if (!(entity instanceof IInWorldGridNodeHost host)) return false;
        var visited = Collections.newSetFromMap(new IdentityHashMap<IGridNode, Boolean>());
        boolean invoked = false;
        for (Direction side : Direction.values()) {
            invoked |= tick(host.getGridNode(side), visited);
            if (entity instanceof IPartHost parts) {
                var part = parts.getPart(side);
                if (part != null) invoked |= tick(part.getGridNode(), visited);
            }
        }
        return invoked;
    }

    private static boolean tick(IGridNode node, java.util.Set<IGridNode> visited) {
        if (node == null || !visited.add(node) || !node.isActive()) return false;
        var service = node.getService(IGridTickable.class);
        if (service == null) return false;
        service.tickingRequest(node, 1);
        return true;
    }
}
