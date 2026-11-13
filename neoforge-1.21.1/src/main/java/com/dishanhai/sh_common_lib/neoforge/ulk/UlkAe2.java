// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.ulk;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocators;
import com.dishanhai.sh_common_lib.api.ulk.*;
import net.minecraft.server.level.ServerPlayer;

final class UlkAe2 {
    static void inspect(ServerPlayer player, InventoryScan scan) {
        for (int index = 0; index < scan.candidateCount(); index++) {
            var candidate = scan.candidateAt(index);
            var stack = candidate.stack();
            if (stack.getItem() instanceof WirelessTerminalItem terminal) {
                if (terminal.getLinkedGrid(stack, player.level(), null) == null || !terminal.hasPower(player, 1, stack)) {
                    scan.status(InventorySource.AE2, InventorySnapshot.Status.INCOMPLETE);
                    continue;
                }
                var host = terminal.getMenuHost(player, MenuLocators.forStack(stack), null);
                if (host == null || !host.getLinkStatus().connected() || host.getActionableNode() == null
                        || host.getActionableNode().getGrid() == null) {
                    scan.status(InventorySource.AE2, InventorySnapshot.Status.INCOMPLETE);
                    continue;
                }
                var grid = host.getActionableNode().getGrid();
                scan.status(InventorySource.AE2, InventorySnapshot.Status.AVAILABLE);
                if (scan.enter(InventorySource.AE2, grid)) add(scan, candidate.location() + "/ae2", grid.getStorageService().getInventory());
            } else if (StorageCells.isCellHandled(stack)) {
                var cell = StorageCells.getCellInventory(stack.copy(), null);
                if (cell != null) {
                    scan.status(InventorySource.AE2, InventorySnapshot.Status.AVAILABLE);
                    if (scan.enter(InventorySource.AE2, "cell:" + candidate.location())) add(scan, candidate.location() + "/cell", cell);
                }
            }
        }
    }

    private static void add(InventoryScan scan, String location, MEStorage storage) {
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey item && entry.getLongValue() > 0)
                scan.add(InventorySource.AE2, location, item.toStack(), entry.getLongValue());
        }
    }
}
