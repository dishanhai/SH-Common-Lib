// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocators;
import com.dishanhai.sh_common_lib.internal.performance.InventoryHudSnapshot;
import net.minecraft.server.level.ServerPlayer;

/** Samples only the already connected wireless network, with a hard traversal bound. */
final class NeoForgeEcoHud {
    record Result(int state, int nodes, int active, boolean truncated) {}
    private NeoForgeEcoHud() {}

    static Result sample(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) continue;
            var host = terminal.getMenuHost(player, MenuLocators.forInventorySlot(slot), null);
            if (host == null || !host.getLinkStatus().connected() || host.getActionableNode() == null
                    || host.getActionableNode().getGrid() == null) continue;
            int inspected = 0, nodes = 0, active = 0;
            for (var node : host.getActionableNode().getGrid().getNodes()) {
                if (inspected++ >= 1024) return new Result(InventoryHudSnapshot.ECO_CONNECTED, nodes, active, true);
                Object owner = node.getOwner();
                if (owner != null && owner.getClass().getName().startsWith("cn.dancingsnow.neoecoae.")) {
                    nodes++;
                    if (node.isActive()) active++;
                }
            }
            return new Result(InventoryHudSnapshot.ECO_CONNECTED, nodes, active, false);
        }
        return new Result(InventoryHudSnapshot.ECO_NO_TERMINAL, 0, 0, false);
    }
}
