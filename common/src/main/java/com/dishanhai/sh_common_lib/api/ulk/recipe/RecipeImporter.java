// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import com.dishanhai.sh_common_lib.api.ulk.fumo.FumoRewardDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Imports only losslessly representable recipes into the visual editor. */
public final class RecipeImporter {
    private static final int MAX_INPUT = 1_048_576;

    private RecipeImporter() {}

    /** Keeps a foreign serializer's original source and target together when a visual import falls back. */
    public static RecipeDocument importForEditing(RecipeDocument current, String raw, RecipeTarget clientTarget) {
        RecipeTarget selected = current.editingTarget(clientTarget);
        var imported = fromJson(current.id(), raw, selected);
        if (imported.type() == RecipeType.CUSTOM) {
            RecipeTarget pinned = current.type() == RecipeType.GTCEU ? selected : current.customTarget();
            return imported.withCustomTarget(pinned);
        }
        if (selected != clientTarget && imported.type() != RecipeType.GTCEU && imported.type() != RecipeType.FUMO_REWARDS
                && imported.type() != RecipeType.BREWING && imported.type() != RecipeType.BREWING_CONTAINER) {
            // The vanilla canvas always uses the running client format. Retain foreign JSON verbatim.
            return custom(current.id(), raw).withCustomTarget(selected);
        }
        return imported;
    }

