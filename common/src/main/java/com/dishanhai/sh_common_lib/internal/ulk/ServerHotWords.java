// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.ulk;

import com.dishanhai.sh_common_lib.api.ulk.HotWordLedger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server configuration is opt-in; aggregates never leave the server process except as a top list. */
public final class ServerHotWords {
    private static final Map<Object, HotWordLedger> SESSIONS = new WeakHashMap<>();
    private static boolean enabled;
    private ServerHotWords() {}

    public static void initialize(Path configDirectory) {
        enabled = false;
        SESSIONS.clear();
        var properties = new Properties();
        try (var input = Files.newInputStream(configDirectory.resolve("sh_common_lib").resolve("hotwords-server.properties"))) {
            properties.load(input);
            enabled = "true".equalsIgnoreCase(properties.getProperty("enabled", "false").trim());
        } catch (IOException | IllegalArgumentException ignored) { /* Absent or invalid config means disabled. */ }
    }

    public static boolean enabled() { return enabled; }
    public static void report(Object server, UUID player, String word) {
        if (enabled && server != null) SESSIONS.computeIfAbsent(server, ignored -> new HotWordLedger())
                .record(player, word, System.currentTimeMillis());
    }
    public static java.util.Optional<List<String>> top(Object server, UUID player) {
        if (!enabled || server == null) return java.util.Optional.empty();
        var ledger = SESSIONS.computeIfAbsent(server, ignored -> new HotWordLedger());
        long now = System.currentTimeMillis();
        return ledger.allowRequest(player, now) ? java.util.Optional.of(ledger.top(now, HotWordLedger.MAX_RESULTS)) : java.util.Optional.empty();
    }
    public static void clear(Object server) { SESSIONS.remove(server); }
}
