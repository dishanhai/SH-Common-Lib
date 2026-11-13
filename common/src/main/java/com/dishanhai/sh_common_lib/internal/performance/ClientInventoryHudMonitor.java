// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.performance;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import net.minecraft.network.Connection;

public final class ClientInventoryHudMonitor {
    public static final ClientInventoryHudMonitor INSTANCE = new ClientInventoryHudMonitor();
    private Connection connection;
    private String dimension;
    private int requested;
    private long lastRequest, receivedAt;
    private InventoryHudSnapshot snapshot;
    private ClientInventoryHudMonitor() {}

    public void tick(Connection active, String dimension, int flags, BiConsumer<Connection, Integer> request) {
        long now = System.nanoTime();
        boolean changed = connection != active || !Objects.equals(this.dimension, dimension) || requested != flags;
        if (changed) { connection = active; this.dimension = dimension; requested = flags; snapshot = null; }
        if (active != null && flags != 0 && (changed || now - lastRequest >= TimeUnit.SECONDS.toNanos(snapshot == null ? 5 : 30))) {
            lastRequest = now;
            request.accept(active, flags);
        }
    }
    public void accept(Connection source, InventoryHudSnapshot value) {
        if (source != null && source == connection && value.dimension().equals(dimension) && value.flags() == requested) {
            snapshot = value;
            receivedAt = System.nanoTime();
        }
    }
    public InventoryHudSnapshot current() {
        return snapshot != null && System.nanoTime() - receivedAt < TimeUnit.SECONDS.toNanos(70) ? snapshot : null;
    }
}
