// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk;

import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** A copied item identity and its count. The returned stack has a count of one. */
public record InventoryEntry(InventorySource source, String location, ItemStack item, long amount) {
    public InventoryEntry {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(item, "item");
        if (item.isEmpty() || amount <= 0) throw new IllegalArgumentException("empty item entry");
        item = item.copy();
        item.setCount(1);
    }

    @Override public ItemStack item() { return item.copy(); }
}
