// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** A non-blocking snapshot with at most one pending refresh. */
final class AsyncMetric<T> {
    private final long interval;
    private final Executor executor;
    private final LongSupplier clock;
    private Optional<T> last = Optional.empty();
    private long completed;
    private boolean sampled, pending;

    AsyncMetric(long interval, Executor executor, LongSupplier clock) {
        this.interval = interval;
        this.executor = executor;
        this.clock = clock;
    }

    synchronized Optional<T> read(Supplier<T> supplier) {
        if (!pending && (!sampled || clock.getAsLong() - completed >= interval)) {
            pending = true;
            try {
                executor.execute(() -> sample(supplier));
            } catch (RuntimeException rejected) {
                pending = false;
                sampled = true;
                completed = clock.getAsLong();
            }
        }
        return last;
    }

    private void sample(Supplier<T> supplier) {
        T value = null;
        try {
            value = supplier.get();
        } catch (RuntimeException | LinkageError ignored) {
            // An unavailable OS counter should not block rendering or stop later retries.
        } finally {
            synchronized (this) {
                last = Optional.ofNullable(value);
                completed = clock.getAsLong();
                sampled = true;
                pending = false;
            }
        }
    }
}
