// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocators;
import com.dishanhai.sh_common_lib.internal.performance.Ae2Snapshot;
import net.minecraft.server.level.ServerPlayer;

/** Loaded only when AE2 is installed. Does not open a menu or consume terminal power. */
final class NeoForgeAe2Hud {
    private NeoForgeAe2Hud() {}

    static Ae2Snapshot sample(ServerPlayer player, String dimension) {
        boolean found = false, failed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) continue;
            found = true;
            try {
                var host = terminal.getMenuHost(player, MenuLocators.forInventorySlot(slot), null);
                if (host == null || !host.getLinkStatus().connected()) continue;
                var node = host.getActionableNode();
                if (node == null || node.getGrid() == null) continue;
                var grid = node.getGrid();
                var path = grid.getPathingService();
                var energy = grid.getEnergyService();
                return new Ae2Snapshot(dimension, Ae2Snapshot.CONNECTED, Math.max(0, grid.size()),
                        Math.max(0, path.getUsedChannels()), Math.max(0, node.getUsedChannels()), Math.max(0, node.getMaxChannels()),
                        path.getChannelMode().name(), path.getControllerState().name(), energy.isNetworkPowered(),
                        path.isNetworkBooting(), nonnegative(energy.getStoredPower()), nonnegative(energy.getMaxStoredPower()),
                        nonnegative(energy.getAvgPowerUsage()), nonnegative(energy.getAvgPowerInjection()));
            } catch (RuntimeException | LinkageError ignored) {
                failed = true;
            }
        }
        return Ae2Snapshot.empty(dimension, failed ? Ae2Snapshot.ERROR : found ? Ae2Snapshot.DISCONNECTED : Ae2Snapshot.NO_TERMINAL);
    }

    private static double nonnegative(double value) { return Double.isFinite(value) && value >= 0 ? value : 0; }
}
