// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Client-side search snapshots. Sources and values are supplied by the consuming mod. */
public final class ClientSearch {
    private ClientSearch() {}

    public record Entry<T>(T value, String name, List<String> ids, List<String> aliases,
                           String modName, List<String> tags) {
        public Entry {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(name, "name");
            ids = List.copyOf(ids);
            aliases = List.copyOf(aliases);
            Objects.requireNonNull(modName, "modName");
            tags = List.copyOf(tags);
        }
    }

    public record Suggestion<T>(T value, String completion, String label, int matchRank) {
        public Suggestion(T value, String completion, String label) { this(value, completion, label, 0); }
    }

    public static <T> Index<T> index(List<Entry<T>> entries) {
        return new Index<>(entries);
    }

    public static final class Index<T> {
        private final SearchIndex<T> delegate;

        private Index(List<Entry<T>> entries) {
            delegate = new SearchIndex<>(entries.stream().map(entry -> new SearchIndex.Entry<>(entry.value(),
                    entry.name(), entry.ids(), entry.aliases(), entry.modName(), entry.tags())).toList());
        }

        public List<T> search(String query) {
            return search(query, ignored -> true);
        }

        public List<T> search(String query, Predicate<? super T> scope) {
            return delegate.search(query, scope).stream().map(SearchIndex.Entry::value).toList();
        }

        public List<Suggestion<T>> suggest(String query, int limit) {
            return delegate.suggest(query, limit).stream().map(suggestion -> new Suggestion<>(
                    suggestion.entry().value(), suggestion.completion(), suggestion.label(), suggestion.matchRank())).toList();
        }
    }
}
