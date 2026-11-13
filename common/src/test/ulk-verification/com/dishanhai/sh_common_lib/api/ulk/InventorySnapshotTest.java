// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk;

import net.minecraft.world.item.ItemStack;

public final class InventorySnapshotTest {
    public static void main(String[] args) {
        var scan = new InventoryScan();
        var iron = new ItemStack("iron", 32);
        scan.status(InventorySource.INVENTORY, InventorySnapshot.Status.AVAILABLE);
        scan.candidate(InventorySource.INVENTORY, "player/0", 0, iron);
        scan.add(InventorySource.AE2, "grid", new ItemStack("iron", 1), 10);
        scan.add(InventorySource.AE2, "grid", new ItemStack("gold", 1), Long.MAX_VALUE);
        iron.setCount(0);
        var snapshot = scan.snapshot();
        check(snapshot.count(stack -> stack.id().equals("iron")) == 42, "counts from different sources must add");
        check(snapshot.count(java.util.Set.of(InventorySource.INVENTORY), stack -> stack.id().equals("iron")) == 32,
                "selected sources must exclude other storage");
        check(snapshot.count(java.util.Set.of(InventorySource.AE2), stack -> stack.id().equals("iron")) == 10,
                "selected AE2 source must not include player slots");
        check(snapshot.count(stack -> true) == Long.MAX_VALUE, "total must saturate rather than wrap");
        check(snapshot.contains(stack -> stack.id().equals("iron"), 42), "contains uses actual counts");
        var exposed = snapshot.entries().get(0).item();
        exposed.setCount(0);
        check(snapshot.entries().get(0).item().getCount() == 1, "snapshot item must be a defensive copy");
        check(scan.enter(InventorySource.BACKPACK, "shared"), "first shared storage must be visited");
        check(!scan.enter(InventorySource.BACKPACK, "shared"), "shared storage must be deduplicated");
        scan.status(InventorySource.BACKPACK, InventorySnapshot.Status.INCOMPLETE);
        scan.status(InventorySource.BACKPACK, InventorySnapshot.Status.AVAILABLE);
        check(scan.snapshot().sources().get(InventorySource.BACKPACK) == InventorySnapshot.Status.INCOMPLETE,
                "later success must not conceal an incomplete scan");
        var partial = new InventoryScan();
        partial.status(InventorySource.CURIOS, InventorySnapshot.Status.INCOMPLETE);
        partial.status(InventorySource.BACKPACK, InventorySnapshot.Status.NOT_FOUND);
        partial.status(InventorySource.AE2, InventorySnapshot.Status.NOT_FOUND);
        var partialSnapshot = partial.snapshot();
        check(partialSnapshot.sources().get(InventorySource.BACKPACK) == InventorySnapshot.Status.INCOMPLETE,
                "missing curio data can hide a backpack");
        check(partialSnapshot.sources().get(InventorySource.AE2) == InventorySnapshot.Status.INCOMPLETE,
                "missing curio data can hide a terminal");
        check(partialSnapshot.sources().get(InventorySource.MEKANISM) == InventorySnapshot.Status.UNAVAILABLE,
                "an uninstalled integration remains unavailable");
        var bounded = new InventoryScan();
        for (int i = 0; i < 16385; i++) bounded.add(InventorySource.CUSTOM, "test", new ItemStack("x", 1), 1);
        check(bounded.snapshot().entries().size() == 16384, "entry count must be bounded");
        check(bounded.snapshot().sources().get(InventorySource.CUSTOM) == InventorySnapshot.Status.INCOMPLETE,
                "entry limit must be visible to callers");
        try {
            snapshot.contains(stack -> true, -1);
            throw new AssertionError("negative required quantities must be rejected");
        } catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
