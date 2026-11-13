// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.compat;

import com.dishanhai.sh_common_lib.api.ulk.*;
import java.util.Objects;
import mekanism.api.inventory.qio.IQIOFrequency;

/** Explicit QIO frequency supplied by a caller which has already checked access. */
public final class MekanismInventories {
    private MekanismInventories() {}

    public static InventoryProvider frequency(IQIOFrequency frequency) {
        Objects.requireNonNull(frequency, "frequency");
        return (player, scan) -> {
            scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.AVAILABLE);
            if (scan.enter(InventorySource.MEKANISM, frequency)) {
                frequency.forAllStored((item, count) -> scan.add(InventorySource.MEKANISM, "explicit/qio", item, count));
            }
        };
    }
}
