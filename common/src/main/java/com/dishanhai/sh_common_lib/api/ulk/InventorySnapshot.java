// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk;

import com.dishanhai.sh_common_lib.internal.ulk.InventoryScanTracker;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/** Point-in-time, read-only inventory data. Incomplete sources must not be interpreted as empty. */
public record InventorySnapshot(List<InventoryEntry> entries, Map<InventorySource, Status> sources) {
    public enum Status { AVAILABLE, NOT_FOUND, UNAVAILABLE, INCOMPLETE }

    public InventorySnapshot {
        entries = List.copyOf(entries);
        sources = Map.copyOf(sources);
    }

    public long count(Predicate<ItemStack> matcher) {
        return count(Set.of(InventorySource.values()), matcher);
    }

    public long count(Set<InventorySource> selected, Predicate<ItemStack> matcher) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(matcher, "matcher");
        long total = 0;
        for (var entry : entries) {
            if (selected.contains(entry.source()) && matcher.test(entry.item())) {
                total = InventoryScanTracker.add(total, entry.amount());
            }
        }
        return total;
    }

    public boolean contains(Predicate<ItemStack> matcher, long amount) {
        if (amount < 0) throw new IllegalArgumentException("negative amount");
        return count(matcher) >= amount;
    }
}
