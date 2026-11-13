// Author: dishanhai
package com.dishanhai.sh_common_lib.forge.ulk;

import com.dishanhai.sh_common_lib.api.ulk.*;
import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.CuriosApi;

final class UlkCurios {
    static void inspect(ServerPlayer player, InventoryScan scan) {
        scan.status(InventorySource.CURIOS, InventorySnapshot.Status.AVAILABLE);
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var slots = curios.getEquippedCurios();
            for (int slot = 0; slot < slots.getSlots(); slot++) {
                scan.candidate(InventorySource.CURIOS, "curios/" + slot, slot, slots.getStackInSlot(slot));
            }
        });
    }
}
