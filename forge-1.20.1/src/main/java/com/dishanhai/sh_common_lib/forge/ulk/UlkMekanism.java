// Author: dishanhai
package com.dishanhai.sh_common_lib.forge.ulk;

import com.dishanhai.sh_common_lib.api.ulk.*;
import mekanism.api.NBTConstants;
import mekanism.api.security.ISecurityUtils;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.QIOGlobalItemLookup;
import mekanism.common.util.ItemDataUtils;
import com.dishanhai.sh_common_lib.internal.ulk.QioDriveRecords;
import mekanism.api.security.SecurityMode;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.item.ItemPortableQIODashboard;
import net.minecraft.server.level.ServerPlayer;

final class UlkMekanism {
    static void inspect(ServerPlayer player, InventoryScan scan) {
        for (int index = 0; index < scan.candidateCount(); index++) {
            var candidate = scan.candidateAt(index);
            var stack = candidate.stack();
            if (stack.getItem() instanceof IQIODriveItem drive) {
                scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.AVAILABLE);
                if (!scan.enter(InventorySource.MEKANISM, "drive:" + candidate.location())) continue;
                if (!drive.hasStoredItemMap(stack)) {
                    if (IQIODriveItem.DriveMetadata.load(stack).count() > 0)
                        scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.INCOMPLETE);
                    continue;
                }
                boolean valid = QioDriveRecords.read(ItemDataUtils.getLongArray(stack, NBTConstants.QIO_ITEM_MAP), (id, count) -> {
                    var item = QIOGlobalItemLookup.INSTANCE.getTypeByUUID(id);
                    if (item == null) scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.INCOMPLETE);
                    else scan.add(InventorySource.MEKANISM, candidate.location() + "/qio_drive", item.getInternalStack(), count);
                });
                if (!valid) scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.INCOMPLETE);
                continue;
            }
            if (!(stack.getItem() instanceof ItemPortableQIODashboard dashboard)) continue;
            if (!ISecurityUtils.INSTANCE.canAccess(player, stack)) {
                scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.INCOMPLETE);
                continue;
            }
            var frequency = dashboard.getFrequency(stack);
            if (!(frequency instanceof QIOFrequency qio) || !qio.isValid()
                    || (qio.getSecurity() != SecurityMode.PUBLIC && !qio.ownerMatches(player.getUUID()))) {
                scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.INCOMPLETE);
                continue;
            }
            scan.status(InventorySource.MEKANISM, InventorySnapshot.Status.AVAILABLE);
            if (scan.enter(InventorySource.MEKANISM, qio)) {
                qio.forAllStored((item, count) -> scan.add(InventorySource.MEKANISM, candidate.location() + "/qio", item, count));
            }
        }
    }
}
