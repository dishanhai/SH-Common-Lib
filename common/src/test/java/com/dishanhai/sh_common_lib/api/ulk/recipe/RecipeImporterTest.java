// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

public final class RecipeImporterTest {
    public static void main(String[] args) {
        for (var target : RecipeTarget.values()) {
            for (var type : RecipeType.values()) {
                if (type == RecipeType.CUSTOM || type == RecipeType.GTCEU) continue;
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
        gtMachineRoundTrip();
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
        editGenericFields();
    }

    private static void editGenericFields() {
        String original = "{\"type\":\"demo:press\",\"nested/key\":{\"\":[null,9007199254740993]},\"flag\":true}";
        var tree = RecipeJsonTree.parse(original);
        var array = List.of("nested/key", "");
        var result = tree.replace(List.of("type"), "\"demo:mill\"")
                .add(array, null, "{\"count\":2}")
                .replace(List.of("nested/key", "", "2", "count"), "4")
                .remove(List.of("flag"))
                .add(List.of(), "optional", "null");
        check(tree.json().equals(original), "cancel preserves the original tree");
        check(result.isArray(array) && result.keys(array).equals(List.of("0", "1", "2")), "array order and empty keys");
        check(result.value(List.of("nested/key", "", "1")).equals("9007199254740993"), "unrelated large integer retained");
        check(result.value(List.of("nested/key", "", "2", "count")).equals("4"), "edit nested object in array");
        check(result.remove(List.of("nested/key", "", "0")).keys(array).size() == 2, "array deletion");
        expectError(() -> tree.add(List.of(), "flag", "null"));
        expectError(() -> tree.replace(List.of("missing"), "0"));
        expectError(() -> tree.replace(List.of("flag"), "null true"));
        expectError(() -> tree.replace(List.of("flag"), "{\"x\":1,\"x\":2}"));
        expectError(() -> tree.replace(List.of("flag"), "[".repeat(64) + "0" + "]".repeat(64)));
        for (var target : RecipeTarget.values()) {
            var draft = RecipeImporter.fromJson("test:generic", result.json(), target);
            check(draft.type() == RecipeType.CUSTOM, "third party serializer remains generic");
            var generated = RecipeGenerator.generate(draft, target, RecipeFormat.JSON);
            check(RecipeJsonTree.parse(generated.content()).json().equals(result.json()), "generic export retains nulls and numeric precision");
        }
    }

    private static void gtMachineRoundTrip() {
        var forge = RecipeTarget.FORGE_1_20_1;
        String id = "example:mix/first";
        String raw = """
                {
                  "type": "gtceu:mixer", "duration": 120,
                  "inputs": {"item": [{"content": {"type": "gtceu:sized", "count": 3, "ingredient": {"tag": "forge:dusts/iron"}}},
                                       {"content": {"item": "minecraft:clay_ball"}, "chance": 10000, "maxChance": 10000, "tierChanceBoost": 0}],
                             "fluid": [{"content": {"amount": 250, "value": [{"fluid": "minecraft:water"}]}}]},
                  "outputs": {"item": [{"content": {"item": "minecraft:clay"}}],
                              "fluid": [{"content": {"amount": 50, "value": {"fluid": "minecraft:lava"}}}]},
                  "tickInputs": {"eu": [{"content": 9007199254740993}]},
                  "tickOutputs": {}, "inputChanceLogics": {}, "outputChanceLogics": {}
                }""";
        var doc = RecipeImporter.fromJson(id, raw, forge);
        check(doc.type() == RecipeType.GTCEU, "GT machine recipe recognized");
        var recipe = GtMachineRecipe.parse(raw);
        check(recipe.duration() == 120 && recipe.eut() == 9007199254740993L, "lossless GT energy and duration");
        check(recipe.itemInputs().equals(List.of(new GtMachineRecipe.Entry("#forge:dusts/iron", 3),
                new GtMachineRecipe.Entry("minecraft:clay_ball", 1))), "sized GT tag and item order");
        check(recipe.fluidInputs().equals(List.of(new GtMachineRecipe.Entry("minecraft:water", 250))),
                "GT fluid and amount");
        check(GtMachineRecipe.parse(recipe.toJson()).equals(recipe), "GT item/fluid composition round-trip");
        var editable = RecipeImporter.fromJson(id, recipe.toJson(), forge);
        var json = RecipeGenerator.generate(editable, forge, RecipeFormat.JSON);
        check(json.relativePath().equals("exports/data/example/recipes/mix/first.json"), "GT exact datapack ID");
        check(GtMachineRecipe.parse(json.content()).equals(recipe), "exported GT JSON preserves edited model");
        var script = RecipeGenerator.generate(doc, forge, RecipeFormat.KUBEJS);
        check(script.relativePath().equals("exports/kubejs/server_scripts/example/mix/first.js")
                && script.content().contains("ServerEvents.highPriorityData")
                && script.content().contains("example:recipes/mix/first.json")
                && script.content().contains("9007199254740993"), "GT KubeJS resource ID and 64-bit payload");
        check(RecipeGenerator.generate(doc, forge, RecipeFormat.JAVA).content().contains("data/example/recipes/mix/first.json"),
                "GT Java generator uses same resource path");
        var references = new ArrayList<String>();
        check(RecipeReferences.validate(doc, forge, (kind, name) -> {
            references.add(kind + ":" + name);
            return RecipeReferences.Presence.PRESENT;
        }).isEmpty(), "registered GT references accepted");
        check(references.equals(List.of("GT_MACHINE:gtceu:mixer", "ITEM_TAG:forge:dusts/iron",
                "ITEM:minecraft:clay_ball", "ITEM:minecraft:clay", "FLUID:minecraft:water", "FLUID:minecraft:lava")),
                "GT registry namespaces and input/output ordering");
        for (var target : List.of(RecipeTarget.NEOFORGE_1_21_1, RecipeTarget.NEOFORGE_26_1_2)) {
            check(RecipeGenerator.validate(doc, target, RecipeFormat.JSON).contains(new RecipeIssue("unsupported_target", "gt_target")),
                    "GT Forge format rejected for " + target);
            check(RecipeImporter.fromJson(id, raw, target).type() == RecipeType.CUSTOM, "GT generic fallback on " + target);
        }
        for (String advanced : List.of(raw.replace("\"chance\": 10000", "\"chance\": 7500"),
                raw.replace("\"duration\": 120", "\"duration\": 120, \"recipeConditions\": []"),
                raw.replace("\"amount\": 250", "\"amount\": 250, \"nbt\": \"{}\""),
                raw.replace("\"tickOutputs\": {}", "\"tickOutputs\": {\"eu\":[{\"content\":1}]}"),
                raw.replace("9007199254740993", "1e2147483647"))) {
            var preserved = RecipeImporter.fromJson(id, advanced, forge);
            check(preserved.type() == RecipeType.CUSTOM && preserved.rawJson().equals(advanced), "GT fallback retains full input");
        }
        var advanced = JsonParser.parseString(raw).getAsJsonObject();
        var metadata = new com.google.gson.JsonObject();
        metadata.addProperty("description", "quote \" slash \\ backtick ` expression ${missing} 海");
        advanced.add("data", metadata);
        var generic = RecipeImporter.fromJson(id, advanced.toString(), forge);
        var genericScript = RecipeGenerator.generate(generic, forge, RecipeFormat.KUBEJS);
        check(generic.type() == RecipeType.CUSTOM && genericScript.relativePath().equals(script.relativePath())
                && genericScript.content().contains("ServerEvents.highPriorityData")
                && genericScript.content().contains("example:recipes/mix/first.json")
                && genericScript.content().contains("9007199254740993"), "generic GT preserves script path and precision");
        check(genericScript.content().contains("quote \\\\\" slash \\\\\\\\ backtick \\` expression \\${missing} 海"),
                "generic GT preserves JSON quotes and escapes JavaScript interpolation");
        var fuel = new GtMachineRecipe("gtceu:gas_turbine", 40, -512, List.of(), List.of(), recipe.fluidInputs(), List.of());
        check(fuel.validate().isEmpty() && GtMachineRecipe.parse(fuel.toJson()).equals(fuel)
                && JsonParser.parseString(fuel.toJson()).getAsJsonObject().has("tickOutputs"), "GT generator produces EU/t");
        var incomplete = new GtMachineRecipe("", 0, Long.MIN_VALUE, List.of(), List.of(), List.of(), List.of());
        check(!incomplete.validate().isEmpty() && GtMachineRecipe.parse(incomplete.toJson()).equals(incomplete), "GT invalid draft round-trip");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void expectError(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid input error"); }
        catch (IllegalArgumentException expected) { }
    }
}
