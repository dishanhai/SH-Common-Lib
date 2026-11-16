// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stateless validation and version-specific recipe generation. */
public final class RecipeGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private RecipeGenerator() { }

    public static List<RecipeIssue> validate(RecipeDocument doc, RecipeTarget target, RecipeFormat format) {
        Objects.requireNonNull(doc);
        Objects.requireNonNull(target);
        Objects.requireNonNull(format);
        var issues = new ArrayList<RecipeIssue>();
        if (!validId(doc.id())) issues.add(new RecipeIssue("invalid_id", "id"));
        if (doc.type() == RecipeType.CUSTOM) {
            try {
                JsonElement value = RecipeImporter.parse(doc.rawJson());
                if (!value.isJsonObject() || !value.getAsJsonObject().has("type")
                        || !value.getAsJsonObject().get("type").isJsonPrimitive()
                        || !value.getAsJsonObject().get("type").getAsJsonPrimitive().isString()
                        || !validId(value.getAsJsonObject().get("type").getAsString())) {
                    issues.add(new RecipeIssue("invalid_json", "rawJson"));
                }
            } catch (RuntimeException exception) {
                issues.add(new RecipeIssue("invalid_json", "rawJson"));
            }
            return List.copyOf(issues);
        }
        if (!doc.rawJson().isEmpty()) {
            try { metadata(doc, target); }
            catch (RuntimeException exception) { issues.add(new RecipeIssue("invalid_json", "rawJson")); }
        }
        int required = switch (doc.type()) {
            case SHAPED, SHAPELESS -> 9;
            case SMITHING_TRANSFORM, SMITHING_TRIM -> 3;
            case BREWING, BREWING_CONTAINER -> 2;
            default -> 1;
        };
        int present = 0;
        for (int i = 0; i < required; i++) {
            String value = doc.inputs().get(i);
            if (value.isEmpty()) {
                if (required != 9) issues.add(new RecipeIssue("required", "input" + i));
            } else {
                present++;
                boolean tag = value.startsWith("#");
                boolean potionInput = doc.type() == RecipeType.BREWING && i == 0;
                if ((!tag && !potionInput && value.equals("minecraft:air"))
                        || (isBrewing(doc.type()) && i == 0 && tag)
                        || !validId(tag ? value.substring(1) : value)) {
                    issues.add(new RecipeIssue("invalid_ingredient", "input" + i));
                }
            }
        }
        for (int i = required; i < doc.inputs().size(); i++) {
            if (!doc.inputs().get(i).isEmpty()) issues.add(new RecipeIssue("unused_input", "input" + i));
        }
        if (required == 9 && present == 0) issues.add(new RecipeIssue("required", "inputs"));
        boolean itemResult = doc.type() != RecipeType.BREWING && doc.type() != RecipeType.SMITHING_TRIM;
        if ((doc.type() != RecipeType.SMITHING_TRIM || target == RecipeTarget.NEOFORGE_26_1_2)
                && (!validId(doc.result()) || (itemResult && doc.result().equals("minecraft:air")))) {
            issues.add(new RecipeIssue("invalid_result", "result"));
        }
        if (doc.count() < 1 || doc.count() > 99
                || (isBrewing(doc.type()) && doc.count() != 1)
                || (target == RecipeTarget.FORGE_1_20_1 && isCooking(doc.type()) && doc.count() != 1)) {
            issues.add(new RecipeIssue("invalid_count", "count"));
        }
        if (isCooking(doc.type())) {
            if (!Float.isFinite(doc.experience()) || doc.experience() < 0) issues.add(new RecipeIssue("invalid_experience", "experience"));
            if (doc.cookingTime() < 1) issues.add(new RecipeIssue("invalid_time", "cookingTime"));
        }
        return List.copyOf(issues);
    }

    public static RecipeArtifact generate(RecipeDocument doc, RecipeTarget target, RecipeFormat format) {
        List<RecipeIssue> issues = validate(doc, target, format);
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid recipe: " + issues);
        String[] id = doc.id().split(":", 2);
        JsonObject json = doc.type() == RecipeType.CUSTOM
                ? RecipeImporter.parse(doc.rawJson())
                : isBrewing(doc.type()) ? brewing(doc) : recipe(doc, target);
        String body = GSON.toJson(json) + "\n";
        String dataPath = isBrewing(doc.type())
                ? "config/sh_common_lib/recipe_editor/brewing/" + id[0] + "/" + id[1] + ".json"
                : "data/" + id[0] + "/" + (target == RecipeTarget.FORGE_1_20_1 ? "recipes/" : "recipe/") + id[1] + ".json";
        if (format == RecipeFormat.JSON) {
            String path = isBrewing(doc.type()) ? "brewing/" + id[0] + "/" + id[1] + ".json" : "exports/" + dataPath;
            return new RecipeArtifact(path, body);
        }
        if (format == RecipeFormat.KUBEJS) {
            if (isBrewing(doc.type())) {
                String script = "// SHCLIB brewing configuration; restart or reload the server after execution.\n"
                        + "JsonIO.write(" + GSON.toJson(dataPath) + ", " + GSON.toJson(json) + ")\n";
                return new RecipeArtifact("exports/kubejs/startup_scripts/" + id[0] + "/" + id[1] + ".js", script);
            }
            String script = "ServerEvents.recipes(event => {\n  event.custom(" + GSON.toJson(json)
                    + ").id(" + GSON.toJson(doc.id()) + ")\n})\n";
            return new RecipeArtifact("exports/kubejs/server_scripts/" + id[0] + "/" + id[1] + ".js", script);
        }
        String className = className(doc.id());
        var jsonStatements = new StringBuilder("        StringBuilder json = new StringBuilder();\n");
        for (int i = 0; i < body.length();) {
            int end = Math.min(i + 4096, body.length());
            if (end < body.length() && Character.isHighSurrogate(body.charAt(end - 1))) end--;
            jsonStatements.append("        json.append(").append(javaLiteral(body.substring(i, end))).append(");\n");
            i = end;
        }
        String source = "import java.io.IOException;\nimport java.nio.charset.StandardCharsets;\nimport java.nio.file.Files;\n"
                + "import java.nio.file.Path;\n\n/** Writes one generated "
                + (isBrewing(doc.type()) ? "SHCLIB brewing configuration into a game directory" : "recipe into a resources root") + ". */\n"
                + "public final class " + className + " {\n    private " + className + "() {}\n"
                + "    public static void save(Path root) throws IOException {\n"
                + "        Path target = root.resolve(" + GSON.toJson(dataPath) + ");\n"
                + "        Files.createDirectories(target.getParent());\n"
                + jsonStatements
                + "        Files.writeString(target, json.toString(), StandardCharsets.UTF_8);\n"
                + "    }\n}\n";
        return new RecipeArtifact("exports/java/" + id[0] + "/" + id[1] + "/" + className + ".java", source);
    }

    private static String className(String id) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "Recipe_" + java.util.HexFormat.of().formatHex(hash, 0, 8);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String javaLiteral(String value) {
        var out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\134");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32 || c == 127) {
                        out.append('\\').append((char) ('0' + (c >> 6)))
                                .append((char) ('0' + ((c >> 3) & 7))).append((char) ('0' + (c & 7)));
                    } else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private static JsonObject brewing(RecipeDocument doc) {
        var json = new JsonObject();
        json.addProperty("id", doc.id());
        if (doc.type() == RecipeType.BREWING_CONTAINER) json.addProperty("kind", "container");
        json.addProperty("input", doc.inputs().get(0));
        json.addProperty("ingredient", doc.inputs().get(1));
        json.addProperty("output", doc.result());
        return json;
    }

    private static JsonObject metadata(RecipeDocument doc, RecipeTarget target) {
        var object = RecipeImporter.parse(doc.rawJson());
        for (var entry : object.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (key.equals("category")) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid category");
                String category = value.getAsString();
                boolean crafting = doc.type() == RecipeType.SHAPED || doc.type() == RecipeType.SHAPELESS;
                if (crafting && !List.of("building", "redstone", "equipment", "misc").contains(category)) throw new IllegalArgumentException("Invalid crafting category");
                if (isCooking(doc.type()) && !List.of("food", "blocks", "misc").contains(category)) throw new IllegalArgumentException("Invalid cooking category");
                if (!crafting && !isCooking(doc.type())) throw new IllegalArgumentException("Category unsupported");
            } else if (key.equals("show_notification")) {
                if (doc.type() != RecipeType.SHAPED || target == RecipeTarget.NEOFORGE_26_1_2
                        || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException("Notification unsupported");
                }
            } else throw new IllegalArgumentException("Unknown recipe metadata");
        }
        return object;
    }

    private static JsonObject recipe(RecipeDocument doc, RecipeTarget target) {
        var json = new JsonObject();
        String type = switch (doc.type()) {
            case SHAPED -> "crafting_shaped";
            case SHAPELESS -> "crafting_shapeless";
            case CAMPFIRE -> "campfire_cooking";
            default -> doc.type().name().toLowerCase(java.util.Locale.ROOT);
        };
        json.addProperty("type", "minecraft:" + type);
        if (!doc.group().isEmpty() && doc.type() != RecipeType.SMITHING_TRANSFORM && doc.type() != RecipeType.SMITHING_TRIM) {
            json.addProperty("group", doc.group());
        }
        if (!doc.rawJson().isEmpty()) metadata(doc, target).entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue()));
        switch (doc.type()) {
            case SHAPED -> addShaped(json, doc, target);
            case SHAPELESS -> {
                var ingredients = new JsonArray();
                doc.inputs().stream().filter(value -> !value.isEmpty()).forEach(value -> ingredients.add(ingredient(value, target)));
                json.add("ingredients", ingredients);
                json.add("result", result(doc, target));
            }
            case SMELTING, BLASTING, SMOKING, CAMPFIRE -> {
                json.add("ingredient", ingredient(doc.inputs().get(0), target));
                if (target == RecipeTarget.FORGE_1_20_1) json.addProperty("result", doc.result());
                else json.add("result", result(doc, target));
                json.addProperty("experience", doc.experience());
                json.addProperty("cookingtime", doc.cookingTime());
            }
            case STONECUTTING -> {
                json.add("ingredient", ingredient(doc.inputs().get(0), target));
                if (target == RecipeTarget.FORGE_1_20_1) {
                    json.addProperty("result", doc.result());
                    json.addProperty("count", doc.count());
                } else json.add("result", result(doc, target));
            }
            case SMITHING_TRANSFORM, SMITHING_TRIM -> {
                json.add("template", ingredient(doc.inputs().get(0), target));
                json.add("base", ingredient(doc.inputs().get(1), target));
                json.add("addition", ingredient(doc.inputs().get(2), target));
                if (doc.type() == RecipeType.SMITHING_TRANSFORM) json.add("result", result(doc, target));
                else if (target == RecipeTarget.NEOFORGE_26_1_2) json.addProperty("pattern", doc.result());
            }
            default -> throw new IllegalArgumentException("No vanilla serializer: " + doc.type());
        }
        return json;
    }

    private static void addShaped(JsonObject json, RecipeDocument doc, RecipeTarget target) {
        int left = 3, right = -1, top = 3, bottom = -1;
        for (int i = 0; i < 9; i++) if (!doc.inputs().get(i).isEmpty()) {
            left = Math.min(left, i % 3); right = Math.max(right, i % 3);
            top = Math.min(top, i / 3); bottom = Math.max(bottom, i / 3);
        }
        Map<String, Character> keys = new LinkedHashMap<>();
        var pattern = new JsonArray();
        for (int y = top; y <= bottom; y++) {
            var row = new StringBuilder();
            for (int x = left; x <= right; x++) {
                String input = doc.inputs().get(y * 3 + x);
                row.append(input.isEmpty() ? ' ' : keys.computeIfAbsent(input, ignored -> (char) ('A' + keys.size())));
            }
            pattern.add(row.toString());
        }
        var key = new JsonObject();
        keys.forEach((input, symbol) -> key.add(symbol.toString(), ingredient(input, target)));
        json.add("pattern", pattern);
        json.add("key", key);
        json.add("result", result(doc, target));
    }

    private static JsonElement ingredient(String value, RecipeTarget target) {
        if (target == RecipeTarget.NEOFORGE_26_1_2) return new JsonPrimitive(value);
        var object = new JsonObject();
        object.addProperty(value.startsWith("#") ? "tag" : "item", value.startsWith("#") ? value.substring(1) : value);
        return object;
    }

    private static JsonObject result(RecipeDocument doc, RecipeTarget target) {
        var result = new JsonObject();
        result.addProperty(target == RecipeTarget.FORGE_1_20_1 ? "item" : "id", doc.result());
        result.addProperty("count", doc.count());
        return result;
    }

    private static boolean isCooking(RecipeType type) {
        return type == RecipeType.SMELTING || type == RecipeType.BLASTING || type == RecipeType.SMOKING || type == RecipeType.CAMPFIRE;
    }

    private static boolean isBrewing(RecipeType type) {
        return type == RecipeType.BREWING || type == RecipeType.BREWING_CONTAINER;
    }

    static boolean validId(String id) {
        if (id == null || !ID.matcher(id).matches()) return false;
        String[] parts = id.split(":", 2);
        if (parts[0].equals(".") || parts[0].equals("..")) return false;
        if (parts[1].startsWith("/") || parts[1].endsWith("/")) return false;
        for (String part : parts[1].split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        }
        return true;
    }
}
