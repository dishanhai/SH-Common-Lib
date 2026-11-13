// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.ulk;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Bounds a single scan and identifies shared or cyclic storage only once. */
public final class InventoryScanTracker {
    private final int limit;
    private final Set<Key> visited = new HashSet<>();
    private boolean truncated;

    public InventoryScanTracker(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        this.limit = limit;
    }

    public boolean enter(String kind, Object identity) {
        var key = new Key(Objects.requireNonNull(kind), Objects.requireNonNull(identity));
        if (visited.contains(key)) return false;
        if (visited.size() >= limit) {
            truncated = true;
            return false;
        }
        visited.add(key);
        return true;
    }

    public boolean truncated() { return truncated; }

    public static long add(long left, long right) {
        if (left < 0 || right < 0) throw new IllegalArgumentException("negative item count");
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private record Key(String kind, Object identity) {}
}
