// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

abstract class TrashCanBlockEntity extends BlockEntity {
    private final Map<Extension, Function<TrashCanBlockEntity, TrashCanRuntime>> factories;
    private final Map<Extension, TrashCanRuntime> modules = new EnumMap<>(Extension.class);
    private boolean restoring;
    private int stressLimit;
    final SimpleContainer storage = new SimpleContainer(Extension.values().length) {
        @Override
        public void setChanged() {
            super.setChanged();
            TrashCanBlockEntity.this.modulesChanged();
        }
    };

    TrashCanBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                        Map<Extension, Function<TrashCanBlockEntity, TrashCanRuntime>> factories) {
        super(type, pos, state);
        this.factories = factories;
    }

    final TrashCanRuntime module(Extension extension) {
        if (level == null || level.isClientSide() || !hasModule(extension)) {
            return null;
        }
        var factory = factories.get(extension);
        return factory == null ? null : modules.computeIfAbsent(extension, ignored -> factory.apply(this));
    }

    final void tick() {
        for (var extension : factories.keySet()) {
            var module = module(extension);
            if (module != null) module.tick();
        }
    }

    final int stressLimit() {
        return stressLimit;
    }

    final NonNullList<ItemStack> savedModules() {
        var contents = NonNullList.withSize(Extension.values().length, ItemStack.EMPTY);
        for (int index = 0; index < contents.size(); index++) contents.set(index, storage.getItem(index));
        return contents;
    }

    final void restoreModules(NonNullList<ItemStack> contents) {
        restoring = true;
        try {
            for (var extension : Extension.values()) {
                var stack = contents.get(extension.ordinal());
                storage.setItem(extension.ordinal(), TrashCanModuleItem.matches(stack, extension)
                        ? stack.copyWithCount(1) : ItemStack.EMPTY);
            }
        } finally {
            restoring = false;
        }
        updateStressLimit();
    }

    final boolean hasModule(Extension extension) {
        return !isRemoved() && extension.isInstalled(getBlockState()) && !storage.getItem(extension.ordinal()).isEmpty();
    }

    private void updateStressLimit() {
        stressLimit = Math.max(0, TrashCanModuleItem.stressLimit(storage.getItem(Extension.STRESS.ordinal())));
    }

    static boolean use(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isSecondaryUseActive() || player.isSpectator()
                || !player.mayBuild() || !(level.getBlockEntity(pos) instanceof TrashCanBlockEntity entity)) return false;
        if (!level.isClientSide()) player.openMenu(TrashCanMenu.provider(entity));
        return true;
    }

    private void modulesChanged() {
        if (restoring || level == null || level.isClientSide() || isRemoved()) return;
        updateStressLimit();
        var previous = getBlockState();
        var updated = previous;
        for (var extension : Extension.values()) {
            boolean installed = TrashCanModuleItem.matches(storage.getItem(extension.ordinal()), extension);
            updated = updated.setValue(extension.property(), installed);
        }
        setChanged();
        if (previous == updated) return;
        level.setBlock(worldPosition, updated, Block.UPDATE_ALL);
        for (var extension : Extension.values()) {
            if (extension.isInstalled(updated)) continue;
            var module = modules.remove(extension);
            if (module != null) module.close();
        }
        invalidateModuleCapabilities();
    }

    abstract void invalidateModuleCapabilities();

    @Override
    public void setRemoved() {
        super.setRemoved();
        closeModules();
    }

    protected final void closeModules() {
        for (var module : modules.values()) module.close();
        modules.clear();
    }
}
