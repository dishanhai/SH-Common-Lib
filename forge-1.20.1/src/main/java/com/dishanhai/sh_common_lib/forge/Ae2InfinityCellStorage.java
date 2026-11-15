// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.dishanhai.sh_common_lib.internal.ae2.InfinityCellInventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** AE2 integration. Bound cells have no editable workbench inventory. */
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
        var key = key(stack);
        return key == null ? null : new InfinityCellInventory(stack.getHoverName(), key);
    }

    private static AEKey key(ItemStack stack) {
        var target = InfinityCell.target(stack);
        if (target == null) return null;
        if (target.kind() == InfinityCell.Kind.CHEMICAL) {
            return InfinityCell.supportsChemicals() ? AppliedMekanisticsCells.key(target) : null;
        }
        if (target.kind() == InfinityCell.Kind.FLUID) {
            var fluid = target.fluidStack();
            return fluid.isEmpty() ? null : AEFluidKey.of(fluid);
        }
        var item = target.itemStack();
        return item.isEmpty() ? null : AEItemKey.of(item);
    }
}
