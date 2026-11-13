// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import oshi.SystemInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Asynchronous aggregate of non-loopback interface counters. */
final class LocalNetworkTraffic {
    private final Executor executor;
    private final LongSupplier clock;
    private boolean pending, enabled;
    private long lastRequest, lastCompleted, generation;
    private Map<String, Counter> previous = Map.of();
    private GameTraffic.Rate rate;
    private SystemInfo system;

    LocalNetworkTraffic(Executor executor) { this(executor, System::nanoTime); }

    LocalNetworkTraffic(Executor executor, LongSupplier clock) {
        this.executor = executor;
        this.clock = clock;
    }

    synchronized Optional<GameTraffic.Rate> read(boolean active) {
        if (!active) {
            if (enabled) {
                enabled = false;
                generation++;
                previous = Map.of();
                rate = null;
                lastCompleted = 0;
            }
            return Optional.empty();
        }
        enabled = true;
        long now = clock.getAsLong();
        if (!pending && (lastRequest == 0 || now - lastRequest >= TimeUnit.SECONDS.toNanos(2))) {
            pending = true;
            lastRequest = now;
            long requestGeneration = generation;
            try {
                executor.execute(() -> sample(requestGeneration));
            } catch (RuntimeException rejected) {
                pending = false;
            }
        }
        return Optional.ofNullable(rate);
    }

    synchronized Optional<GameTraffic.Rate> current() { return Optional.ofNullable(rate); }

    private void sample(long requestGeneration) {
        Map<String, Counter> current = new HashMap<>();
        boolean success = false;
        try {
            if (system == null) system = new SystemInfo();
            for (var network : system.getHardware().getNetworkIFs()) {
                if (network.updateAttributes()) {
                    current.put(network.getName(), new Counter(network.getBytesRecv(), network.getBytesSent()));
                }
            }
            success = true;
        } catch (RuntimeException | LinkageError ignored) {
            // Counters are optional and may not exist on the current operating system.
        }
        long now = clock.getAsLong();
        synchronized (this) {
            pending = false;
            if (!enabled || requestGeneration != generation) return;
            if (success && lastCompleted != 0 && now > lastCompleted) {
                long received = 0, sent = 0;
                int matched = 0;
                for (var entry : current.entrySet()) {
                    Counter before = previous.get(entry.getKey()), after = entry.getValue();
                    if (before != null && after.received >= before.received && after.sent >= before.sent) {
                        received += after.received - before.received;
                        sent += after.sent - before.sent;
                        matched++;
                    }
                }
                rate = matched == 0 ? null : new GameTraffic.Rate(received * 1_000_000_000.0 / (now - lastCompleted),
                        sent * 1_000_000_000.0 / (now - lastCompleted));
            } else rate = null;
            previous = current;
            lastCompleted = success ? now : 0;
        }
    }

    private record Counter(long received, long sent) {}
}
