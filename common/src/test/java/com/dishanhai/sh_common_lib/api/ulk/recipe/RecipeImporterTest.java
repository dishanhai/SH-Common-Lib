// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

public final class RecipeImporterTest {
    public static void main(String[] args) {
        for (var target : RecipeTarget.values()) {
            for (var type : RecipeType.values()) {
                if (type == RecipeType.CUSTOM) continue;
                List<String> slots = new ArrayList<>(List.of("", "", "", "", "", "", "", "", ""));
                slots.set(0, type == RecipeType.BREWING ? "minecraft:water"
                        : type == RecipeType.BREWING_CONTAINER ? "minecraft:potion" : "minecraft:stone");
                if (type == RecipeType.SHAPED || type == RecipeType.SHAPELESS) slots.set(4, "#minecraft:planks");
                if (type == RecipeType.SMITHING_TRANSFORM || type == RecipeType.SMITHING_TRIM) {
                    slots.set(1, "#minecraft:trimmable_armor"); slots.set(2, "minecraft:iron_ingot");
                }
                if (type == RecipeType.BREWING || type == RecipeType.BREWING_CONTAINER) slots.set(1, "minecraft:gunpowder");
                String result = type == RecipeType.BREWING ? "minecraft:awkward"
                        : type == RecipeType.BREWING_CONTAINER ? "minecraft:splash_potion"
                        : type == RecipeType.SMITHING_TRIM && target == RecipeTarget.NEOFORGE_26_1_2
                        ? "minecraft:sentry" : "minecraft:diamond";
                var original = new RecipeDocument("example:round_trip", type, slots, result, 1, 0.5f, 200, "", "");
                String json = RecipeGenerator.generate(original, target, RecipeFormat.JSON).content();
                var imported = RecipeImporter.fromJson(original.id(), json, target);
                check(imported.type() == type, target + " " + type + " recognized");
                String regenerated = RecipeGenerator.generate(imported, target, RecipeFormat.JSON).content();
                check(JsonParser.parseString(json).equals(JsonParser.parseString(regenerated)), target + " " + type + " round trip");
            }
        }
        String extended = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:stone\"}],"
                + "\"result\":{\"item\":\"minecraft:diamond\",\"nbt\":{\"foo\":1}}}";
        var preserved = RecipeImporter.fromJson("example:custom", extended, RecipeTarget.FORGE_1_20_1);
        check(preserved.type() == RecipeType.CUSTOM && preserved.rawJson().equals(extended), "unknown fields preserved verbatim");
        String alternate = "{\"type\":\"minecraft:smelting\",\"ingredient\":[{\"item\":\"minecraft:iron_ore\"},{\"item\":\"minecraft:raw_iron\"}],\"result\":\"minecraft:iron_ingot\"}";
        check(RecipeImporter.fromJson("example:custom", alternate, RecipeTarget.FORGE_1_20_1).type() == RecipeType.CUSTOM,
                "alternative ingredient list remains custom");
        String extendedBrewing = "{\"id\":\"example:brew\",\"input\":\"minecraft:water\",\"ingredient\":\"minecraft:sugar\","
                + "\"output\":\"minecraft:awkward\",\"extension\":true}";
        expectError(() -> RecipeImporter.fromJson("example:brew", extendedBrewing, RecipeTarget.FORGE_1_20_1));
        String standard = "{\"type\":\"minecraft:crafting_shaped\",\"category\":\"equipment\",\"show_notification\":false,"
                + "\"pattern\":[\"A\"],\"key\":{\"A\":{\"item\":\"minecraft:stone\"}},\"result\":{\"item\":\"minecraft:diamond\"}}";
        var importedStandard = RecipeImporter.fromJson("example:standard", standard, RecipeTarget.FORGE_1_20_1);
        check(importedStandard.type() == RecipeType.SHAPED, "standard metadata remains visually editable");
        var standardOutput = JsonParser.parseString(RecipeGenerator.generate(importedStandard, RecipeTarget.FORGE_1_20_1, RecipeFormat.JSON).content()).getAsJsonObject();
        check(standardOutput.get("category").getAsString().equals("equipment") && !standardOutput.get("show_notification").getAsBoolean(), "metadata survives generation");
        String cooking = "{\"type\":\"minecraft:smelting\",\"category\":\"misc\",\"ingredient\":{\"item\":\"minecraft:iron_ore\"},\"result\":\"minecraft:iron_ingot\"}";
        var importedCooking = RecipeImporter.fromJson("example:cook", cooking, RecipeTarget.FORGE_1_20_1);
        check(importedCooking.type() == RecipeType.SMELTING, "cooking metadata remains editable");
        check(JsonParser.parseString(RecipeGenerator.generate(importedCooking, RecipeTarget.FORGE_1_20_1, RecipeFormat.JSON).content()).getAsJsonObject().get("category").getAsString().equals("misc"), "cooking metadata preserved");
        String modern = "{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"building\",\"ingredients\":[\"minecraft:stone\"],\"result\":{\"id\":\"minecraft:diamond\"}}";
        var importedModern = RecipeImporter.fromJson("example:modern", modern, RecipeTarget.NEOFORGE_26_1_2);
        check(importedModern.type() == RecipeType.SHAPELESS, "26.1 category remains editable");
        check(JsonParser.parseString(RecipeGenerator.generate(importedModern, RecipeTarget.NEOFORGE_26_1_2, RecipeFormat.JSON).content()).getAsJsonObject().get("category").getAsString().equals("building"), "26.1 category preserved");
        String wrongCategory = standard.replace("equipment", "food");
        check(RecipeImporter.fromJson("example:invalid", wrongCategory, RecipeTarget.FORGE_1_20_1).type() == RecipeType.CUSTOM, "unknown category not silently rewritten");
        String shaped = "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"Q \",\" Q\"],"
                + "\"key\":{\"Q\":{\"tag\":\"minecraft:planks\"}},\"result\":{\"item\":\"minecraft:stick\"}}";
        var shapedDocument = RecipeImporter.fromJson("example:shaped", shaped, RecipeTarget.FORGE_1_20_1);
        check(shapedDocument.type() == RecipeType.SHAPED && shapedDocument.inputs().get(0).equals("#minecraft:planks")
                && shapedDocument.inputs().get(4).equals("#minecraft:planks") && shapedDocument.inputs().get(1).isEmpty(),
                "arbitrary pattern symbols retain positions and tags");
        expectError(() -> RecipeImporter.fromJson("../bad:id", "{}", RecipeTarget.FORGE_1_20_1));
        expectError(() -> RecipeImporter.fromJson("example:bad", "{", RecipeTarget.FORGE_1_20_1));
        expectError(() -> RecipeImporter.fromJson("example:bad", "{\"input\":\"minecraft:water\"}", RecipeTarget.FORGE_1_20_1));
        expectError(() -> RecipeImporter.fromJson("example:bad", "{\"type\":\"minecraft:smelting\"} {}", RecipeTarget.FORGE_1_20_1));
        expectError(() -> RecipeImporter.fromJson("example:bad", "{\"type\":\"minecraft:smelting\",\"type\":\"minecraft:smoking\"}", RecipeTarget.FORGE_1_20_1));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void expectError(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid input error"); }
        catch (IllegalArgumentException expected) { }
    }
}
