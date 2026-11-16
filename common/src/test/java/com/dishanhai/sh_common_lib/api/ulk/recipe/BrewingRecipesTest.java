// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Contract checks for third-party registration and immutable concurrent snapshots. */
public final class BrewingRecipesTest {
    public static void main(String[] args) throws Exception {
        var first = new BrewingRecipe("test:first", BrewingRecipe.Kind.POTION,
                "minecraft:water", "minecraft:diamond", "minecraft:awkward");
        var replacement = new BrewingRecipe("test:first", BrewingRecipe.Kind.CONTAINER,
                "minecraft:potion", "#minecraft:flowers", "minecraft:splash_potion");
        long start = BrewingRecipes.snapshot().revision();
        check(BrewingRecipes.register(first).isEmpty(), "new ID has no previous registration");
        var snapshot = BrewingRecipes.snapshot();
        check(snapshot.revision() > start && snapshot.recipes().contains(first), "registered definition in snapshot");
        check(BrewingRecipes.register(first).orElseThrow().equals(first), "equal registration returns previous value");
        check(BrewingRecipes.snapshot().revision() == snapshot.revision(), "equal registration does not change revision");
        check(BrewingRecipes.register(replacement).orElseThrow().equals(first), "same ID replaces atomically");
        check(BrewingRecipes.find("test:first").orElseThrow().equals(replacement), "lookup finds replacement");
        check(snapshot.recipes().contains(first) && !snapshot.recipes().contains(replacement), "old snapshot remains immutable");
        try { snapshot.recipes().clear(); throw new AssertionError("snapshot list mutable"); }
        catch (UnsupportedOperationException expected) {}
        try {
            BrewingRecipes.register(new BrewingRecipe("test:first", BrewingRecipe.Kind.POTION,
                    "minecraft:water", "#../bad", "minecraft:awkward"));
            throw new AssertionError("invalid ingredient accepted");
        } catch (IllegalArgumentException expected) {
            check(BrewingRecipes.find("test:first").orElseThrow().equals(replacement), "invalid replacement keeps previous value");
        }
        expectInvalid("test:../escape", BrewingRecipe.Kind.POTION, "minecraft:water", "minecraft:diamond", "minecraft:awkward");
        expectInvalid("test:air", BrewingRecipe.Kind.CONTAINER, "minecraft:air", "minecraft:diamond", "minecraft:potion");
        expectInvalid("test:air", BrewingRecipe.Kind.POTION, "minecraft:water", "minecraft:air", "minecraft:awkward");
        expectInvalid("test:air", BrewingRecipe.Kind.CONTAINER, "minecraft:potion", "minecraft:diamond", "minecraft:air");

        var pool = Executors.newFixedThreadPool(4);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                final int index = worker;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 40; i++) {
                        var recipe = new BrewingRecipe("test:parallel/" + index + "/" + i,
                                BrewingRecipe.Kind.POTION, "minecraft:water", "minecraft:diamond", "minecraft:awkward");
                        BrewingRecipes.register(recipe);
                        var current = BrewingRecipes.snapshot();
                        check(new HashSet<>(current.recipes().stream().map(BrewingRecipe::id).toList()).size()
                                == current.recipes().size(), "snapshot contains unique IDs");
                    }
                }));
            }
            for (var future : futures) future.get();
        } finally {
            pool.shutdown();
            check(pool.awaitTermination(20, TimeUnit.SECONDS), "parallel registration timed out");
        }
        check(BrewingRecipes.snapshot().recipes().size() == 161, "all concurrent registrations retained");
        check(BrewingRecipes.remove("test:first").orElseThrow().equals(replacement), "remove returns old value");
        check(BrewingRecipes.remove("test:first").isEmpty(), "removing missing value is harmless");
    }

    private static void expectInvalid(String id, BrewingRecipe.Kind kind, String input, String ingredient, String output) {
        try { new BrewingRecipe(id, kind, input, ingredient, output); throw new AssertionError("invalid definition accepted"); }
        catch (IllegalArgumentException expected) {}
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
