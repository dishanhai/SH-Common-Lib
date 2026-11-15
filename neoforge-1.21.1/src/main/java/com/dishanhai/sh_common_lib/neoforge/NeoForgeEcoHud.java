// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import appeng.api.networking.IGrid;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocators;
import com.dishanhai.sh_common_lib.internal.performance.EcoNetworkDiagnostics;
import net.minecraft.server.level.ServerPlayer;

/** Samples only the already connected wireless network, with a hard traversal bound. */
final class NeoForgeEcoHud {
    private NeoForgeEcoHud() {}

    static EcoNetworkDiagnostics.Network sample(ServerPlayer player) {
        return EcoNetworkDiagnostics.network(connectedGrid(player));
    }

    static IGrid connectedGrid(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) continue;
            var host = terminal.getMenuHost(player, MenuLocators.forInventorySlot(slot), null);
            if (host == null || !host.getLinkStatus().connected()) continue;
            var node = host.getActionableNode();
            if (node != null && node.getGrid() != null) return node.getGrid();
        }
        return null;
    }
}
