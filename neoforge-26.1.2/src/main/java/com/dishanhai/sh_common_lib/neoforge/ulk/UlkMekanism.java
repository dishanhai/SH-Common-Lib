// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.ulk;

import com.dishanhai.sh_common_lib.api.ulk.*;
import mekanism.api.security.IItemSecurityUtils;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.QIOGlobalItemLookup;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.api.security.SecurityMode;
import mekanism.common.item.ItemPortableQIODashboard;
import mekanism.common.lib.security.SecurityUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.transfer.access.ItemAccess;

/** Mekanism: Version Locked 2.11 community port for 26.1.2. */
final class UlkMekanism {
    static void inspect(ServerPlayer player, InventoryScan scan) {
        for (int index = 0; index < scan.candidateCount(); index++) {
            var candidate = scan.candidateAt(index);
            var stack = candidate.stack();
            if (stack.getItem() instanceof IQIODriveItem) {
                scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.AVAILABLE);
                if (!scan.enter(InventorySource.MEKANISM, "drive:" + candidate.location())) continue;
                var contents = stack.get(MekanismDataComponents.DRIVE_CONTENTS);
                var metadata = stack.get(MekanismDataComponents.DRIVE_METADATA);
                if (contents == null) {
                    if (metadata != null && metadata.count() > 0)
                        scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.INCOMPLETE);
                    continue;
                }
                if (contents.namedItemMap().isEmpty() && metadata != null && metadata.count() > 0)
                    scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.INCOMPLETE);
                for (var stored : contents.namedItemMap().object2LongEntrySet()) {
                    var item = QIOGlobalItemLookup.instance().getTypeByUUID(stored.getKey());
                    if (item == null || item.isEmpty() || stored.getLongValue() <= 0)
                        scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.INCOMPLETE);
                    else scan.add(InventorySource.MEKANISM, candidate.location() + "/qio_drive", item.toStack(), stored.getLongValue());
                }
                continue;
            }
            if (!(stack.getItem() instanceof ItemPortableQIODashboard dashboard)) continue;
            if (!IItemSecurityUtils.INSTANCE.canAccess(player, ItemAccess.forStack(stack.copy()))) {
                scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.INCOMPLETE);
                continue;
            }
            var data = stack.get(dashboard.getFrequencyComponent());
            var qio = data == null ? null : data.frequency().orElse(null);
            if (qio == null || !qio.isValid() || (qio.getSecurity() != SecurityMode.PUBLIC
                    && !qio.ownerMatches(player.getUUID())
                    && !SecurityUtils.get().isTrusted(qio.getSecurity(), qio.getOwner(), player.getUUID()))) {
                scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.INCOMPLETE);
                continue;
            }
            scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.AVAILABLE);
            if (scan.enter(InventorySource.MEKANISM, qio)) {
                qio.forAllStoredTypes((item, count) -> scan.add(InventorySource.MEKANISM,
                        candidate.location() + "/qio", item.toStack(), count));
            }
        }
    }
}
