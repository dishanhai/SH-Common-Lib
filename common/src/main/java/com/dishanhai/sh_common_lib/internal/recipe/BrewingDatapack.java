// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.recipe;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.server.packs.resources.ResourceManager;

/** Reads the highest-priority resource for each data-pack path. */
public final class BrewingDatapack {
    private static final String ROOT = "sh_common_lib/brewing/";

    private BrewingDatapack() {}

    public static List<BrewingDefinitions.Definition> loadWithConfig(ResourceManager manager, Path config,
                                                                      Consumer<String> error) {
        return BrewingDefinitions.merge(BrewingDefinitions.load(config, error), load(manager, error));
    }

    public static List<BrewingDefinitions.Definition> load(ResourceManager manager, Consumer<String> error) {
        var resources = manager.listResources("sh_common_lib/brewing", id -> id.getPath().endsWith(".json"));
        var entries = resources.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .limit(BrewingDefinitions.MAX_FILES + 1L).toList();
        if (entries.size() > BrewingDefinitions.MAX_FILES) {
            error.accept("too many brewing resources (limit " + BrewingDefinitions.MAX_FILES + ")");
        }
        Map<String, String> json = new LinkedHashMap<>();
        for (var entry : entries.subList(0, Math.min(entries.size(), BrewingDefinitions.MAX_FILES))) {
            var location = entry.getKey();
            String path = location.getPath();
            if (!path.startsWith(ROOT) || path.length() <= ROOT.length() + 5) continue;
            String id = location.getNamespace() + ":" + path.substring(ROOT.length(), path.length() - 5);
            try (var input = entry.getValue().open()) {
                json.put(id, BrewingDefinitions.readBounded(input));
            } catch (IOException | RuntimeException failure) {
                error.accept(location + ": " + failure.getMessage());
            }
        }
        return BrewingDefinitions.loadDatapack(json, error);
    }
}
