// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Bounded, session-only aggregate. Caller owns the server thread and the opt-in policy. */
public final class HotWordLedger {
    public static final int MAX_WORD_LENGTH = 64;
    public static final int MAX_RESULTS = 32;
    private static final int MAX_WORDS = 512;
    private static final int MAX_PLAYERS = 2048;
    private static final long RATE_MS = 5_000L;
    private static final long EXPIRY_MS = 6 * 60 * 60 * 1_000L;
    private record Count(String text, int count, long last) {}
    private final Map<String, Count> words = new HashMap<>();
    private final Map<UUID, Long> lastReport = new HashMap<>();
    private final Map<UUID, Long> lastRequest = new HashMap<>();

    public boolean allowRequest(UUID player, long now) {
        if (player == null) return false;
        lastRequest.entrySet().removeIf(entry -> now - entry.getValue() >= EXPIRY_MS || now < entry.getValue());
        Long previous = lastRequest.get(player);
        if (previous != null && now - previous < RATE_MS || previous == null && lastRequest.size() >= MAX_PLAYERS) return false;
        lastRequest.put(player, now);
        return true;
    }

    public boolean record(UUID player, String text, long now) {
        if (player == null || text == null) return false;
        String word = text.strip();
        if (word.length() < 2 || word.length() > MAX_WORD_LENGTH || word.codePoints().anyMatch(Character::isISOControl)) return false;
        lastReport.entrySet().removeIf(entry -> now - entry.getValue() >= EXPIRY_MS || now < entry.getValue());
        Long previous = lastReport.get(player);
        if (previous != null && now - previous < RATE_MS) return false;
        if (previous == null && lastReport.size() >= MAX_PLAYERS) return false;
        lastReport.put(player, now);
        words.entrySet().removeIf(entry -> now - entry.getValue().last() >= EXPIRY_MS || now < entry.getValue().last());
        String key = word.toLowerCase(Locale.ROOT);
        Count old = words.get(key);
        if (old == null && words.size() >= MAX_WORDS) return false;
        words.put(key, new Count(word, old == null ? 1 : Math.min(Integer.MAX_VALUE, old.count() + (old.count() == Integer.MAX_VALUE ? 0 : 1)), now));
        return true;
    }

    public List<String> top(long now, int limit) {
        words.entrySet().removeIf(entry -> now - entry.getValue().last() >= EXPIRY_MS || now < entry.getValue().last());
        var ranked = new ArrayList<>(words.values());
        ranked.sort(Comparator.comparingInt(Count::count).reversed().thenComparing(Comparator.comparingLong(Count::last).reversed())
                .thenComparing(Count::text));
        return ranked.stream().limit(Math.max(0, Math.min(limit, MAX_RESULTS))).map(Count::text).toList();
    }
}
