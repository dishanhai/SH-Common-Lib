// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable field editing for arbitrary recipe serializers. Paths contain literal keys or array indices. */
public final class RecipeJsonTree {
    private final JsonObject root;

    private RecipeJsonTree(JsonObject root) { this.root = root; }

    public static RecipeJsonTree parse(String source) {
        return new RecipeJsonTree(RecipeImporter.parse(source));
    }

    public String json() { return root.toString(); }

    public String value(List<String> path) { return node(root, path).toString(); }

    public String formattedValue(List<String> path) { return RecipeGenerator.GSON.toJson(node(root, path)); }

    public int childCount(List<String> path) {
        var value = node(root, path);
        return value.isJsonArray() ? value.getAsJsonArray().size() : value.getAsJsonObject().size();
    }

    public String stringValue(List<String> path) { return node(root, path).getAsString(); }

    public boolean isContainer(List<String> path) {
        var value = node(root, path);
        return value.isJsonObject() || value.isJsonArray();
    }

    public boolean isArray(List<String> path) { return node(root, path).isJsonArray(); }

    public List<String> keys(List<String> path) {
        var container = node(root, path);
        var keys = new ArrayList<String>();
        if (container.isJsonArray()) {
            for (int i = 0; i < container.getAsJsonArray().size(); i++) keys.add(Integer.toString(i));
        } else {
            container.getAsJsonObject().entrySet().forEach(entry -> keys.add(entry.getKey()));
        }
        return List.copyOf(keys);
    }

    /** Adds a named object member, or appends an array element when the key is null. */
    public RecipeJsonTree add(List<String> path, String key, String value) {
        var added = RecipeImporter.parseValue(value);
        var copy = root.deepCopy();
        var container = node(copy, path);
        if (container.isJsonArray() && key == null) {
            container.getAsJsonArray().add(added);
        } else {
            var object = container.getAsJsonObject();
            Objects.requireNonNull(key);
            if (object.has(key)) throw new IllegalArgumentException("Duplicate field");
            object.add(key, added);
        }
        return parse(copy.toString());
    }

    public RecipeJsonTree replace(List<String> path, String value) {
        if (path.isEmpty()) return parse(value);
        return editExisting(path, RecipeImporter.parseValue(value));
    }

    public RecipeJsonTree remove(List<String> path) {
        if (path.isEmpty()) throw new IllegalArgumentException("Cannot remove recipe root");
        return editExisting(path, null);
    }

    private RecipeJsonTree editExisting(List<String> path, JsonElement replacement) {
        var copy = root.deepCopy();
        int last = path.size() - 1;
        var container = node(copy, path.subList(0, last));
        String key = path.get(last);
        if (container.isJsonArray()) {
            var array = container.getAsJsonArray();
            int index = Integer.parseInt(key);
            if (replacement == null) array.remove(index);
            else array.set(index, replacement);
        } else {
            var object = container.getAsJsonObject();
            if (!object.has(key)) throw new IllegalArgumentException("Unknown field");
            if (replacement == null) object.remove(key);
            else object.add(key, replacement);
        }
        // A subtree can fit by itself and still exceed the document's size or depth limit.
        return replacement == null ? new RecipeJsonTree(copy) : parse(copy.toString());
    }

    private static JsonElement node(JsonObject root, List<String> path) {
        JsonElement node = root;
        for (String key : path) {
            node = node.isJsonArray() ? node.getAsJsonArray().get(Integer.parseInt(key))
                    : node.getAsJsonObject().get(key);
            if (node == null) throw new IllegalArgumentException("Unknown field");
        }
        return node;
    }
}
