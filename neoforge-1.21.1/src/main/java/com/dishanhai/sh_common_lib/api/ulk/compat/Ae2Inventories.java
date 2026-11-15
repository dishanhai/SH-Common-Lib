// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.compat;

import appeng.api.networking.IGrid;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.dishanhai.sh_common_lib.api.ulk.*;
import java.util.Objects;

/** Explicit AE2 storage supplied by a caller which has already checked access. */
public final class Ae2Inventories {
    private Ae2Inventories() {}

    public static IGrid wirelessGrid(ServerLevel level, BlockPos accessPoint) {
        if (!level.hasChunkAt(accessPoint)
                || !(level.getBlockEntity(accessPoint) instanceof WirelessAccessPointBlockEntity wireless)
                || !wireless.getMainNode().isOnline()) return null;
        return wireless.getGrid();
    }

    /** Inserts from the player's action source and returns the number accepted by the grid. */
    public static int insert(IGrid grid, ServerPlayer player, ItemStack stack) {
        if (grid == null || stack.isEmpty()) return 0;
        var key = AEItemKey.of(stack);
        if (key == null) return 0;
        long accepted = grid.getStorageService().getInventory().insert(key, stack.getCount(),
                Actionable.MODULATE, IActionSource.ofPlayer(player));
        return (int) Math.max(0, Math.min(accepted, stack.getCount()));
    }

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