    /** Unknown fields and serializers remain a CUSTOM document containing the original JSON. */
    public static RecipeDocument fromJson(String recipeId, String raw, RecipeTarget target) {
        if (!RecipeGenerator.validId(recipeId)) throw new IllegalArgumentException("Invalid recipe ID");
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(target, "target");
        if (raw.length() > MAX_INPUT) throw new IllegalArgumentException("Recipe JSON is too large");
        JsonObject json = parse(raw);
        if (!json.has("type") && json.has("rewards")) return fumo(recipeId, json, target);
        if (!json.has("type")) return brewing(recipeId, raw, json, target);
        JsonElement typeValue = json.get("type");
        if (!string(typeValue) || !RecipeGenerator.validId(typeValue.getAsString())) {
            throw new IllegalArgumentException("Invalid recipe type");
        }
        RecipeType type = switch (typeValue.getAsString()) {
            case "minecraft:crafting_shaped" -> RecipeType.SHAPED;
            case "minecraft:crafting_shapeless" -> RecipeType.SHAPELESS;
            case "minecraft:smelting" -> RecipeType.SMELTING;
            case "minecraft:blasting" -> RecipeType.BLASTING;
            case "minecraft:smoking" -> RecipeType.SMOKING;
            case "minecraft:campfire_cooking" -> RecipeType.CAMPFIRE;
            case "minecraft:stonecutting" -> RecipeType.STONECUTTING;
            case "minecraft:smithing_transform" -> RecipeType.SMITHING_TRANSFORM;
            case "minecraft:smithing_trim" -> RecipeType.SMITHING_TRIM;
            default -> null;
        };
        if (type == null) {
            if (target == RecipeTarget.FORGE_1_20_1 && typeValue.getAsString().startsWith("gtceu:")) {
                try {
                    if (GtMachineRecipe.decode(json).validate().isEmpty()) {
                        return new RecipeDocument(recipeId, RecipeType.GTCEU, Collections.nCopies(9, ""), "", 1, 0, 200, "", raw);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Unknown mechanics and ingredients are kept intact in the generic editor.
                }
            }
            return custom(recipeId, raw);
        }
        try {
            var slots = new ArrayList<>(Collections.nCopies(9, ""));
            String result = "";
            int count = 1;
            float experience = 0;
            int cookingTime = 200;
            String group = optionalString(json, "group", "");
            switch (type) {
                case SHAPED -> {
                    fields(json, "type", "group", "pattern", "key", "result", "category", "show_notification");
                    shaped(json, target, slots);
                    var output = result(json.get("result"), target);
                    result = output.id(); count = output.count();
                }
                case SHAPELESS -> {
                    fields(json, "type", "group", "ingredients", "result", "category");
                    JsonArray ingredients = array(json.get("ingredients"));
                    if (ingredients.isEmpty() || ingredients.size() > 9) throw unsupported();
                    for (int i = 0; i < ingredients.size(); i++) slots.set(i, ingredient(ingredients.get(i), target));
                    var output = result(json.get("result"), target);
                    result = output.id(); count = output.count();
                }
                case SMELTING, BLASTING, SMOKING, CAMPFIRE -> {
                    fields(json, "type", "group", "ingredient", "result", "experience", "cookingtime", "category");
                    slots.set(0, ingredient(json.get("ingredient"), target));
                    if (target == RecipeTarget.FORGE_1_20_1) result = requiredString(json.get("result"));
                    else { var output = result(json.get("result"), target); result = output.id(); count = output.count(); }
                    experience = decimal(json.get("experience"), 0);
                    cookingTime = integer(json.get("cookingtime"), type == RecipeType.CAMPFIRE ? 600 : 200);
                }
                case STONECUTTING -> {
                    fields(json, "type", "group", "ingredient", "result", "count");
                    slots.set(0, ingredient(json.get("ingredient"), target));
                    if (target == RecipeTarget.FORGE_1_20_1) {
                        result = requiredString(json.get("result")); count = integer(json.get("count"), 1);
                    } else {
                        if (json.has("count")) throw unsupported();
                        var output = result(json.get("result"), target); result = output.id(); count = output.count();
                    }
                }
                case SMITHING_TRANSFORM, SMITHING_TRIM -> {
                    fields(json, "type", "template", "base", "addition", type == RecipeType.SMITHING_TRIM ? "pattern" : "result");
                    if (!group.isEmpty()) throw unsupported();
                    slots.set(0, ingredient(json.get("template"), target));
                    slots.set(1, ingredient(json.get("base"), target));
                    slots.set(2, ingredient(json.get("addition"), target));
                    if (type == RecipeType.SMITHING_TRANSFORM) {
                        var output = result(json.get("result"), target); result = output.id(); count = output.count();
                    } else if (target == RecipeTarget.NEOFORGE_26_1_2) result = requiredString(json.get("pattern"));
                    else if (json.has("pattern")) throw unsupported();
                }
                default -> throw unsupported();
            }
            var metadata = new JsonObject();
            if (json.has("category")) metadata.add("category", json.get("category"));
            if (json.has("show_notification")) metadata.add("show_notification", json.get("show_notification"));
            var doc = new RecipeDocument(recipeId, type, slots, result, count, experience, cookingTime, group,
                    metadata.size() == 0 ? "" : metadata.toString());
            return RecipeGenerator.validate(doc, target, RecipeFormat.JSON).isEmpty() ? doc : custom(recipeId, raw);
        } catch (UnsupportedOperationException exception) {
            return custom(recipeId, raw);
        }
    }

    private static RecipeDocument fumo(String recipeId, JsonObject json, RecipeTarget target) {
        try {
            fields(json, "rewards");
            for (var entry : array(json.get("rewards"))) fields(object(entry), "item", "weight");
            var doc = RecipeDocument.fumo(recipeId, FumoRewardDefinition.read(json));
            if (!RecipeGenerator.validate(doc, target, RecipeFormat.JSON).isEmpty())
                throw new IllegalArgumentException("Invalid Fumo reward pool");
            return doc;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Fumo reward pool", exception);
        }
    }

    private static RecipeDocument brewing(String recipeId, String raw, JsonObject json, RecipeTarget target) {
        if (!json.has("id") || !json.has("input") || !json.has("ingredient") || !json.has("output")) {
            throw new IllegalArgumentException("Invalid brewing configuration");
        }
        try {
            String id = requiredString(json.get("id"));
            if (!recipeId.equals(id)) throw new IllegalArgumentException("Brewing ID differs from requested ID");
            String kind = optionalString(json, "kind", "potion");
            RecipeType type = switch (kind) {
                case "potion" -> RecipeType.BREWING;
                case "container" -> RecipeType.BREWING_CONTAINER;
                default -> throw new IllegalArgumentException("Unknown brewing kind");
            };
            var slots = new ArrayList<>(Collections.nCopies(9, ""));
            slots.set(0, requiredString(json.get("input")));
            slots.set(1, requiredString(json.get("ingredient")));
            var doc = new RecipeDocument(recipeId, type, slots, requiredString(json.get("output")), 1, 0, 200, "", "");
            if (!RecipeGenerator.validate(doc, target, RecipeFormat.JSON).isEmpty()) {
                throw new IllegalArgumentException("Invalid brewing configuration");
            }
            fields(json, "id", "kind", "input", "ingredient", "output");
            return doc;
        } catch (UnsupportedOperationException exception) {
            throw new IllegalArgumentException("Invalid brewing configuration", exception);
        }
    }

    private static void shaped(JsonObject json, RecipeTarget target, List<String> slots) {
        JsonArray pattern = array(json.get("pattern"));
        if (pattern.isEmpty() || pattern.size() > 3) throw unsupported();
        JsonObject keys = object(json.get("key"));
        Map<Character, String> symbols = new HashMap<>();
        for (var entry : keys.entrySet()) {
            if (entry.getKey().length() != 1 || entry.getKey().charAt(0) == ' ') throw unsupported();
            symbols.put(entry.getKey().charAt(0), ingredient(entry.getValue(), target));
        }
        Set<Character> used = new HashSet<>();
        int width = -1;
        for (int y = 0; y < pattern.size(); y++) {
            String row = requiredString(pattern.get(y));
            if (row.isEmpty() || row.length() > 3 || (width >= 0 && row.length() != width)) throw unsupported();
            width = row.length();
            for (int x = 0; x < row.length(); x++) {
                char symbol = row.charAt(x);
                if (symbol == ' ') continue;
                String value = symbols.get(symbol);
                if (value == null) throw unsupported();
                used.add(symbol);
                slots.set(y * 3 + x, value);
            }
        }
        if (used.isEmpty() || used.size() != symbols.size()) throw unsupported();
    }

    private static String ingredient(JsonElement value, RecipeTarget target) {
        String result;
        if (target == RecipeTarget.NEOFORGE_26_1_2) result = requiredString(value);
        else {
            JsonObject object = object(value);
            if (object.size() != 1) throw unsupported();
            if (object.has("item")) result = requiredString(object.get("item"));
            else if (object.has("tag")) result = "#" + requiredString(object.get("tag"));
            else throw unsupported();
        }
        if (!RecipeGenerator.validId(result.startsWith("#") ? result.substring(1) : result)) throw unsupported();
        return result;
    }

    private record Result(String id, int count) {}
    private static Result result(JsonElement value, RecipeTarget target) {
        JsonObject object = object(value);
        String idField = target == RecipeTarget.FORGE_1_20_1 ? "item" : "id";
        fields(object, idField, "count");
        return new Result(requiredString(object.get(idField)), integer(object.get("count"), 1));
    }

    private static void fields(JsonObject object, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String key : object.keySet()) if (!names.contains(key)) throw unsupported();
    }

    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw unsupported();
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value) {
        if (value == null || !value.isJsonArray()) throw unsupported();
        return value.getAsJsonArray();
    }

    private static boolean string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static String requiredString(JsonElement value) {
        if (!string(value)) throw unsupported();
        return value.getAsString();
    }

    private static String optionalString(JsonObject object, String key, String fallback) {
        return object.has(key) ? requiredString(object.get(key)) : fallback;
    }

    private static int integer(JsonElement value, int fallback) {
        if (value == null) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw unsupported();
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException | NumberFormatException exception) { throw unsupported(); }
    }

    private static float decimal(JsonElement value, float fallback) {
        if (value == null) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw unsupported();
        try {
            float number = value.getAsFloat();
            if (!Float.isFinite(number) || value.getAsBigDecimal().compareTo(new BigDecimal(Float.toString(number))) != 0) {
                throw unsupported();
            }
            return number;
        } catch (NumberFormatException exception) { throw unsupported(); }
    }

    private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException("Unsupported recipe format"); }
    private static RecipeDocument custom(String id, String raw) {
        return new RecipeDocument(id, RecipeType.CUSTOM, Collections.nCopies(9, ""), "", 1, 0, 200, "", raw);
    }

    static JsonObject parse(String raw) {
        JsonElement value = parseValue(raw);
        if (!value.isJsonObject()) throw new IllegalArgumentException("Expected one JSON object");
        return value.getAsJsonObject();
    }

    static JsonElement parseValue(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.length() > MAX_INPUT) throw new IllegalArgumentException("Recipe JSON is too large");
        try (var reader = new JsonReader(new StringReader(raw))) {
            reader.setLenient(false);
            JsonElement value = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Expected one JSON value");
            return value;
        } catch (IOException | IllegalStateException | NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid recipe JSON", exception);
        }
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 64) throw new IllegalArgumentException("JSON nesting exceeds limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IllegalArgumentException("Duplicate JSON key " + name);
                    object.add(name, read(reader, depth + 1));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Invalid JSON token");
        };
    }
}
