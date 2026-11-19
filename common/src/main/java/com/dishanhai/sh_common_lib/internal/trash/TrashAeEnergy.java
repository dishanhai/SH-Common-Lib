// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import java.util.EnumSet;
import net.minecraft.core.Direction;

final class TrashAeEnergy implements TrashCanRuntime, IInWorldGridNodeHost {
    private final TrashCanBlockEntity owner;
    private final IManagedGridNode node;

    TrashAeEnergy(TrashCanBlockEntity owner) {
        this.owner = owner;
        this.node = GridHelper.createManagedNode(owner, (entity, gridNode) -> {})
                .setInWorldNode(true)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setFlags(GridFlags.CANNOT_CARRY)
                .setIdlePowerUsage(0);
    }

    @Override
    public IGridNode getGridNode(Direction side) {
        return node.getNode();
    }

    @Override
    public void tick() {
        if (!node.isReady()) node.create(owner.getLevel(), owner.getBlockPos());
        node.ifPresent(grid -> grid.getEnergyService()
                .extractAEPower(1_000_000_000, Actionable.MODULATE, PowerMultiplier.ONE));
    }

    @Override
    public void close() {
        node.destroy();
    }
}
