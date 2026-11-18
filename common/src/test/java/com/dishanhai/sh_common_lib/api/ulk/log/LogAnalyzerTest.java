// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.log;

import java.io.StringReader;
import java.nio.file.Files;
import java.util.List;

public final class LogAnalyzerTest {
    public static void main(String[] args) throws Exception {
        sortsMixedMinecraftLogs();
        keepsStackTracesWithTheirEvents();
        groupsCrashReports();
        handlesStandaloneExceptions();
        readsUtf8AndKeepsOriginalText();
        System.out.println("LogAnalyzerTest: 5 scenarios passed");
    }

    private static void sortsMixedMinecraftLogs() {
        var analysis = LogAnalyzer.analyze("""
                [02:15:57] [Render thread/INFO]: [CHAT] Warning and fatal error are words
                [1111月2026 02:15:57.157] [modloading-worker-0/WARN] [example/MOD]: warning
                [2026-11-11 02:15:57.164] [Server thread/ERROR] [example/]: recoverable error
                [02:15:58] [Server thread/ERROR]: Encountered an unexpected exception
                WARNING: another warning
                [FATAL] Crash during initialization
                [DEBUG] debug message
                [TRACE] trace message
                plain text
                """);

        check(analysis.entries().stream().map(LogEntry::type).toList().equals(List.of(
                LogType.NORMAL, LogType.WARNING, LogType.ERROR, LogType.CRASH, LogType.WARNING,
                LogType.CRASH, LogType.NORMAL, LogType.NORMAL, LogType.NORMAL)), "source order and types");
        check(analysis.byWeight().stream().map(LogEntry::lineNumber).toList().equals(
                List.of(4, 6, 3, 2, 5, 1, 7, 8, 9)), "weight order with stable ties");
        check(analysis.byWeight().stream().map(LogEntry::weight).distinct().toList().equals(
                List.of(100, 70, 40, 10)), "default weights");
        check(analysis.entries(LogType.WARNING).stream().map(LogEntry::lineNumber).toList().equals(
                List.of(2, 5)), "filter by type");
        check(LogAnalyzer.analyze("\n \r\n").entries().isEmpty(), "blank input");
    }

    private static void keepsStackTracesWithTheirEvents() {
        var analysis = LogAnalyzer.analyze("  \n" + """
                [WARN] Retry after caught exception
                java.io.IOException: could not read
                \tat example.Reader.read(Reader.java:10) ~[example.jar:?]
                \tSuppressed: java.lang.IllegalStateException: cannot close
                \t\tat example.Reader.close(Reader.java:22)
                Caused by: java.io.FileNotFoundException: config.json
                \t... 1 more
                [INFO] Ready
                """);

        check(analysis.entries().size() == 2, "stack trace count");
        var warning = analysis.entries().get(0);
        check(warning.type() == LogType.WARNING && warning.lineNumber() == 2, "stack keeps parent severity");
        check(warning.text().contains("\n\tSuppressed:") && warning.text().contains("\nCaused by:")
                && warning.text().endsWith("\n\t... 1 more"), "stack content and whitespace");
        check(analysis.entries().get(1).lineNumber() == 9, "next event line");
    }

    private static void groupsCrashReports() {
        String minecraft = """
                ---- Minecraft Crash Report ----
                // example crash report

                Description: Exception in server tick loop
                java.lang.IllegalStateException: invalid state
                \tat example.Server.tick(Server.java:42)

                -- System Details --
                Details:
                \tMinecraft Version: 1.21.1
                """.stripTrailing();
        String hotspot = """
                # A fatal error has been detected by the Java Runtime Environment:
                #
                # EXCEPTION_ACCESS_VIOLATION
                # Problematic frame:
                # C  [example.dll+0x123]
                """.stripTrailing();
        var analysis = LogAnalyzer.analyze("[ERROR] " + minecraft + "\n" + hotspot + "\n[WARN] next event");

        check(analysis.entries().size() == 3, "report boundaries");
        var crashes = analysis.entries(LogType.CRASH);
        check(crashes.get(0).text().equals("[ERROR] " + minecraft), "complete prefixed Minecraft crash report");
        check(crashes.get(1).text().equals(hotspot), "complete HotSpot crash report");
        check(LogAnalyzer.analyze(minecraft).entries(LogType.CRASH).get(0).text().equals(minecraft),
                "standalone Minecraft crash report");
        check(analysis.entries().get(2).type() == LogType.WARNING, "structured event following report");
    }

    private static void handlesStandaloneExceptions() {
        var analysis = LogAnalyzer.analyze("""
                [INFO] Booting
                Exception in thread "main" java.lang.IllegalStateException: boom
                \tat example.Main.main(Main.java:5)
                Exception in thread "worker" java.lang.IllegalStateException: retry
                \tat example.Work.run(Work.java:6)

                java.lang.NoClassDefFoundError: example/Missing
                \tat example.Mod.load(Mod.java:1)
                #@!@# Game crashed! Crash report saved to: #@!@# crash.txt
                """);
        check(analysis.entries().stream().map(LogEntry::type).toList().equals(List.of(
                LogType.NORMAL, LogType.CRASH, LogType.ERROR, LogType.ERROR, LogType.CRASH)),
                "worker exception and standalone error have separate severity from crash markers");
        check(analysis.entries().get(1).text().endsWith("\tat example.Main.main(Main.java:5)"),
                "standalone crash preserves its stack");
    }

    private static void readsUtf8AndKeepsOriginalText() throws Exception {
        String warning = "\uFEFF\u001B[33m[WARN] 配置读取失败\u001B[0m";
        String text = warning + "\r\n\r\n[INFO] 完成";
        var path = Files.createTempFile("shclib-log-analyzer-", ".log");
        try {
            Files.writeString(path, text);
            var analysis = LogAnalyzer.analyze(path);
            var reader = new StringReader(text) {
                @Override
                public void close() {
                    throw new AssertionError("caller-owned reader was closed");
                }
            };
            check(analysis.equals(LogAnalyzer.analyze(reader)) && analysis.equals(LogAnalyzer.analyze(text)),
                    "path, reader and text agree");
            check(analysis.entries().get(0).equals(new LogEntry(1, LogType.WARNING, warning)),
                    "BOM and ANSI colors preserved in the original text");
            check(analysis.entries().get(1).lineNumber() == 3, "CRLF line numbers");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
