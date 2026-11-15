// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Localized tag names indexed independently of the search UI and loader. */
public final class SearchTagNames<T> {
    private record Tag<T>(String id, String name, String forward, String reverse, List<T> values) {}
    private final List<Tag<T>> tags;

    public SearchTagNames(List<ClientSearch.Entry<T>> entries, Map<String, String> names) {
        var values = new LinkedHashMap<String, LinkedHashSet<T>>();
        for (var entry : entries) for (String id : entry.tags())
            values.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(entry.value());
        var built = new ArrayList<Tag<T>>();
        values.forEach((id, items) -> {
            String name = names.getOrDefault(id, id).toLowerCase(Locale.ROOT);
            String[] parts = name.split(" · ");
            StringBuilder forward = new StringBuilder(), reverse = new StringBuilder();
            for (String part : parts) forward.append(part);
            for (int i = parts.length - 1; i >= 0; i--) reverse.append(parts[i]);
            built.add(new Tag<>(id, name, forward.toString(), reverse.toString(), List.copyOf(items)));
        });
        tags = List.copyOf(built);
    }

    /** Names use a standard full tag translation first, then independently translated path parts. */
    public static String display(String id, Function<String, String> translate) {
        Objects.requireNonNull(id); Objects.requireNonNull(translate);
        String full = "tag.item." + id.replace(':', '.').replace('/', '.');
        String translated = translate.apply(full);
        if (translated != null && !translated.isBlank() && !translated.equals(full)) return translated;
        int colon = id.indexOf(':');
        String path = colon < 0 ? id : id.substring(colon + 1);
        var parts = new ArrayList<String>();
        for (String part : path.split("/")) {
            if (part.isBlank()) continue;
            String key = "sh_common_lib.jei.tag." + part;
            String text = translate.apply(key);
            parts.add(text == null || text.isBlank() || text.equals(key) ? part.replace('_', ' ') : text);
        }
        return parts.isEmpty() ? id : String.join(" · ", parts);
    }

    /** Completes the last simple # token; earlier terms are restricted by allowed values. */
    public List<ClientSearch.Suggestion<T>> suggest(String query, Set<T> allowed, int limit) {
        if (limit <= 0 || query.isEmpty() || Character.isWhitespace(query.charAt(query.length() - 1))) return List.of();
        int start = query.length() - 1;
        while (start > 0 && !Character.isWhitespace(query.charAt(start - 1))) start--;
        String token = query.substring(start);
        if (!token.startsWith("#") || token.indexOf('"') >= 0) return List.of();
        String needle = token.substring(1).toLowerCase(Locale.ROOT);
        if (needle.isBlank()) return List.of();
        var result = new ArrayList<ClientSearch.Suggestion<T>>();
        for (var tag : tags) {
            if (!tag.name().contains(needle) && !tag.forward().contains(needle) && !tag.reverse().contains(needle)) continue;
            T value = tag.values().stream().filter(allowed::contains).findFirst().orElse(null);
            if (value == null) continue;
            String label = "#" + tag.id();
            result.add(new ClientSearch.Suggestion<>(value, query.substring(0, start) + label, label, 3));
            if (result.size() == limit) break;
        }
        return List.copyOf(result);
    }
}
