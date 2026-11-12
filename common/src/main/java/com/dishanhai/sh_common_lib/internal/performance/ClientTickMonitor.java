// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.performance;

import net.minecraft.network.Connection;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Client-thread polling and rate calculation from successive samples of the same server. */
public final class ClientTickMonitor {
    public static final ClientTickMonitor INSTANCE = new ClientTickMonitor();
    private static final long INTERVAL = TimeUnit.SECONDS.toNanos(2);
    private static final long EXPIRY = TimeUnit.SECONDS.toNanos(8);

    private Connection connection;
    private long lastRequest;
    private long receivedAt;
    private ServerTickSample sample;
    private double tps = Double.NaN;

    private ClientTickMonitor() {}

    /** Pass null to stop the detector and discard its last connection's readings. */
    public void tick(Connection active, Consumer<Connection> request) {
        long now = System.nanoTime();
        boolean changed = connection != active;
        if (changed) {
            connection = active;
            sample = null;
            tps = Double.NaN;
        }
        if (active != null && (changed || now - lastRequest >= INTERVAL)) {
            lastRequest = now;
            request.accept(active);
        }
    }

    public void accept(Connection source, ServerTickSample updated) {
        if (source != connection || source == null || !Double.isFinite(updated.mspt()) || updated.mspt() < 0) return;
        tps = sample == null ? Double.NaN : between(sample, updated);
        sample = updated;
        receivedAt = System.nanoTime();
    }

    static double between(ServerTickSample previous, ServerTickSample next) {
        int ticks = next.tickCount() - previous.tickCount();
        long elapsed = next.nanoTime() - previous.nanoTime();
        if (ticks < 0 || elapsed < TimeUnit.SECONDS.toNanos(1) || elapsed > EXPIRY) return Double.NaN;
        return ticks * 1_000_000_000.0 / elapsed;
    }

    public double tps() { return fresh() ? tps : Double.NaN; }

    public double mspt() { return fresh() ? sample.mspt() : Double.NaN; }

    private boolean fresh() {
        return sample != null && System.nanoTime() - receivedAt < EXPIRY;
    }
}
