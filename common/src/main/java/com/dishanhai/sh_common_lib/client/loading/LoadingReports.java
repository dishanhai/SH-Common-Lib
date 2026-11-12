// Author: dishanhai
package com.dishanhai.sh_common_lib.client.loading;

import com.dishanhai.sh_common_lib.api.performance.JfrSampler;
import com.dishanhai.sh_common_lib.api.performance.LoadingMetrics;
import com.dishanhai.sh_common_lib.api.performance.PerformanceSession;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class LoadingReports {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), task -> {
                Thread thread = new Thread(task, "shclib-loading-report");
                thread.setDaemon(true);
                return thread;
            });

    LoadingReports(Path directory) { this.directory = directory; }

    Path directory() { return directory; }

    Capture begin(String phase) {
        return new Capture(new PerformanceSession(phase), JfrSampler.start("SHCLIB " + phase, true));
    }

    void finish(Capture capture, String outcome) {
        var report = capture.session().finish(outcome);
        try {
            writer.execute(() -> write(report, capture.sampler()));
        } catch (RejectedExecutionException error) {
            capture.sampler().close();
            ClientLoading.LOGGER.warn("Loading report queue is full for {}", report.metrics().phase());
        }
    }

    private void write(PerformanceSession.Report report, JfrSampler sampler) {
        String name = "latest-" + report.metrics().phase();
        try (sampler) {
            Files.createDirectories(directory);
            Path recording = directory.resolve(name + ".jfr");
            Files.deleteIfExists(recording);
            var sampling = sampler.finish(recording, report.capturedAt());
            var data = new SavedReport(1, report.capturedAt().toString(), report.metrics(), report.processCpuNanos(),
                    report.processors(), report.gcCollections(), report.gcMillis(), sampling);
            Path file = directory.resolve(name + ".json");
            Path pending = Files.createTempFile(directory, name, ".tmp");
            try {
                Files.writeString(pending, JSON.toJson(data));
                try {
                    Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(pending);
            }
            ClientLoading.LOGGER.info("Loading report saved to {}", file);
        } catch (Exception error) {
            ClientLoading.LOGGER.warn("Could not save loading report {}", name, error);
        }
    }

    record Capture(PerformanceSession session, JfrSampler sampler) {}

    private record SavedReport(int schemaVersion, String capturedAt, LoadingMetrics.Report metrics,
                               long processCpuNanos, int processors, long gcCollections, long gcMillis,
                               JfrSampler.Summary sampling) {}
}
