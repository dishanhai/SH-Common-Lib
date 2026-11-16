// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.recipe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BrewingDefinitionTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("shclib-brewing-test");
        try {
            Path namespace = Files.createDirectories(directory.resolve("example"));
            Files.writeString(namespace.resolve("valid.json"), """
                    {"id":"example:valid","input":"minecraft:water",
                     "ingredient":"#minecraft:flowers","output":"minecraft:awkward"}
                    """);
            Files.writeString(namespace.resolve("broken.json"), "{not-json");
            var problems = new ArrayList<String>();
            var definitions = BrewingDefinitions.load(directory, problems::add);
            check(definitions.size() == 1, "valid recipe is retained despite broken neighbor");
            check(definitions.get(0).ingredient().equals("#minecraft:flowers"), "ingredient tag retained");
            check(definitions.get(0).kind() == BrewingDefinitions.Kind.POTION, "missing kind retains potion behavior");
            check(problems.size() == 1 && problems.get(0).contains("broken.json"), "error names broken file");

            Files.writeString(namespace.resolve("invalid.json"), """
                    {"id":"example:invalid","input":"../water",
                     "ingredient":"minecraft:sugar","output":"minecraft:awkward"}
                    """);
            problems.clear();
            definitions = BrewingDefinitions.load(directory, problems::add);
            check(definitions.size() == 1 && problems.size() == 2, "invalid identifiers rejected");

            Files.writeString(namespace.resolve("duplicate.json"), """
                    {"id":"example:valid","input":"minecraft:water",
                     "ingredient":"minecraft:sugar","output":"minecraft:awkward"}
                    """);
            problems.clear();
            definitions = BrewingDefinitions.load(directory, problems::add);
            check(definitions.size() == 1 && problems.stream().anyMatch(s -> s.contains("duplicate")),
                    "duplicate recipe identifiers rejected");

            Files.writeString(namespace.resolve("container.json"), """
                    {"id":"example:container","kind":"container","input":"minecraft:potion",
                     "ingredient":"minecraft:gunpowder","output":"minecraft:splash_potion"}
                    """);
            Files.writeString(namespace.resolve("wrong-kind.json"), """
                    {"id":"example:wrong","kind":"unsupported","input":"minecraft:potion",
                     "ingredient":"minecraft:gunpowder","output":"minecraft:splash_potion"}
                    """);
            problems.clear();
            definitions = BrewingDefinitions.load(directory, problems::add);
            check(definitions.size() == 2 && definitions.stream().anyMatch(d -> d.kind() == BrewingDefinitions.Kind.CONTAINER
                    && d.input().equals("minecraft:potion") && d.output().equals("minecraft:splash_potion")),
                    "container conversion retains both item IDs");
            check(problems.stream().anyMatch(s -> s.contains("wrong-kind.json")), "unknown kind is rejected");

            var fromPack = BrewingDefinitions.loadDatapack(Map.of(
                    "example:valid", "{\"input\":\"minecraft:water\",\"ingredient\":\"minecraft:diamond\",\"output\":\"minecraft:awkward\"}",
                    "other:nested/container", "{\"kind\":\"container\",\"input\":\"minecraft:potion\",\"ingredient\":\"#minecraft:flowers\",\"output\":\"minecraft:splash_potion\"}",
                    "example:malformed", "{bad",
                    "example:wrong_id", "{\"id\":\"example:elsewhere\",\"input\":\"minecraft:water\",\"ingredient\":\"minecraft:diamond\",\"output\":\"minecraft:awkward\"}"), problems::add);
            check(fromPack.size() == 2, "malformed resources and mismatching IDs are isolated");
            check(fromPack.stream().anyMatch(d -> d.id().equals("other:nested/container") && d.kind() == BrewingDefinitions.Kind.CONTAINER),
                    "data resource path supplies namespace and nested ID");
            var combined = BrewingDefinitions.merge(definitions, fromPack);
            check(combined.size() == 3, "data pack overrides matching config ID once");
            check(combined.get(0).id().equals("example:valid") && combined.get(0).ingredient().equals("minecraft:diamond"),
                    "data pack takes precedence over config");
            check(BrewingDefinitions.merge(definitions, List.of()).equals(definitions), "empty pack retains config rules");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
