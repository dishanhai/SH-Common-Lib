// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
            int distance = prefixDistance(input, name, threshold);
            if (distance > 0 && distance <= threshold) {
                matches.add(new ClientSearch.Suggestion<>(entry.value(), entry.name(), entry.name(), 7 + distance));
            }
        }
        matches.sort(Comparator.comparingInt(ClientSearch.Suggestion::matchRank));
        return List.copyOf(matches.subList(0, Math.min(limit, matches.size())));
    }

    private static int prefixDistance(int[] input, int[] name, int threshold) {
        if (name.length < input.length - threshold) return threshold + 1;
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
        int best = threshold + 1;
        for (int j = Math.max(1, input.length - threshold); j <= name.length; j++) {
            best = Math.min(best, previous[j]);
        }
        return best;
    }
}
