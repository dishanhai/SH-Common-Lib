// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.ulk;

import com.dishanhai.sh_common_lib.api.ulk.*;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;

final class UlkBackpacks {
    static void inspect(InventoryScan scan) {
        for (int index = 0; index < scan.candidateCount(); index++) {
            var candidate = scan.candidateAt(index);
            var stack = candidate.stack().copy();
            if (!(stack.getItem() instanceof BackpackItem)) continue;
            scan.status(InventorySource.BACKPACK, InventorySnapshot.Status.AVAILABLE);
            var wrapper = BackpackWrapper.fromStack(stack);
            var linked = wrapper.getLinkedStorageEndpoint();
            Object identity = linked.isPresent() ? "linked:" + linked.get().groupId()
                    : wrapper.getContentsUuid().map(id -> "uuid:" + id).orElse(null);
            if (identity == null || !scan.enter(InventorySource.BACKPACK, identity)) continue;
            var inventory = wrapper.getInventoryHandler();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                scan.candidate(InventorySource.BACKPACK, candidate.location() + "/backpack/" + slot,
                        slot, inventory.getStackInSlot(slot));
            }
        }
    }
}
