// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded typo suggestions for plain item names. Never rewrites a query. */
public final class SearchCorrection {
    private SearchCorrection() {}

    public static <T> List<ClientSearch.Suggestion<T>> suggest(
            String query, List<ClientSearch.Entry<T>> entries, int limit) {
        int[] input = query.strip().toLowerCase(Locale.ROOT).codePoints().toArray();
        if (limit <= 0 || input.length < 3 || input.length > 64) return List.of();
        for (int codePoint : input) {
            if (!Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint)) return List.of();
        }
        int threshold = input.length < 6 ? 1 : 2;
        var matches = new ArrayList<ClientSearch.Suggestion<T>>();
        for (var entry : entries) {
            int[] name = entry.name().strip().toLowerCase(Locale.ROOT).codePoints()
                    .limit(input.length + threshold).toArray();
            int distance = distance(input, name, threshold, true);
            if (distance > 0 && distance <= threshold) {
                matches.add(new ClientSearch.Suggestion<>(entry.value(), entry.name(), entry.name(), 7 + distance));
            }
        }
        matches.sort(Comparator.comparingInt(ClientSearch.Suggestion::matchRank));
        return List.copyOf(matches.subList(0, Math.min(limit, matches.size())));
    }

    /**
     * Finds a unique best value among normalized names. Full-name edits outrank one-word
     * expansions (or up to two extra characters in unspaced Chinese names).
     */
    public static <T> Optional<T> uniqueNameMatch(String query, Map<String, T> names) {
        int[] input = query.codePoints().limit(65).toArray();
        boolean unspacedChinese = input.length > 0 && java.util.Arrays.stream(input)
                .allMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN);
        if (input.length < (unspacedChinese ? 2 : 3) || input.length > 64) return Optional.empty();

        int threshold = input.length < 6 ? 1 : 2;
        int bestRank = threshold + 2;
        T best = null;
        boolean ambiguous = false;
        for (var entry : names.entrySet()) {
            String name = entry.getKey();
            int[] candidate = name.codePoints().limit(input.length + threshold + 1L).toArray();
            int rank = distance(input, candidate, threshold, false);
            if (rank > threshold && !expands(query, name, unspacedChinese)) continue;
            if (rank < bestRank) {
                bestRank = rank;
                best = entry.getValue();
                ambiguous = false;
            } else if (rank == bestRank && !Objects.equals(best, entry.getValue())) {
                ambiguous = true;
            }
        }
        return ambiguous ? Optional.empty() : Optional.ofNullable(best);
    }

    private static boolean expands(String query, String name, boolean unspacedChinese) {
        if (unspacedChinese) {
            return (name.startsWith(query) || name.endsWith(query))
                    && name.codePointCount(0, name.length()) - query.codePointCount(0, query.length()) <= 2;
        }
        return (name.startsWith(query + " ") && name.indexOf(' ', query.length() + 1) < 0)
                || (name.endsWith(" " + query) && name.indexOf(' ') == name.length() - query.length() - 1);
    }

    private static int distance(int[] input, int[] name, int threshold, boolean prefix) {
        if (name.length < input.length - threshold || (!prefix && name.length > input.length + threshold)) {
            return threshold + 1;
        }
        int[] previous = new int[name.length + 1];
        int[] current = new int[name.length + 1];
        for (int j = 0; j <= name.length; j++) previous[j] = j;
        for (int i = 1; i <= input.length; i++) {
            current[0] = i;
            int minimum = current[0];
            for (int j = 1; j <= name.length; j++) {
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + (input[i - 1] == name[j - 1] ? 0 : 1));
                minimum = Math.min(minimum, current[j]);
            }
            if (minimum > threshold) return threshold + 1;
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        if (!prefix) return Math.min(previous[name.length], threshold + 1);
        int best = threshold + 1;
        for (int j = Math.max(1, input.length - threshold); j <= name.length; j++) {
            best = Math.min(best, previous[j]);
        }
        return best;
    }
}
