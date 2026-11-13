// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.util.Objects;
import java.util.Optional;

/** Literal display highlights. Phonetic and alias matches need not have a literal span. */
public final class SearchMatch {
    private SearchMatch() {}

    public record Range(int start, int end) {}

    /** An inline suffix is safe only when the completion literally starts with the whole query. */
    public static String suffix(String query, String completion) {
        Objects.requireNonNull(query);
        Objects.requireNonNull(completion);
        return !query.isEmpty() && completion.length() > query.length()
                && completion.regionMatches(true, 0, query, 0, query.length())
                ? completion.substring(query.length()) : "";
    }

    public static Optional<Range> literal(String label, String query) {
        Objects.requireNonNull(label);
        Objects.requireNonNull(query);
        String text = query.strip();
        if (text.isEmpty() || text.length() > label.length()) return Optional.empty();
        for (int i = 0; i <= label.length() - text.length(); i++) {
            if (label.regionMatches(true, i, text, 0, text.length())) return Optional.of(new Range(i, i + text.length()));
        }
        return Optional.empty();
    }
}
