package com.dishanhai.sh_common_lib.api.block;

import com.dishanhai.sh_common_lib.internal.block.BlockEntityData;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Exports block information on the thread that owns the source world. */
public final class BlockInspection {
    private BlockInspection() {}

    /** Returns the block ID and its current properties in vanilla command syntax. */
    public static String serializeState(BlockState state) {
        return BlockStateParser.serialize(state);
    }

    /**
     * Returns a new tag with the block entity's saved data, excluding its type ID and position.
     * Minecraft 1.21.1 includes persistent data components. Use the source world's registries.
     * A client-side block entity can only represent the data available to that client.
     */
    public static CompoundTag saveBlockEntityData(BlockEntity entity, RegistryAccess registries) {
        return BlockEntityData.save(entity, registries);
    }
}
