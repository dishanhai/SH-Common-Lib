// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.log;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable analysis in source order. */
public record LogAnalysis(List<LogEntry> entries) {
    public LogAnalysis {
        entries = List.copyOf(entries);
    }

    public List<LogEntry> entries(LogType type) {
        Objects.requireNonNull(type, "type");
        return entries.stream().filter(entry -> entry.type() == type).toList();
    }

    /** Sorts by descending weight, retaining source order for equal weights. */
    public List<LogEntry> byWeight() {
        return entries.stream().sorted(Comparator.comparingInt(LogEntry::weight).reversed()).toList();
    }
}
