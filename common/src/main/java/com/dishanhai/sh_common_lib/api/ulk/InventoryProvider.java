// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk;

import net.minecraft.server.level.ServerPlayer;

/** Supplies an explicitly selected inventory on the server thread. Never mutate scanned stacks. */
@FunctionalInterface
public interface InventoryProvider {
    void inspect(ServerPlayer player, InventoryScan output);
}
