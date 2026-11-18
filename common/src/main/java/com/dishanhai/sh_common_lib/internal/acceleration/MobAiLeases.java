// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.acceleration;

import java.util.HashMap;
import java.util.Map;

/** Tracks shared AI-disable requests and restores the pre-lease state. */
final class MobAiLeases<K, M> {
    interface Adapter<M> {
        boolean marked(M mob);
        boolean original(M mob);
        void mark(M mob, boolean original);
        void clear(M mob);
        boolean disabled(M mob);
        void disable(M mob, boolean disabled);
    }

    private final Adapter<M> adapter;
    private final Map<K, Lease<M>> leases = new HashMap<>();

    MobAiLeases(Adapter<M> adapter) { this.adapter = adapter; }

    void reconcile(Map<K, M> desired) {
        var existing = leases.entrySet().iterator();
        while (existing.hasNext()) {
            var entry = existing.next();
            if (desired.get(entry.getKey()) != entry.getValue().mob) {
                release(entry.getValue());
                existing.remove();
            }
        }
        desired.forEach((key, mob) -> {
            var lease = leases.get(key);
            if (lease == null) {
                recover(key, mob);
                boolean original = adapter.disabled(mob);
                adapter.mark(mob, original);
                leases.put(key, new Lease<>(mob, original));
            }
            if (!adapter.disabled(mob)) adapter.disable(mob, true);
        });
    }

    /** Restore an interrupted lease before the reloaded entity gets its first tick. */
    void recover(K key, M mob) {
        var existing = leases.get(key);
        if (existing != null && existing.mob == mob) return;
        if (existing != null) {
            leases.remove(key);
            release(existing);
        }
        if (adapter.marked(mob)) {
            adapter.disable(mob, adapter.original(mob));
            adapter.clear(mob);
        }
    }

    void close() {
        leases.values().forEach(this::release);
        leases.clear();
    }

    private void release(Lease<M> lease) {
        adapter.disable(lease.mob, lease.original);
        adapter.clear(lease.mob);
    }

    private record Lease<M>(M mob, boolean original) {}
}
