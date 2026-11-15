// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import appeng.api.networking.IGrid;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.dishanhai.sh_common_lib.internal.performance.EcoMachineSnapshot;
import com.dishanhai.sh_common_lib.internal.performance.EcoNetworkDiagnostics;
import net.minecraft.server.level.ServerPlayer;

final class ForgeEcoHud {
    private ForgeEcoHud() {}

    static EcoNetworkDiagnostics.Network sample(ServerPlayer player) {
        return EcoNetworkDiagnostics.network(connectedGrid(player));
    }

    static EcoMachineSnapshot machines(ServerPlayer player, String dimension, int mask) {
        return EcoNetworkDiagnostics.machines(connectedGrid(player), dimension, mask,
                block -> block.getLevel().dimension().location().toString());
    }

    private static IGrid connectedGrid(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof WirelessTerminalItem terminal)
                    || !terminal.hasPower(player, 1, stack)
                    || terminal.getLinkedGrid(stack, player.level(), null) == null) continue;
            if (!(terminal.getMenuHost(player, slot, stack, null) instanceof WirelessTerminalMenuHost host)) continue;
            var node = host.getActionableNode();
            if (node != null && node.getGrid() != null) return node.getGrid();
        }
        return null;
    }
}
