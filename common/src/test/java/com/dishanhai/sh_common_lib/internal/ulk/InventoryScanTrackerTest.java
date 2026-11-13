// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.ulk;

public final class InventoryScanTrackerTest {
    public static void main(String[] args) {
        var tracker = new InventoryScanTracker(2);
        check(tracker.enter("backpack", "shared"), "first backpack must be visited");
        check(!tracker.enter("backpack", "shared"), "linked or cyclic backpack must not be visited twice");
        check(tracker.enter("ae2", "shared"), "identities in different storage systems are independent");
        check(!tracker.enter("backpack", "third"), "scan must stop at its limit");
        check(tracker.truncated(), "a limit must be reported to callers");
        check(InventoryScanTracker.add(12, 30) == 42, "counts should add exactly");
        check(InventoryScanTracker.add(Long.MAX_VALUE - 1, 2) == Long.MAX_VALUE, "counts must not overflow");
        var found = new java.util.HashMap<java.util.UUID, Long>();
        check(QioDriveRecords.read(new long[] {1, 2, 64, 3, 4, 128}, found::put), "valid QIO records must decode");
        check(found.get(new java.util.UUID(1, 2)) == 64, "QIO records must preserve item identity and amount");
        check(!QioDriveRecords.read(new long[] {1, 2}, found::put), "truncated QIO records must be incomplete");
        check(!QioDriveRecords.read(new long[] {1, 2, -1}, found::put), "invalid QIO quantities must be incomplete");
        try {
            InventoryScanTracker.add(1, -1);
            throw new AssertionError("negative quantities must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
