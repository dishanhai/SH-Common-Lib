// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Parses Minecraft, Forge, NeoForge and JVM text logs without loader initialization. */
public final class LogAnalyzer {
    private static final String LEVEL = "(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)";
    private static final Pattern BRACKETED_LEVEL = Pattern.compile(
            "^(?:\\[[^\\]]*?\\d{2}:\\d{2}:\\d{2}[^\\]]*\\]\\s*)?"
                    + "\\[(?:[^/\\]]+/)?" + LEVEL + "\\]"
                    + "(?:\\s*\\[[^\\]]*\\](?=\\s*:))?\\s*:?\\s*");
    private static final Pattern PLAIN_LEVEL = Pattern.compile("^" + LEVEL + "(?=[:\\s]|$)\\s*:?\\s*");
    private static final Pattern ANSI_COLOR = Pattern.compile("\u001B\\[[0-9;:]*m");
    private static final Pattern UNCAUGHT = Pattern.compile("^Exception in thread \"([^\"]+)\"(?:\\s|$)");
    private static final Pattern THROWABLE = Pattern.compile(
            "^(?:[\\w$]+\\.)*(?:[\\w$]*(?:Exception|Error|Throwable))(?::|$)");
    private static final Pattern STACK_LINE = Pattern.compile(
            "^(?:at\\s+\\S+\\([^)]*\\)|Caused by:|Suppressed:|\\.\\.\\. \\d+ (?:more|common frames omitted)$)");

    private LogAnalyzer() {}

    public static LogAnalysis analyze(String text) {
        try {
            return analyze(new StringReader(text));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Reads a UTF-8 log and closes the file when analysis finishes. */
    public static LogAnalysis analyze(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return analyze(reader);
        }
    }

    /** Reads through the end of the input. The caller retains ownership of the reader. */
    public static LogAnalysis analyze(Reader reader) throws IOException {
        BufferedReader lines = reader instanceof BufferedReader buffered ? buffered : new BufferedReader(reader);
        List<LogEntry> entries = new ArrayList<>();
        PendingEntry current = null;
        int lineNumber = 0;
        String line;
        while ((line = lines.readLine()) != null) {
            lineNumber++;
            String content = ANSI_COLOR.matcher(line).replaceAll("");
            if (lineNumber == 1 && content.startsWith("\uFEFF")) content = content.substring(1);
            content = content.strip();

            var header = BRACKETED_LEVEL.matcher(content);
            if (!header.lookingAt()) header = PLAIN_LEVEL.matcher(content);
            boolean hasLevel = header.lookingAt();
            String level = hasLevel ? header.group(1) : "";
            String message = hasLevel ? content.substring(header.end()) : content;
            boolean report = (level.isEmpty() || level.equals("ERROR") || level.equals("FATAL"))
                    && isReportHeading(message);
            if (current != null && !hasLevel && !report && current.continuesWith(content)) {
                current.text.append('\n').append(line);
                continue;
            }

            if (current != null) entries.add(current.finish());
            current = null;
            if (content.isEmpty()) continue;

            LogType type = report ? LogType.CRASH
                    : hasLevel ? classifyLevel(level, message) : classifyStandalone(content);
            current = new PendingEntry(lineNumber, type, hasLevel, report, new StringBuilder(line));
        }
        if (current != null) entries.add(current.finish());
        return new LogAnalysis(entries);
    }

    private static LogType classifyLevel(String level, String message) {
        return switch (level) {
            case "FATAL" -> LogType.CRASH;
            case "ERROR" -> isCrashMessage(message) ? LogType.CRASH : LogType.ERROR;
            case "WARN", "WARNING" -> LogType.WARNING;
            default -> LogType.NORMAL;
        };
    }

    private static LogType classifyStandalone(String content) {
        if (isCrashMessage(content)) return LogType.CRASH;
        var uncaught = UNCAUGHT.matcher(content);
        if (uncaught.lookingAt()) {
            return switch (uncaught.group(1)) {
                case "main", "Render thread", "Server thread" -> LogType.CRASH;
                default -> LogType.ERROR;
            };
        }
        if (THROWABLE.matcher(content).lookingAt() || STACK_LINE.matcher(content).lookingAt()) return LogType.ERROR;
        return LogType.NORMAL;
    }

    private static boolean isReportHeading(String content) {
        return content.equals("---- Minecraft Crash Report ----")
                || content.equals("# A fatal error has been detected by the Java Runtime Environment:");
    }

    private static boolean isCrashMessage(String content) {
        return content.startsWith("Encountered an unexpected exception")
                || content.startsWith("Reported exception thrown!")
                || content.startsWith("This crash report has been saved to:")
                || content.startsWith("Crash report saved to:")
                || content.startsWith("#@!@# Game crashed!");
    }

    private record PendingEntry(int lineNumber, LogType type, boolean hasLevel, boolean report, StringBuilder text) {
        boolean continuesWith(String content) {
            return report || STACK_LINE.matcher(content).lookingAt()
                    || hasLevel && THROWABLE.matcher(content).lookingAt();
        }

        LogEntry finish() {
            return new LogEntry(lineNumber, type, text.toString());
        }
    }
}
