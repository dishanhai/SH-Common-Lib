// Author: dishanhai
package com.dishanhai.sh_common_lib.forge.ulk;

import com.dishanhai.sh_common_lib.api.ulk.*;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;

final class UlkBackpacks {
    static void inspect(InventoryScan scan) {
        for (int index = 0; index < scan.candidateCount(); index++) {
            var candidate = scan.candidateAt(index);
            var stack = candidate.stack().copy();
            if (!(stack.getItem() instanceof BackpackItem)) continue;
            scan.status(InventorySource.BACKPACK, InventorySnapshot.Status.AVAILABLE);
            var wrapper = stack.getCapability(CapabilityBackpackWrapper.getCapabilityInstance()).orElse(null);
            if (wrapper == null) { scan.status(InventorySource.BACKPACK, InventorySnapshot.Status.INCOMPLETE); continue; }
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
