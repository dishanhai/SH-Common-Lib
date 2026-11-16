// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Loader-independent representation of the editor's potion brewing definitions. */
public final class BrewingDefinitions {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    public static final int MAX_FILE_BYTES = 65536;
    public static final int MAX_FILES = 4096;

    private BrewingDefinitions() {}

    public enum Kind { POTION, CONTAINER }

    public record Definition(String id, Kind kind, String input, String ingredient, String output) {}

    public static List<Definition> load(Path directory, Consumer<String> error) {
        if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        List<Path> files;
        try (var paths = Files.walk(directory, 32)) {
            files = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().limit(MAX_FILES + 1L).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read brewing configuration " + directory, e);
        }
        var result = new ArrayList<Definition>();
        Set<String> ids = new HashSet<>();
        if (files.size() > MAX_FILES) error.accept(directory + ": too many brewing definitions (limit " + MAX_FILES + ")");
        for (Path file : files.subList(0, Math.min(files.size(), MAX_FILES))) {
            try {
                if (Files.size(file) > MAX_FILE_BYTES) throw new IllegalArgumentException("file exceeds size limit");
                JsonObject object;
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    object = JsonParser.parseReader(reader).getAsJsonObject();
                }
                Definition definition = definition(value(object, "id", false), object);
                if (!ids.add(definition.id())) throw new IllegalArgumentException("duplicate id " + definition.id());
                result.add(definition);
            } catch (IOException | RuntimeException e) {
                error.accept(file + ": " + e.getMessage());
            }
        }
        return List.copyOf(result);
    }

    /** Decodes data-pack resources after the caller has resolved pack priority and resource paths. */
    public static List<Definition> loadDatapack(Map<String, String> resources, Consumer<String> error) {
        var entries = resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(MAX_FILES + 1L).toList();
        if (entries.size() > MAX_FILES) error.accept("too many brewing resources (limit " + MAX_FILES + ")");
        var result = new ArrayList<Definition>();
        for (var entry : entries.subList(0, Math.min(entries.size(), MAX_FILES))) {
            try {
                String id = entry.getKey();
                if (!ID.matcher(id).matches()) throw new IllegalArgumentException("invalid resource id " + id);
                if (entry.getValue().length() > MAX_FILE_BYTES) throw new IllegalArgumentException("file exceeds size limit");
                JsonObject object = JsonParser.parseString(entry.getValue()).getAsJsonObject();
                if (object.has("id") && !id.equals(value(object, "id", false))) {
                    throw new IllegalArgumentException("id must match resource path " + id);
                }
                if (object.has("type") && !"sh_common_lib:brewing".equals(value(object, "type", false))) {
                    throw new IllegalArgumentException("unknown brewing type");
                }
                result.add(definition(id, object));
            } catch (RuntimeException e) {
                error.accept(entry.getKey() + ": " + e.getMessage());
            }
        }
        return List.copyOf(result);
    }

    /** Reads a bounded resource without permitting an oversized JSON to allocate without limit. */
    public static String readBounded(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        if (bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("file exceeds size limit");
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    }

    /** Data packs win over legacy configuration; distinct definitions keep deterministic order. */
    public static List<Definition> merge(List<Definition> config, List<Definition> datapack) {
        Map<String, Definition> merged = new LinkedHashMap<>();
        for (var definition : datapack) merged.putIfAbsent(definition.id(), definition);
        for (var definition : config) merged.putIfAbsent(definition.id(), definition);
        return List.copyOf(merged.values());
    }

    private static Definition definition(String id, JsonObject object) {
        return new Definition(id, kind(object), value(object, "input", false), value(object, "ingredient", true),
                value(object, "output", false));
    }

    private static Kind kind(JsonObject object) {
        JsonElement value = object.get("kind");
        if (value == null) return Kind.POTION;
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new IllegalArgumentException("kind must be potion or container");
        }
        return switch (primitive.getAsString()) {
            case "potion" -> Kind.POTION;
            case "container" -> Kind.CONTAINER;
            default -> throw new IllegalArgumentException("unknown brewing kind " + primitive.getAsString());
        };
    }

    private static String value(JsonObject object, String name, boolean allowTag) {
        JsonElement element = object.get(name);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new IllegalArgumentException("missing string " + name);
        }
        String value = primitive.getAsString();
        String identifier = allowTag && value.startsWith("#") ? value.substring(1) : value;
        if (!ID.matcher(identifier).matches()) throw new IllegalArgumentException("invalid " + name + ": " + value);
        return value;
    }
}
