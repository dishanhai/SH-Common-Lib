package com.dishanhai.sh_common_lib.neoforge;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.dishanhai.sh_common_lib.internal.ae2.InfinityCellInventory;
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
        return key == null ? null : new InfinityCellInventory(stack.getHoverName(), key);
    }

    private static AEKey key(ItemStack stack) {
        var target = InfinityCell.target(stack);
        if (target == null) return null;
        return switch (target.kind()) {
            case ITEM -> AEItemKey.of(target.itemStack());
            case FLUID -> {
                var fluid = target.fluidStack();
                yield fluid.isEmpty() ? null : AEFluidKey.of(fluid);
            }
            case CHEMICAL -> InfinityCell.supportsChemicals() ? AppliedMekanisticsCells.key(target.id()) : null;
        };
    }
}
