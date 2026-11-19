// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;

final class TrashStress implements TrashCanRuntime {
    private final TrashCanBlockEntity owner;
    private Set<KineticNetwork> attached = Set.of();
    private int previousLimit = -1;

    TrashStress(TrashCanBlockEntity owner) {
        this.owner = owner;
    }

    @Override
    public void tick() {
        var level = owner.getLevel();
        var pos = owner.getBlockPos();
        var networks = new HashSet<KineticNetwork>();
        for (var side : Direction.values()) {
            var neighbor = pos.relative(side);
            if (!level.hasChunkAt(neighbor) || !(level.getBlockEntity(neighbor) instanceof KineticBlockEntity shaft)
                    || !shaft.hasNetwork()) continue;
            var state = shaft.getBlockState();
            if (state.getBlock() instanceof IRotate rotation
                    && rotation.hasShaftTowards(level, neighbor, state, side.getOpposite())) {
                networks.add(shaft.getOrCreateNetwork());
            }
        }

        int limit = owner.stressLimit();
        for (var network : attached) {
            if (!networks.contains(network)) update(network, null);
        }
        for (var network : networks) {
            if (limit != previousLimit || !attached.contains(network)) update(network, limit);
        }
        attached = networks;
        previousLimit = limit;
    }

    private void update(KineticNetwork network, Integer limit) {
        if (network instanceof TrashStressNetwork limited) {
            limited.shclib$setLimit(owner.getBlockPos(), limit);
            network.updateCapacity();
        }
    }

    @Override
    public void close() {
        for (var network : attached) update(network, null);
    }
}
