package com.dishanhai.sh_common_lib.internal.block;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class BlockEntityData {
    private BlockEntityData() {}

    public static CompoundTag save(BlockEntity entity, RegistryAccess registries) {
        return entity.saveWithoutMetadata();
    }
}
