// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecipeBatchTest {
    private static RecipeDocument recipe(String id) {
        return new RecipeDocument(id, RecipeType.SHAPELESS,
                List.of("minecraft:stone", "", "", "", "", "", "", "", ""),
                "minecraft:stone", 1, 0, 200, "", "");
    }
    public static void main(String[] args) throws Exception {
        for (var target : RecipeTarget.values()) for (var format : RecipeFormat.values()) {
            var first = recipe("example:a");
            var second = recipe("example:b");
            var result = RecipeBatch.prepare(List.of(first, second), target, format);
            expect(result.valid() && result.artifacts().equals(List.of(
                    RecipeGenerator.generate(first, target, format), RecipeGenerator.generate(second, target, format))), "ordered parity");
            try { result.artifacts().clear(); throw new AssertionError("mutable artifacts"); }
            catch (UnsupportedOperationException expected) { }
            var duplicate = RecipeBatch.prepare(List.of(first, first), target, format);
            expect(!duplicate.valid() && duplicate.artifacts().isEmpty(), "no partial duplicate outputs");
            expect(duplicate.issues().stream().anyMatch(i -> i.index() == 1 && i.issue().key().equals("duplicate_id")), "indexed duplicate");
            expect(duplicate.issues().stream().anyMatch(i -> i.issue().key().equals("duplicate_output")), "output collision diagnosed");
            var invalid = RecipeBatch.prepare(List.of(first, RecipeDocument.empty()), target, format);
            expect(!invalid.valid() && invalid.artifacts().isEmpty(), "no partial invalid outputs");
            var calls = new ArrayList<String>();
            var semantic = RecipeBatch.prepare(List.of(first, RecipeDocument.empty()), target, format, doc -> {
                calls.add(doc.id()); return List.of(new RecipeIssue("unknown_item", "result"));
            });
            expect(calls.equals(List.of(first.id())) && semantic.artifacts().isEmpty(), "only valid structure invokes validator");
            expect(RecipeBatch.prepare(List.of(), target, format).valid(), "empty batch");
        }
        try { RecipeBatch.prepare(Collections.nCopies(257, recipe("example:a")), RecipeTarget.FORGE_1_20_1, RecipeFormat.JSON); throw new AssertionError("unbounded batch"); }
        catch (IllegalArgumentException expected) { }
        try { RecipeBatch.prepare(List.of(recipe("example:a")), RecipeTarget.FORGE_1_20_1, RecipeFormat.JSON, d -> { throw new IllegalStateException("failed"); }); throw new AssertionError("validator swallowed"); }
        catch (IllegalStateException expected) { }
        var large = new RecipeDocument("example:large", RecipeType.CUSTOM,
                Collections.nCopies(9, ""), "", 1, 0, 200, "",
                "{\"type\":\"example:custom\",\"text\":\"" + "海".repeat(400_000) + "\"}");
        var oversized = RecipeBatch.prepare(List.of(large), RecipeTarget.NEOFORGE_1_21_1, RecipeFormat.JSON);
        expect(!oversized.valid() && oversized.artifacts().isEmpty()
                && oversized.issues().getFirst().issue().key().equals("artifact_too_large"), "UTF-8 byte limit, not char count");
        var dir = Files.createTempDirectory("recipe-query-");
        try {
            var workspace = new RecipeWorkspace(dir);
            verifyGenericTarget(new RecipeWorkspace(dir.resolve("edit-targets")));
            expect(workspace.query("", 0, 10).total() == 0, "missing directory");
            for (var id : List.of("z:z", "a:c", "a:a", "a:b")) workspace.save(recipe(id), false);
            var page = workspace.query("a:", 0, 2);
            expect(page.ids().equals(List.of("a:a", "a:b")) && page.total() == 3 && page.hasMore(), "sorted prefix page");
            expect(workspace.query("a:", 2, 2).ids().equals(List.of("a:c")), "last page");
            expect(!workspace.query("a:", Integer.MAX_VALUE, 2).hasMore(), "overflow offset");
            expect(workspace.query("missing:", 0, 2).total() == 0, "no match");
            try { workspace.query("", 0, 257); throw new AssertionError("unbounded page"); }
            catch (IllegalArgumentException expected) { }
            var external = Files.createDirectory(dir.resolve("external"));
            Files.writeString(external.resolve("escape.json"), "{}");
            // Windows symlinks require an OS privilege not granted to ordinary build users.
            if (!System.getProperty("os.name").startsWith("Windows")) {
                Files.createSymbolicLink(dir.resolve("drafts/link"), external);
                expect(workspace.query("", 0, 256).total() == 4, "no followed directory links");
            } else {
                System.out.println("Symlink fixture skipped on Windows (requires OS privilege)");
            }
            // Actual contents can be invalid: this API lists IDs, not their semantic validity.
            Files.writeString(dir.resolve("drafts/a/b.json"), "broken");
            expect(workspace.query("a:", 0, 256).total() == 3, "does not parse drafts");
            Files.createDirectories(dir.resolve("drafts/large"));
            for (int i=0; i<16384; i++) Files.createFile(dir.resolve("drafts/large/" + i));
            try { workspace.query("a:", 0, 1); throw new AssertionError("scan silently truncated"); }
            catch (java.io.IOException expected) { expect(expected.getMessage().contains("scan limit"), "explicit scan limit"); }
        } finally {
            try (var files = Files.walk(dir)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        var invalidRoot = Files.createTempFile("recipe-root-", ".tmp");
        try {
            try { new RecipeWorkspace(invalidRoot).query("", 0, 10); throw new AssertionError("non-directory hidden"); }
            catch (java.io.IOException expected) { }
        } finally { Files.delete(invalidRoot); }
        System.out.println("RecipeBatchTest passed");
    }
    private static void verifyGenericTarget(RecipeWorkspace workspace) throws Exception {
        var forge = RecipeTarget.FORGE_1_20_1;
        var client = RecipeTarget.NEOFORGE_1_21_1;
        String source = "{\"type\":\"gtceu:mixer\",\"futureAddon\":{\"value\":null}}";
        var machine = new RecipeDocument("example:mixed", RecipeType.GTCEU, RecipeDocument.empty().inputs(),
                "", 1, 0, 200, "", GtMachineRecipe.empty().toJson());
        var generic = RecipeImporter.importForEditing(machine, source, client);
        expect(generic.type() == RecipeType.CUSTOM && generic.rawJson().equals(source), "foreign fallback preserves source");
        expect(generic.customTarget() == forge && generic.editingTarget(client) == forge, "foreign fallback retains Forge");
        workspace.save(generic, false);
        var reopened = workspace.load(generic.id());
        expect(reopened.equals(generic), "pinned target persists in a draft");
        expect(RecipeGenerator.generate(reopened, reopened.editingTarget(client), RecipeFormat.JSON).relativePath()
                .equals("exports/data/example/recipes/mixed.json"), "reopened draft keeps Forge recipes directory");
        expect(RecipeBatch.prepare(List.of(reopened), client, RecipeFormat.JSON).issues().stream()
                .anyMatch(issue -> issue.issue().key().equals("target_mismatch")), "batch refuses the wrong game format");

        String nativeJson = "{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"minecraft:sand\"},\"result\":\"minecraft:glass\"}";
        var foreignVanilla = RecipeImporter.importForEditing(reopened, nativeJson, client);
        expect(foreignVanilla.type() == RecipeType.CUSTOM && foreignVanilla.rawJson().equals(nativeJson)
                && foreignVanilla.customTarget() == forge, "foreign vanilla import retains the selected schema");
        expect(RecipeImporter.importForEditing(RecipeDocument.empty(), nativeJson, forge).type() == RecipeType.SMELTING,
                "native import keeps the vanilla canvas");

        var automatic = generic.withCustomTarget(null);
        workspace.save(automatic);
        var legacy = workspace.load(automatic.id());
        expect(legacy.equals(automatic) && legacy.editingTarget(client) == client, "legacy draft without a target follows the client");
        expect(RecipeGenerator.generate(legacy, client, RecipeFormat.JSON).relativePath().contains("/recipe/"),
                "unpinned generics generate for the requested version");
    }

    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
