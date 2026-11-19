// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import net.minecraft.core.Direction;

final class ForgeTrashSource implements TrashCanRuntime {
    private final TrashCanBlockEntity owner;

    ForgeTrashSource(TrashCanBlockEntity owner) {
        this.owner = owner;
    }

    @Override
    public void tick() {
        var level = owner.getLevel();
        if (level.getGameTime() % 20 != 0) return;
        for (var side : Direction.values()) {
            var pos = owner.getBlockPos().relative(side);
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof SourceJarTile jar && jar.getSource() > 0) {
                jar.removeSource(jar.getSource());
            }
        }
    }
}
