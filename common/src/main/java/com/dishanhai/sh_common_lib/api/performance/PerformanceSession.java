// Author: dishanhai
package com.dishanhai.sh_common_lib.api.performance;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * An opt-in profiling window for client or server code. Create one per operation or
 * sampling window and share it with its workers. All durations are inclusive.
 */
public final class PerformanceSession {
    private final long initialProcessCpu = processCpu();
    private final GcSample initialGc = gc();
    private final LoadingMetrics metrics;
    private volatile Report completed;

    public PerformanceSession(String phase) {
        metrics = new LoadingMetrics(phase, System::nanoTime, heap());
    }

    /** Records time, CPU and allocated bytes for work executed on the current thread. */
    public <T> T measure(String name, String kind, Supplier<T> work) {
        if (completed != null) return work.get();
        long started = System.nanoTime();
        var thread = ManagementFactory.getThreadMXBean();
        long cpu = threadCpu(thread);
        long allocated = allocated(thread);
        boolean failed = true;
        try {
            T result = work.get();
            failed = false;
            return result;
        } finally {
            metrics.record(name, kind, System.nanoTime() - started, delta(cpu, threadCpu(thread)),
                    delta(allocated, allocated(thread)), heap(), failed);
        }
    }

    /** Times prepare/apply execution and separately records elapsed time including asynchronous waits. */
    public <T> CompletableFuture<T> trackReload(String name, Executor prepare, Executor apply,
            BiFunction<Executor, Executor, CompletableFuture<T>> work) {
        if (completed != null) return work.apply(prepare, apply);
        long started = System.nanoTime();
        try {
            var future = work.apply(instrument(prepare, name, "prepare"), instrument(apply, name, "apply"));
            future.whenComplete((result, error) -> recordElapsed(name, "listener elapsed (includes waits)", started, error != null));
            return future;
        } catch (RuntimeException | Error error) {
            recordElapsed(name, "listener elapsed (includes waits)", started, true);
            throw error;
        }
    }

    public Executor instrument(Executor executor, String name, String kind) {
        return task -> executor.execute(() -> measure(name, kind, () -> {
            task.run();
            return null;
        }));
    }

    /** Records wall time from a caller-owned {@link System#nanoTime()} timestamp. */
    public void recordElapsed(String name, String kind, long started, boolean failed) {
        metrics.record(name, kind, System.nanoTime() - started, -1, -1, heap(), failed);
    }

    public void recordRecipe(String namespace, Object id, long started, boolean failed) {
        metrics.recipe(namespace, id, System.nanoTime() - started, failed);
    }

    /** Freezes the session. Later submissions are discarded and repeated calls return the same report. */
    public synchronized Report finish(String outcome) {
        if (completed == null) {
            var snapshot = metrics.finish(outcome, heap());
            var endGc = gc();
            completed = new Report(Instant.now(), snapshot, delta(initialProcessCpu, processCpu()),
                    Runtime.getRuntime().availableProcessors(), delta(initialGc.count(), endGc.count()),
                    delta(initialGc.millis(), endGc.millis()));
        }
        return completed;
    }

    private static long heap() {
        var runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long delta(long before, long after) {
        return before < 0 || after < before ? -1 : after - before;
    }

    private static long threadCpu(java.lang.management.ThreadMXBean thread) {
        return thread.isCurrentThreadCpuTimeSupported() && thread.isThreadCpuTimeEnabled()
                ? thread.getCurrentThreadCpuTime() : -1;
    }

    private static long allocated(java.lang.management.ThreadMXBean thread) {
        return thread instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()
                && bean.isThreadAllocatedMemoryEnabled() ? bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) : -1;
    }

    private static long processCpu() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean
                ? bean.getProcessCpuTime() : -1;
    }

    private static GcSample gc() {
        long count = 0;
        long millis = 0;
        for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            long samples = collector.getCollectionCount();
            long elapsed = collector.getCollectionTime();
            if (samples < 0 || elapsed < 0) return new GcSample(-1, -1);
            count += samples;
            millis += elapsed;
        }
        return new GcSample(count, millis);
    }

    private record GcSample(long count, long millis) {}

    public record Report(Instant capturedAt, LoadingMetrics.Report metrics, long processCpuNanos,
                         int processors, long gcCollections, long gcMillis) {}
}
