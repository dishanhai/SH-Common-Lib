// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import java.util.Objects;

/** Loader-independent brewing definition. Potion recipes use potion IDs; container recipes use item IDs. */
public record BrewingRecipe(String id, Kind kind, String input, String ingredient, String output) {
    public enum Kind { POTION, CONTAINER }

    public BrewingRecipe {
        Objects.requireNonNull(kind, "kind");
        requireId(id, "id");
        requireId(input, "input");
        requireId(output, "output");
        if (ingredient == null) throw new IllegalArgumentException("Invalid ingredient: null");
        boolean tag = ingredient.startsWith("#");
        requireId(tag ? ingredient.substring(1) : ingredient, "ingredient");
        if (!tag && ingredient.equals("minecraft:air")) throw new IllegalArgumentException("Ingredient cannot be air");
        if (kind == Kind.CONTAINER && (input.equals("minecraft:air") || output.equals("minecraft:air"))) {
            throw new IllegalArgumentException("Potion containers cannot be air");
        }
    }

    private static void requireId(String value, String name) {
        if (!RecipeGenerator.validId(value)) throw new IllegalArgumentException("Invalid " + name + ": " + value);
    }
}
