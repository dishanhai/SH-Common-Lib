// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

final class PlatformTrashCanEntity extends TrashCanBlockEntity {
    PlatformTrashCanEntity(BlockPos pos, BlockState state) {
        super(TrashCanPlatform.ENTITY.get(), pos, state, TrashCanPlatform.factories());
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider lookup) {
        super.loadAdditional(input, lookup);
        var items = NonNullList.withSize(Extension.values().length, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items, lookup);
        restoreModules(items);
    }

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider lookup) {
        super.saveAdditional(output, lookup);
        ContainerHelper.saveAllItems(output, savedModules(), lookup);
    }

    @Override
    void invalidateModuleCapabilities() {
        if (level instanceof ServerLevel server) server.invalidateCapabilities(worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        closeModules();
    }
}
