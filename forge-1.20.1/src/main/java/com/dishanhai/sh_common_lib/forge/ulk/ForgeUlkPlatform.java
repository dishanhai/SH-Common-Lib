// Author: dishanhai
package com.dishanhai.sh_common_lib.forge.ulk;

import com.dishanhai.sh_common_lib.api.ulk.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

public final class ForgeUlkPlatform implements UnifiedLibKernel.Platform {
    @Override public int selectedSlot(ServerPlayer player) { return player.getInventory().selected; }
    @Override public boolean isServerThread(ServerPlayer player) { return player.getServer().isSameThread(); }

    @Override public void inspect(ServerPlayer player, InventoryScan scan) {
        use("curios", InventorySource.CURIOS, scan, () -> UlkCurios.inspect(player, scan));
        use("sophisticatedbackpacks", InventorySource.BACKPACK, scan, () -> UlkBackpacks.inspect(scan));
        use("ae2", InventorySource.AE2, scan, () -> UlkAe2.inspect(player, scan));
        use("mekanism", InventorySource.MEKANISM, scan, () -> UlkMekanism.inspect(player, scan));
    }

    private static void use(String mod, InventorySource source, InventoryScan scan, Runnable run) {
        if (!ModList.get().isLoaded(mod)) return;
        scan.status(source, InventorySnapshot.Status.NOT_FOUND);
        try { run.run(); }
        catch (RuntimeException | LinkageError error) { scan.status(source, InventorySnapshot.Status.INCOMPLETE); }
    }
}
