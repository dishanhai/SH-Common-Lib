// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Coalesces search requests; callers supply detached snapshots from the client thread. */
public final class AsyncClientSearch<T> {
    private final Executor executor;
    private final boolean corrections;
    private List<ClientSearch.Entry<T>> entries = List.of();
    private Map<String, String> tagNames = Map.of();
    private long generation;
    private String query = "";
    private int limit;
    private boolean running;
    // Only the executor's drain accesses these; snapshots themselves are immutable.
    private List<ClientSearch.Entry<T>> indexedEntries;
    private Map<String, String> indexedTagNames;
    private ClientSearch.Index<Integer> index;
    private SearchTagNames<Integer> localizedTags;
    private volatile Result<T> result = new Result<>("", List.of());

    public record Result<T>(String query, List<ClientSearch.Suggestion<T>> suggestions) {}

    public AsyncClientSearch(Executor executor) { this(executor, false); }

    /** Optional correction is used only when ordinary suggestions are empty. */
    public AsyncClientSearch(Executor executor, boolean corrections) {
        this.executor = Objects.requireNonNull(executor);
        this.corrections = corrections;
    }

    public synchronized void replace(List<ClientSearch.Entry<T>> snapshot) {
        replace(snapshot, Map.of());
    }

    public synchronized void replace(List<ClientSearch.Entry<T>> snapshot, Map<String, String> names) {
        entries = List.copyOf(snapshot);
        tagNames = Map.copyOf(names);
        generation++;
        result = new Result<>("", List.of());
        schedule();
    }

    public synchronized void request(String next, int count) {
        Objects.requireNonNull(next);
        if (query.equals(next) && limit == count) return;
        query = next;
        limit = count;
        generation++;
        result = new Result<>(next, List.of());
        schedule();
    }

    public Result<T> result() { return result; }

    private void schedule() {
        if (!running) {
            running = true;
            executor.execute(this::drain);
        }
    }

    private void drain() {
        for (;;) {
            List<ClientSearch.Entry<T>> snapshot;
            Map<String, String> names;
            String text;
            int count;
            long version;
            synchronized (this) { snapshot = entries; names = tagNames; text = query; count = limit; version = generation; }
            try {
                if (snapshot != indexedEntries || names != indexedTagNames) {
                    var detached = new ArrayList<ClientSearch.Entry<Integer>>(snapshot.size());
                    for (int i = 0; i < snapshot.size(); i++) {
                        var e = snapshot.get(i);
                        detached.add(new ClientSearch.Entry<>(i, e.name(), e.ids(), e.aliases(), e.modName(), e.tags()));
                    }
                    index = ClientSearch.index(detached);
                    localizedTags = new SearchTagNames<>(detached, names);
                    indexedEntries = snapshot;
                    indexedTagNames = names;
                }
                var found = new ArrayList<ClientSearch.Suggestion<T>>();
                for (var hit : index.suggest(text, count)) {
                    found.add(new ClientSearch.Suggestion<>(snapshot.get(hit.value()).value(), hit.completion(), hit.label(), hit.matchRank()));
                }
                int last = text.length() - 1;
                while (last > 0 && !Character.isWhitespace(text.charAt(last - 1))) last--;
                if (last >= 0 && last < text.length() && text.charAt(last) == '#' && found.size() < count) {
                    var allowed = new HashSet<>(index.search(text.substring(0, last)));
                    var completions = new HashSet<String>();
                    for (var hit : found) completions.add(hit.completion());
                    for (var hit : localizedTags.suggest(text, allowed, count)) {
                        if (completions.add(hit.completion()))
                            found.add(new ClientSearch.Suggestion<>(snapshot.get(hit.value()).value(), hit.completion(), hit.label(), hit.matchRank()));
                        if (found.size() >= count) break;
                    }
                }
                if (found.isEmpty() && corrections) found.addAll(SearchCorrection.suggest(text, snapshot, count));
                synchronized (this) {
                    if (version == generation) {
                        result = new Result<>(text, List.copyOf(found));
                        running = false;
                        return;
                    }
                }
            } catch (RuntimeException error) {
                synchronized (this) { running = false; }
                throw error;
            }
        }
    }
}
