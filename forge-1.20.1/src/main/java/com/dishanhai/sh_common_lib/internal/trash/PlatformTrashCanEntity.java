// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

final class PlatformTrashCanEntity extends TrashCanBlockEntity {
    private final Map<Capability<?>, LazyOptional<?>> capabilities = new IdentityHashMap<>();

    PlatformTrashCanEntity(BlockPos pos, BlockState state) {
        super(TrashCanPlatform.ENTITY.get(), pos, state, TrashCanPlatform.factories());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        var items = NonNullList.withSize(Extension.values().length, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        restoreModules(items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, savedModules());
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction direction) {
        if (isRemoved()) return LazyOptional.empty();
        if (capability == ForgeCapabilities.ITEM_HANDLER) return capability(capability, () -> TrashSinks.ITEMS);
        if (capability == ForgeCapabilities.FLUID_HANDLER) return capability(capability, () -> TrashSinks.FLUIDS);
        if (hasModule(Extension.ENERGY)) {
            if (capability == ForgeCapabilities.ENERGY) return capability(capability, () -> TrashSinks.ENERGY);
            if (TrashCanPlatform.aeLoaded()) {
                var ae = ForgeTrashAeCapability.get(this, capability);
                if (ae.isPresent()) return ae;
            }
        }
        if (hasModule(Extension.CHEMICAL) && TrashCanPlatform.available(Extension.CHEMICAL)) {
            var chemical = ForgeTrashChemicals.get(this, capability);
            if (chemical.isPresent()) return chemical;
        }
        return super.getCapability(capability, direction);
    }

    <T> LazyOptional<T> capability(Capability<T> key, Supplier<?> factory) {
        return capabilities.computeIfAbsent(key, ignored -> LazyOptional.of(factory::get)).cast();
    }

    @Override
    void invalidateModuleCapabilities() {
        var old = java.util.List.copyOf(capabilities.values());
        capabilities.clear();
        old.forEach(LazyOptional::invalidate);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        invalidateModuleCapabilities();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        closeModules();
    }
}
