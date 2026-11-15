// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.network.chat.Component;

public record InfinityCellInventory(Component description, AEKey key) implements StorageCell {
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
