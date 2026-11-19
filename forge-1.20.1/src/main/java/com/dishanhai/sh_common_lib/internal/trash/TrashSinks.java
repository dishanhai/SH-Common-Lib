// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

final class TrashSinks {
    static final IItemHandler ITEMS = new IItemHandler() {
        public int getSlots() { return 1; }
        public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return slot == 0 ? ItemStack.EMPTY : stack; }
        public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        public int getSlotLimit(int slot) { return slot == 0 ? Integer.MAX_VALUE : 0; }
        public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && !stack.isEmpty(); }
    };

    static final IFluidHandler FLUIDS = new IFluidHandler() {
        public int getTanks() { return 1; }
        public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        public int getTankCapacity(int tank) { return tank == 0 ? Integer.MAX_VALUE : 0; }
        public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && !stack.isEmpty(); }
        public int fill(FluidStack stack, FluidAction action) { return stack.isEmpty() ? 0 : stack.getAmount(); }
        public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    };

    static final IEnergyStorage ENERGY = new EnergyStorage(Integer.MAX_VALUE, Integer.MAX_VALUE, 0) {
        @Override
        public int receiveEnergy(int amount, boolean simulate) { return Math.max(0, amount); }
    };

    private TrashSinks() {}
}
