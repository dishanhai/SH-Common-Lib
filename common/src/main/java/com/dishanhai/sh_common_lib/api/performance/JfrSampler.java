// Author: dishanhai
package com.dishanhai.sh_common_lib.api.performance;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Opt-in JVM-wide execution and allocation sampling with a bounded recording. */
public final class JfrSampler implements AutoCloseable {
    private final Recording recording;
    private final Instant started = Instant.now();
    private final String unavailable;

    private JfrSampler(Recording recording, String unavailable) { this.recording = recording; this.unavailable = unavailable; }

    public static JfrSampler start(String name, boolean enabled) {
        if (!enabled) return new JfrSampler(null, "disabled");
        Recording recording = null;
        try {
            if (!FlightRecorder.isAvailable()) return new JfrSampler(null, "JFR unavailable");
            recording = new Recording();
            recording.setName(name);
            recording.setToDisk(true);
            recording.setMaxSize(32L * 1024 * 1024);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20)).withStackTrace();
            recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(20)).withStackTrace();
            recording.enable("jdk.ObjectAllocationSample").with("throttle", "100/s").withStackTrace();
            recording.start();
            return new JfrSampler(recording, "");
        } catch (Exception | LinkageError error) {
            if (recording != null) recording.close();
            return new JfrSampler(null, "JFR unavailable: " + error.getClass().getSimpleName());
        }
    }

    /** Stops and dumps the recording once. Only events through the supplied end time are summarized. */
    public Summary finish(Path file, Instant ended) {
        if (recording == null) return new Summary(unavailable, List.of(), List.of());
        try (Recording closed = recording) {
            closed.stop();
            closed.dump(file);
            return read(file, started, ended);
        } catch (Exception error) {
            return new Summary("JFR failed: " + error.getClass().getSimpleName(), List.of(), List.of());
        }
    }

    @Override public void close() { if (recording != null) recording.close(); }

    static Summary read(Path file, Instant start, Instant end) throws java.io.IOException {
        Map<String, long[]> counters = new HashMap<>();
        try (RecordingFile input = new RecordingFile(file)) {
            while (input.hasMoreEvents()) {
                var event = input.readEvent();
                if (event.getStartTime().isBefore(start) || event.getStartTime().isAfter(end)) continue;
                String type = event.getEventType().getName();
                boolean cpu = type.equals("jdk.ExecutionSample") || type.equals("jdk.NativeMethodSample");
                if (!cpu && !type.equals("jdk.ObjectAllocationSample")) continue;
                String method = method(event.getStackTrace());
                if (counters.size() >= 4096 && !counters.containsKey(method)) method = "overflow";
                long[] values = counters.computeIfAbsent(method, key -> new long[3]);
                if (cpu) values[0]++;
                else { values[1] += event.getLong("weight"); values[2]++; }
            }
        }
        List<Hotspot> rows = counters.entrySet().stream().map(entry -> new Hotspot(entry.getKey(),
                entry.getValue()[0], entry.getValue()[1], entry.getValue()[2])).toList();
        return new Summary("complete (20 ms CPU samples, allocation estimates, 32 MiB rolling limit)",
                rows.stream().filter(row -> row.cpuSamples() > 0).sorted(Comparator.comparingLong(Hotspot::cpuSamples).reversed()).limit(64).toList(),
                rows.stream().filter(row -> row.allocationSamples() > 0).sorted(Comparator.comparingLong(Hotspot::sampledAllocationBytes).reversed()).limit(64).toList());
    }

    private static String method(RecordedStackTrace trace) {
        if (trace != null) for (var frame : trace.getFrames()) {
            String type = frame.getMethod().getType().getName();
            if (type.startsWith("java.") || type.startsWith("javax.") || type.startsWith("jdk.")
                    || type.startsWith("sun.") || type.startsWith("com.sun.")) continue;
            return type + "." + frame.getMethod().getName();
        }
        return "JVM / native / unknown stack";
    }

    public record Hotspot(String method, long cpuSamples, long sampledAllocationBytes, long allocationSamples) {}
    public record Summary(String status, List<Hotspot> cpuHotspots, List<Hotspot> allocationHotspots) {}
}
