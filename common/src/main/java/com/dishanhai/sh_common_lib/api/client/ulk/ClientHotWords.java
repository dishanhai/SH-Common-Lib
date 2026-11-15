// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import com.dishanhai.sh_common_lib.api.ulk.HotWordLedger;
import java.util.List;

/** Session-scoped client bridge. The loader transport must check the remote channel before sending. */
public final class ClientHotWords {
    public interface Transport {
        void request(Object connection);
        void report(Object connection, String query);
    }
    private static Transport transport;
    private static Object connection;
    private static List<String> words = List.of();
    private static List<String> externalWords = List.of();
    private static boolean receive, share;
    private static long nextRequest = Long.MIN_VALUE, nextReport = Long.MIN_VALUE;
    private ClientHotWords() {}

    public static void install(Transport next) { transport = next; }
    public static void update(Object current, boolean receiveEnabled, boolean shareEnabled, long now) {
        if (connection != current) { connection = current; words = externalWords = List.of(); nextRequest = nextReport = Long.MIN_VALUE; }
        receive = receiveEnabled;
        share = shareEnabled;
        if (!receive || current == null) words = externalWords = List.of();
        if (receive && current != null && transport != null && now >= nextRequest) {
            nextRequest = now + 30_000L;
            try { transport.request(current); } catch (RuntimeException | LinkageError ignored) { /* Optional transport may disconnect. */ }
        }
    }
    public static void accept(Object source, List<String> result) {
        if (!receive || source == null || source != connection) return;
        words = clean(result);
    }
    /** Supplies a second local provider's already-received words for the same connection. */
    public static void acceptExternal(Object source, List<String> result) {
        if (!receive || source == null || source != connection) return;
        externalWords = clean(result);
    }
    private static List<String> clean(List<String> result) {
        return result.stream().filter(word -> word != null && !word.isBlank()
                && word.length() <= HotWordLedger.MAX_WORD_LENGTH && word.codePoints().noneMatch(Character::isISOControl))
                .distinct().limit(HotWordLedger.MAX_RESULTS).toList();
    }
    public static void report(String query, long now) {
        if (!share || connection == null || transport == null || query == null || now < nextReport) return;
        String word = query.strip();
        if (word.length() < 2 || word.length() > HotWordLedger.MAX_WORD_LENGTH || word.codePoints().anyMatch(Character::isISOControl)) return;
        nextReport = now + 5_000L;
        try { transport.report(connection, word); } catch (RuntimeException | LinkageError ignored) { /* Do not disable local search. */ }
    }
    public static List<String> words() {
        var combined = new java.util.LinkedHashSet<>(words);
        combined.addAll(externalWords);
        return combined.stream().limit(HotWordLedger.MAX_RESULTS).toList();
    }
    public static void reset() { connection = null; words = externalWords = List.of(); receive = share = false; nextRequest = nextReport = Long.MIN_VALUE; }
}
