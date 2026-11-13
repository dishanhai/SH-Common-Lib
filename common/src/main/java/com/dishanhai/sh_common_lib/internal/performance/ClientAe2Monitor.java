// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.performance;

import net.minecraft.network.Connection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ClientAe2Monitor {
    public static final ClientAe2Monitor INSTANCE = new ClientAe2Monitor();
    private Connection connection;
    private String dimension;
    private Ae2Snapshot snapshot;
    private long lastRequest, receivedAt;

    private ClientAe2Monitor() {}

    public void tick(Connection active, String currentDimension, Consumer<Connection> request) {
        long now = System.nanoTime();
        boolean changed = connection != active || !Objects.equals(dimension, currentDimension);
        if (changed) {
            connection = active;
            dimension = currentDimension;
            snapshot = null;
        }
        if (active != null && (changed || now - lastRequest >= TimeUnit.SECONDS.toNanos(5))) {
            lastRequest = now;
            request.accept(active);
        }
    }

    public void accept(Connection source, Ae2Snapshot value) {
        if (source != null && source == connection && value.dimension().equals(dimension) && value.valid()) {
            snapshot = value;
            receivedAt = System.nanoTime();
        }
    }

    public Ae2Snapshot current() {
        return snapshot != null && System.nanoTime() - receivedAt < TimeUnit.SECONDS.toNanos(15) ? snapshot : null;
    }
}
