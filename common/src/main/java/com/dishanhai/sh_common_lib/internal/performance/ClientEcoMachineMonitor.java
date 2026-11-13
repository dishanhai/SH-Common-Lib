// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.performance;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import net.minecraft.network.Connection;

public final class ClientEcoMachineMonitor {
    public static final ClientEcoMachineMonitor INSTANCE = new ClientEcoMachineMonitor();
    private Connection connection;
    private String dimension;
    private int mask;
    private long requestedAt, receivedAt;
    private EcoMachineSnapshot snapshot;
    private ClientEcoMachineMonitor() {}
    public void tick(Connection connection, String dimension, int mask, BiConsumer<Connection, Integer> send) {
        long now = System.nanoTime();
        boolean changed = this.connection != connection || !Objects.equals(this.dimension, dimension) || this.mask != mask;
        if (changed) { this.connection = connection; this.dimension = dimension; this.mask = mask; snapshot = null; }
        if (connection != null && mask != 0 && (changed || now - requestedAt >= TimeUnit.SECONDS.toNanos(snapshot == null ? 5 : 30))) {
            requestedAt = now;
            send.accept(connection, mask);
        }
    }
    public void accept(Connection source, EcoMachineSnapshot value) {
        if (source != null && source == connection && value.mask() == mask && value.dimension().equals(dimension)) {
            snapshot = value;
            receivedAt = System.nanoTime();
        }
    }
    public EcoMachineSnapshot current() {
        return snapshot != null && System.nanoTime() - receivedAt < TimeUnit.SECONDS.toNanos(70) ? snapshot : null;
    }
}
