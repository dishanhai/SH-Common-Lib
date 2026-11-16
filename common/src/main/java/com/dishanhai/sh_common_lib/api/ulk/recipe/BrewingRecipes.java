// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Thread-safe program registrations; the server resolves these when its brewing rules are queried. */
public final class BrewingRecipes {
    private static final Map<String, BrewingRecipe> ENTRIES = new TreeMap<>();
    private static volatile Snapshot snapshot = new Snapshot(0, List.of());

    private BrewingRecipes() {}

    /** A stable view of one registry revision, ordered by recipe ID. */
    public record Snapshot(long revision, List<BrewingRecipe> recipes) {
        public Snapshot { recipes = List.copyOf(recipes); }
    }

    /** Registers or atomically replaces an ID; equal registrations do not advance the revision. */
    public static synchronized Optional<BrewingRecipe> register(BrewingRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        BrewingRecipe previous = ENTRIES.put(recipe.id(), recipe);
        if (!recipe.equals(previous)) publish();
        return Optional.ofNullable(previous);
    }

    /** Removes only a program registration, leaving configuration and data-pack rules untouched. */
    public static synchronized Optional<BrewingRecipe> remove(String id) {
        Objects.requireNonNull(id, "id");
        BrewingRecipe previous = ENTRIES.remove(id);
        if (previous != null) publish();
        return Optional.ofNullable(previous);
    }

    public static synchronized Optional<BrewingRecipe> find(String id) {
        return Optional.ofNullable(ENTRIES.get(Objects.requireNonNull(id, "id")));
    }

    /** Previously returned snapshots never change, including when registrations change concurrently. */
    public static Snapshot snapshot() { return snapshot; }

    private static void publish() { snapshot = new Snapshot(snapshot.revision() + 1, List.copyOf(ENTRIES.values())); }
}
