// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import com.google.gson.JsonParser;
import com.dishanhai.sh_common_lib.internal.recipe.BrewingDefinitions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;

public final class RecipeGeneratorTest {
    public static void main(String[] args) throws Exception {
        var shaped = new RecipeDocument("example:test", RecipeType.SHAPED,
                List.of("", "", "", "", "minecraft:stick", "", "", "#minecraft:planks", ""),
                "minecraft:torch", 4, 0, 200, "", "");
        for (var target : RecipeTarget.values()) {
            var artifact = RecipeGenerator.generate(shaped, target, RecipeFormat.JSON);
            var json = JsonParser.parseString(artifact.content()).getAsJsonObject();
            expect(json.getAsJsonArray("pattern").size() == 2, "pattern trimmed");
            expect(json.getAsJsonObject("key").size() == 2, "two keys");
            expect(json.getAsJsonObject("result").get("count").getAsInt() == 4, "result count");
            expect(artifact.relativePath().contains(target == RecipeTarget.FORGE_1_20_1 ? "/recipes/" : "/recipe/"), "recipe directory");
            var script = RecipeGenerator.generate(shaped, target, RecipeFormat.KUBEJS).content();
            expect(script.contains("ServerEvents.recipes") && script.contains("example:test"), "script and id");
            expect(RecipeGenerator.generate(shaped, target, RecipeFormat.JAVA).content().contains("class Recipe_"), "java class");
            var key = json.getAsJsonObject("key").get("A");
            expect(key.isJsonPrimitive() == (target == RecipeTarget.NEOFORGE_26_1_2), "ingredient schema by version");
            expect(json.getAsJsonObject("result").has(target == RecipeTarget.FORGE_1_20_1 ? "item" : "id"), "result field by version");
            var furnace = new RecipeDocument("example:cook", RecipeType.SMELTING,
                    List.of("minecraft:raw_iron", "", "", "", "", "", "", "", ""),
                    "minecraft:iron_ingot", 1, 0.7f, 200, "", "");
            var cooked = JsonParser.parseString(RecipeGenerator.generate(furnace, target, RecipeFormat.JSON).content()).getAsJsonObject();
            expect(cooked.get("result").isJsonPrimitive() == (target == RecipeTarget.FORGE_1_20_1), "cooking output schema");
            var trim = new RecipeDocument("example:trim", RecipeType.SMITHING_TRIM,
                    List.of("minecraft:sentry_armor_trim_smithing_template", "#minecraft:trimmable_armor", "#minecraft:trim_materials", "", "", "", "", "", ""),
                    "minecraft:sentry", 1, 0, 200, "", "");
            var trimmed = JsonParser.parseString(RecipeGenerator.generate(trim, target, RecipeFormat.JSON).content()).getAsJsonObject();
            expect(trimmed.has("pattern") == (target == RecipeTarget.NEOFORGE_26_1_2), "26.1 trim pattern");
        }
        var brew = new RecipeDocument("example:potion", RecipeType.BREWING,
                List.of("minecraft:water", "minecraft:nether_wart", "", "", "", "", "", "", ""),
                "minecraft:awkward", 1, 0, 200, "", "");
        var brewArtifact = RecipeGenerator.generate(brew, RecipeTarget.FORGE_1_20_1, RecipeFormat.JSON);
        expect(brewArtifact.relativePath().equals("brewing/example/potion.json"), "brewing destination");
        expect(JsonParser.parseString(brewArtifact.content()).getAsJsonObject().get("input").getAsString().equals("minecraft:water"), "brewing input");
        expect(RecipeGenerator.generate(brew, RecipeTarget.NEOFORGE_1_21_1, RecipeFormat.KUBEJS).content().contains("JsonIO.write"), "brewing startup script");
        var container = new RecipeDocument("example:splash", RecipeType.BREWING_CONTAINER,
                List.of("minecraft:potion", "minecraft:gunpowder", "", "", "", "", "", "", ""),
                "minecraft:splash_potion", 1, 0, 200, "", "");
        for (var target : RecipeTarget.values()) {
            var containerJson = JsonParser.parseString(RecipeGenerator.generate(container, target, RecipeFormat.JSON).content()).getAsJsonObject();
            expect(containerJson.get("kind").getAsString().equals("container"), "container kind");
            expect(containerJson.get("input").getAsString().equals("minecraft:potion"), "container input");
            expect(containerJson.get("output").getAsString().equals("minecraft:splash_potion"), "container output");
            expect(RecipeGenerator.generate(container, target, RecipeFormat.KUBEJS).content().contains("JsonIO.write"), "container startup script");
            expect(RecipeGenerator.generate(container, target, RecipeFormat.JAVA).content().contains("class Recipe_"), "container java");
        }
        var javaFile = RecipeGenerator.generate(shaped, RecipeTarget.NEOFORGE_1_21_1, RecipeFormat.JAVA);
        var another = new RecipeDocument("example:test2", RecipeType.SHAPED, shaped.inputs(),
                shaped.result(), shaped.count(), 0, 200, "海😀 \\u000a\nquoted\"", "");
        var javaFile2 = RecipeGenerator.generate(another, RecipeTarget.NEOFORGE_1_21_1, RecipeFormat.JAVA);
        Path javaDir = Files.createTempDirectory("generated-recipe-");
        Path source = javaDir.resolve(Path.of(javaFile.relativePath()).getFileName());
        Path source2 = javaDir.resolve(Path.of(javaFile2.relativePath()).getFileName());
        expect(!source.equals(source2), "distinct Java class names");
        Files.writeString(source, javaFile.content());
        Files.writeString(source2, javaFile2.content());
        expect(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-encoding", "UTF-8", "-d", javaDir.toString(), source.toString(), source2.toString()) == 0, "generated Java compiles together");
        try (var loader = new java.net.URLClassLoader(new java.net.URL[] { javaDir.toUri().toURL() })) {
            for (Path file : List.of(source, source2)) {
                String name = file.getFileName().toString();
                loader.loadClass(name.substring(0, name.length() - ".java".length())).getMethod("save", Path.class).invoke(null, javaDir);
            }
        }
        expect(Files.isRegularFile(javaDir.resolve("data/example/recipe/test.json")), "generated Java creates usable recipe");
        expect(Files.readString(javaDir.resolve("data/example/recipe/test2.json")).equals(
                RecipeGenerator.generate(another, RecipeTarget.NEOFORGE_1_21_1, RecipeFormat.JSON).content()), "generated Java preserves JSON escapes and Unicode");
        var bad = new RecipeDocument("../bad:id", RecipeType.SMELTING, shaped.inputs(), "minecraft:iron_ingot", 1, 0, 200, "", "");
        expect(!RecipeGenerator.validate(bad, RecipeTarget.FORGE_1_20_1, RecipeFormat.JSON).isEmpty(), "reject invalid id");
        var airInput = new RecipeDocument("example:air_input", RecipeType.SHAPELESS,
                List.of("minecraft:air", "", "", "", "", "", "", "", ""), "minecraft:stone", 1, 0, 200, "", "");
        var airOutput = new RecipeDocument("example:air_output", RecipeType.SHAPELESS,
                List.of("minecraft:stone", "", "", "", "", "", "", "", ""), "minecraft:air", 1, 0, 200, "", "");
        var airIngredient = new RecipeDocument("example:air_ingredient", RecipeType.BREWING,
                List.of("minecraft:water", "minecraft:air", "", "", "", "", "", "", ""), "minecraft:awkward", 1, 0, 200, "", "");
        for (var target : RecipeTarget.values()) {
            for (var invalid : List.of(airInput, airOutput, airIngredient)) {
                expect(!RecipeGenerator.validate(invalid, target, RecipeFormat.JSON).isEmpty(), "reject air item");
                try { RecipeGenerator.generate(invalid, target, RecipeFormat.JSON); throw new AssertionError("generated recipe containing air"); }
                catch (IllegalArgumentException expected) { }
            }
        }
        var airNamedPotion = new RecipeDocument("example:potion_air_id", RecipeType.BREWING,
                List.of("minecraft:air", "minecraft:stone", "", "", "", "", "", "", ""), "minecraft:air", 1, 0, 200, "", "");
        expect(RecipeGenerator.validate(airNamedPotion, RecipeTarget.FORGE_1_20_1, RecipeFormat.JSON).isEmpty(), "potion IDs are not item IDs");
        var airNamedPattern = new RecipeDocument("example:trim_air_id", RecipeType.SMITHING_TRIM,
                List.of("minecraft:stone", "minecraft:stone", "minecraft:stone", "", "", "", "", "", ""), "minecraft:air", 1, 0, 200, "", "");
        expect(RecipeGenerator.validate(airNamedPattern, RecipeTarget.NEOFORGE_26_1_2, RecipeFormat.JSON).isEmpty(), "pattern IDs are not item IDs");
        var invalidJson = new RecipeDocument("example:custom", RecipeType.CUSTOM, shaped.inputs(), "", 1, 0, 1, "", "{\"type\":\"example:recipe\",\"count\":NaN}");
        expect(!RecipeGenerator.validate(invalidJson, RecipeTarget.FORGE_1_20_1, RecipeFormat.JSON).isEmpty(), "reject non-JSON values");
        var custom = new RecipeDocument("example:custom", RecipeType.CUSTOM, shaped.inputs(), "", 1, 0, 1, "", "{\"type\":\"example:recipe\",\"extra\":{\"text\":\"海\"}}");
        var customJson = JsonParser.parseString(RecipeGenerator.generate(custom, RecipeTarget.NEOFORGE_26_1_2, RecipeFormat.JSON).content()).getAsJsonObject();
        expect(customJson.getAsJsonObject("extra").get("text").getAsString().equals("海"), "custom fields preserved");
        String nested = "{\"type\":\"example:recipe\",\"data\":" + "[".repeat(3000) + "0" + "]".repeat(3000) + "}";
        var deep = new RecipeDocument("example:deep", RecipeType.CUSTOM, shaped.inputs(), "", 1, 0, 1, "", nested);
        expect(!RecipeGenerator.validate(deep, RecipeTarget.FORGE_1_20_1, RecipeFormat.JSON).isEmpty(), "reject deeply nested custom JSON");
        var duplicate = new RecipeDocument("example:duplicate", RecipeType.CUSTOM, shaped.inputs(), "", 1, 0, 1, "", "{\"type\":\"example:x\",\"type\":\"example:y\"}");
        expect(!RecipeGenerator.validate(duplicate, RecipeTarget.FORGE_1_20_1, RecipeFormat.JSON).isEmpty(), "reject duplicate custom JSON keys");
        var dir = Files.createTempDirectory("recipe-workspace-");
        var workspace = new RecipeWorkspace(dir);
        workspace.save(shaped);
        try { workspace.save(shaped, false); throw new AssertionError("draft overwrite accepted"); }
        catch (java.nio.file.FileAlreadyExistsException expected) { }
        expect(workspace.load(shaped.id()).equals(shaped), "draft round trip");
        expect(workspace.list().equals(List.of(shaped.id())), "draft list");
        workspace.export(brewArtifact, false);
        var errors = new java.util.ArrayList<String>();
        var definitions = BrewingDefinitions.load(dir.resolve("brewing"), errors::add);
        expect(errors.isEmpty() && definitions.size() == 1
                && definitions.get(0).output().equals("minecraft:awkward"), "brewing bridge reads generated JSON");
        try { workspace.export(brewArtifact, false); throw new AssertionError("overwrite accepted"); }
        catch (java.nio.file.FileAlreadyExistsException expected) { }
        try { workspace.export(new RecipeArtifact("../outside", "x"), false); throw new AssertionError("traversal accepted"); }
        catch (IllegalArgumentException expected) { }
        deleteTree(javaDir);
        deleteTree(dir);
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteTree(Path path) throws Exception {
        try (var paths = Files.walk(path)) {
            for (Path entry : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(entry);
        }
    }
}
