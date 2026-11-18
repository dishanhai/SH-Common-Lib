// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.log;

import java.util.Objects;

/** A log event with a one-based source line number and its original text with LF line breaks. */
public record LogEntry(int lineNumber, LogType type, String text) {
    public LogEntry {
        if (lineNumber < 1) throw new IllegalArgumentException("lineNumber must be positive");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
    }

    public int weight() {
        return type.weight();
    }
}
