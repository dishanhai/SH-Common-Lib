package com.dishanhai.sh_common_lib.neoforge;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** AE2 integration. The cell has no workbench configuration inventory. */
final class Ae2InfinityCellStorage implements ICellHandler {
    static void register(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> StorageCells.addCellHandler(new Ae2InfinityCellStorage())));
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return key(stack) != null;
    }

    @Override
    public StorageCell getCellInventory(ItemStack stack, ISaveProvider host) {
        AEKey key = key(stack);
        return key == null ? null : new Inventory(stack.getHoverName(), key);
    }

    private static AEKey key(ItemStack stack) {
        var target = InfinityCell.target(stack);
        if (target == null) return null;
        if (!target.fluid()) return AEItemKey.of(target.itemStack());
        var fluid = target.fluidStack();
        return fluid.isEmpty() ? null : AEFluidKey.of(fluid);
    }

    private record Inventory(Component description, AEKey key) implements StorageCell {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return key.equals(what) ? amount : 0;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return key.equals(what) ? amount : 0;
        }

        @Override
        public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
            return key.equals(what);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.add(key, (long) Integer.MAX_VALUE * key.getAmountPerUnit());
        }

        @Override
        public Component getDescription() {
            return description;
        }

        @Override
        public CellState getStatus() {
            return CellState.NOT_EMPTY;
        }

        @Override
        public double getIdleDrain() {
            return 8;
        }

        @Override
        public boolean canFitInsideCell() {
            return false;
        }

        @Override
        public void persist() {}
    }
}
