// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

/** Unified LIB Kernel: authoritative, read-only player inventory inspection. */
public final class UnifiedLibKernel {
    private static volatile Platform platform;

    private UnifiedLibKernel() {}

    /** Called once by SHCLIB's loader during initialization. */
    public static void install(Platform implementation) { platform = Objects.requireNonNull(implementation); }

    /** Must be called on the player's server thread. Optional providers add explicitly selected storage. */
    public static InventorySnapshot inspect(ServerPlayer player, InventoryProvider... providers) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(providers, "providers");
        var implementation = platform;
        if (implementation == null) throw new IllegalStateException("ULK platform is not initialized");
        if (!implementation.isServerThread(player)) {
            throw new IllegalStateException("ULK inventory inspection must run on the player's server thread");
        }
        var scan = new InventoryScan();
        scan.status(InventorySource.INVENTORY, InventorySnapshot.Status.AVAILABLE);
        scan.status(InventorySource.MAIN_HAND, InventorySnapshot.Status.AVAILABLE);
        scan.status(InventorySource.OFF_HAND, InventorySnapshot.Status.AVAILABLE);
        var inventory = player.getInventory();
        int selected = implementation.selectedSlot(player);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var source = slot == selected ? InventorySource.MAIN_HAND
                    : slot == Inventory.SLOT_OFFHAND ? InventorySource.OFF_HAND : InventorySource.INVENTORY;
            scan.candidate(source, "player/" + slot, slot, inventory.getItem(slot));
        }
        implementation.inspect(player, scan);
        for (var provider : providers) Objects.requireNonNull(provider, "provider").inspect(player, scan);
        return scan.snapshot();
    }

    public interface Platform {
        int selectedSlot(ServerPlayer player);
        boolean isServerThread(ServerPlayer player);
        void inspect(ServerPlayer player, InventoryScan scan);
    }
}
