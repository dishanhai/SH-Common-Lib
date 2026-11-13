// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk;

import com.dishanhai.sh_common_lib.internal.ulk.InventoryScanTracker;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** Per-query collector for integrations; valid only during the call to inspect. */
public final class InventoryScan {
    private static final int MAX_ENTRIES = 16384;
    private final InventoryScanTracker visits = new InventoryScanTracker(256);
    private final List<InventoryEntry> entries = new ArrayList<>();
    private final List<Candidate> candidates = new ArrayList<>();
    private final EnumMap<InventorySource, InventorySnapshot.Status> statuses = new EnumMap<>(InventorySource.class);

    public InventoryScan() {
        for (var source : InventorySource.values()) statuses.put(source, InventorySnapshot.Status.UNAVAILABLE);
    }

    public void status(InventorySource source, InventorySnapshot.Status status) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        if (statuses.get(source) != InventorySnapshot.Status.INCOMPLETE) statuses.put(source, status);
    }

    /** Avoid visiting the same physical container or network twice in one query. */
    public boolean enter(InventorySource source, Object identity) {
        boolean first = visits.enter(source.name(), identity);
        if (visits.truncated()) status(source, InventorySnapshot.Status.INCOMPLETE);
        return first;
    }

    /** Adds an item to the snapshot without retaining the caller's mutable stack. */
    public void add(InventorySource source, String location, ItemStack stack, long amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) return;
        if (entries.size() >= MAX_ENTRIES) {
            status(source, InventorySnapshot.Status.INCOMPLETE);
            return;
        }
        entries.add(new InventoryEntry(source, location, stack, amount));
    }

    /** Adds a physical stack which can itself contain other storage. */
    public void candidate(InventorySource source, String location, int slot, ItemStack stack) {
        candidate(source, location, slot, stack, stack == null ? 0 : stack.getCount());
    }

    public void candidate(InventorySource source, String location, int slot, ItemStack stack, long amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) return;
        if (candidates.size() >= MAX_ENTRIES) {
            status(source, InventorySnapshot.Status.INCOMPLETE);
            return;
        }
        add(source, location, stack, amount);
        candidates.add(new Candidate(source, location, slot, stack));
    }

    public int candidateCount() { return candidates.size(); }
    public Candidate candidateAt(int index) { return candidates.get(index); }
    public InventorySnapshot snapshot() {
        var result = new EnumMap<>(statuses);
        boolean rootsIncomplete = incomplete(result, InventorySource.INVENTORY)
                || incomplete(result, InventorySource.MAIN_HAND) || incomplete(result, InventorySource.OFF_HAND)
                || incomplete(result, InventorySource.CURIOS);
        if (rootsIncomplete) markIncompleteIfPresent(result, InventorySource.BACKPACK);
        if (rootsIncomplete || incomplete(result, InventorySource.BACKPACK)) {
            markIncompleteIfPresent(result, InventorySource.AE2);
            markIncompleteIfPresent(result, InventorySource.MEKANISM);
        }
        return new InventorySnapshot(entries, result);
    }

    private static boolean incomplete(EnumMap<InventorySource, InventorySnapshot.Status> statuses, InventorySource source) {
        return statuses.get(source) == InventorySnapshot.Status.INCOMPLETE;
    }

    private static void markIncompleteIfPresent(EnumMap<InventorySource, InventorySnapshot.Status> statuses, InventorySource source) {
        if (statuses.get(source) != InventorySnapshot.Status.UNAVAILABLE) statuses.put(source, InventorySnapshot.Status.INCOMPLETE);
    }

    /** Stack is a live server object; integrations may read it but must not modify it. */
    public record Candidate(InventorySource source, String location, int slot, ItemStack stack) {}
}
