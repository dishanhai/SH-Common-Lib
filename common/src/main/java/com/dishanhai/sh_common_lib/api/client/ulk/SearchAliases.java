// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Optional UTF-8 JSON map from stable source IDs to searchable aliases. */
public final class SearchAliases {
    private SearchAliases() {}

    public static Map<String, List<String>> load(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) throw new IOException("Alias root must be an object");
            Map<String, List<String>> aliases = new LinkedHashMap<>();
            for (var entry : root.getAsJsonObject().entrySet()) {
                if (entry.getKey().isBlank() || !entry.getValue().isJsonArray())
                    throw new IOException("Alias entry must have an ID and an array");
                var names = new ArrayList<String>();
                for (var value : entry.getValue().getAsJsonArray()) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                        throw new IOException("Alias must be text");
                    String name = value.getAsString().strip();
                    if (!name.isEmpty() && !names.contains(name)) names.add(name);
                }
                aliases.put(entry.getKey(), List.copyOf(names));
            }
            return Map.copyOf(aliases);
        } catch (NoSuchFileException missing) {
            return Map.of();
        } catch (com.google.gson.JsonParseException invalid) {
            throw new IOException("Invalid alias JSON", invalid);
        }
    }

    public static List<String> merge(List<String> existing, Map<String, List<String>> custom, String id) {
        var merged = new ArrayList<>(existing);
        for (String alias : custom.getOrDefault(id, List.of())) if (!merged.contains(alias)) merged.add(alias);
        return List.copyOf(merged);
    }
}
