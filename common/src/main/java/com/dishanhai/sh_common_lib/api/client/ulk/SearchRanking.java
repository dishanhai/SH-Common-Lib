// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Match quality takes precedence; usage and recency only reorder equally good matches. */
public final class SearchRanking {
    private SearchRanking() {}

    public static <T> List<T> withUsage(List<T> matches, ToIntFunction<T> quality,
                                         ToIntFunction<T> frequency, ToIntFunction<T> recency) {
        Objects.requireNonNull(quality);
        Objects.requireNonNull(frequency);
        Objects.requireNonNull(recency);
        return matches.stream().sorted(Comparator.comparingInt(quality)
                .thenComparing(Comparator.comparingInt(frequency).reversed())
                .thenComparingInt(recency)).toList();
    }
}
