// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Groups host-supplied localized labels by namespace for searchable mod aliases. */
public final class SearchModNames {
    public record Label(String namespace, String name) {}
    private SearchModNames() {}

    public static Map<String, List<String>> index(List<Label> labels) {
        var grouped = new LinkedHashMap<String, LinkedHashSet<String>>();
        for (var label : labels) {
            if (label.namespace() == null || label.name() == null) continue;
            String namespace = label.namespace().strip(), name = label.name().strip();
            if (namespace.isEmpty() || name.isEmpty()) continue;
            grouped.computeIfAbsent(namespace, ignored -> new LinkedHashSet<>()).add(name);
        }
        var result = new LinkedHashMap<String, List<String>>();
        grouped.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    public static String searchText(String displayName, List<String> aliases) {
        var values = new LinkedHashSet<String>();
        if (displayName != null && !displayName.isBlank()) values.add(displayName.strip());
        for (String alias : aliases) if (alias != null && !alias.isBlank()) values.add(alias.strip());
        return String.join(" ", values);
    }

    public static String displayName(String fallback, List<String> aliases) {
        for (String alias : aliases) if (alias != null && !alias.isBlank()) return alias.strip();
        return fallback == null ? "" : fallback.strip();
    }
}
