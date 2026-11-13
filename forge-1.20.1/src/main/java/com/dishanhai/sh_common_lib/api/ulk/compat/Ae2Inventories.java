// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.compat;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.dishanhai.sh_common_lib.api.ulk.*;
import java.util.Objects;

/** Explicit AE2 storage supplied by a caller which has already checked access. */
public final class Ae2Inventories {
    private Ae2Inventories() {}

    public static InventoryProvider grid(IGrid grid) {
        Objects.requireNonNull(grid, "grid");
        return storage(grid, grid.getStorageService().getInventory());
    }

    public static InventoryProvider storage(Object identity, MEStorage storage) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(storage, "storage");
        return (player, scan) -> {
            scan.status(InventorySource.AE2, InventorySnapshot.Status.AVAILABLE);
            if (!scan.enter(InventorySource.AE2, identity)) return;
            for (var entry : storage.getAvailableStacks()) {
                if (entry.getKey() instanceof AEItemKey item && entry.getLongValue() > 0)
                    scan.add(InventorySource.AE2, "explicit/ae2", item.toStack(), entry.getLongValue());
            }
        };
    }
}
