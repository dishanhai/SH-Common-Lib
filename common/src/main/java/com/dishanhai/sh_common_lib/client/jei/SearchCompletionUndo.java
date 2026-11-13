// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import java.util.Objects;
import java.util.Optional;

/** A completion can be undone only while its exact result is still present. */
final class SearchCompletionUndo {
    private String previous;
    private String applied;

    void accepted(String before, String after) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        previous = before.equals(after) ? null : before;
        applied = previous == null ? null : after;
    }

    void observe(String value) {
        if (previous != null && !Objects.equals(applied, value)) clear();
    }

    Optional<String> undo(String value) {
        observe(value);
        String result = previous;
        clear();
        return Optional.ofNullable(result);
    }

    void clear() { previous = applied = null; }
}
