// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.recipe;

import com.dishanhai.sh_common_lib.api.ulk.recipe.BrewingRecipe;
import com.dishanhai.sh_common_lib.api.ulk.recipe.BrewingRecipes;
import java.util.List;

/** Live public registrations combine with a loaded server snapshot without restarting the server. */
public final class BrewingRuleCacheTest {
    public static void main(String[] args) {
        var cache = new BrewingRuleCache<String>(definition -> {
            if (definition.ingredient().equals("minecraft:invalid")) throw new IllegalStateException("failed to compile");
            return definition.id() + "=" + definition.ingredient();
        });
        BrewingRecipes.register(recipe("test:default", "minecraft:diamond"));
        BrewingRecipes.register(recipe("test:override", "minecraft:sugar"));
        check(cache.current().isEmpty(), "registrations wait for a server resource snapshot");
        var override = new BrewingDefinitions.Definition("test:override", BrewingDefinitions.Kind.POTION,
                "minecraft:water", "minecraft:nether_wart", "minecraft:awkward");
        cache.load(List.of(override));
        check(cache.current().equals(List.of("test:override=minecraft:nether_wart", "test:default=minecraft:diamond")),
                "loaded rules override program ID and program supplies defaults");
        BrewingRecipes.register(recipe("test:default", "minecraft:emerald"));
        check(cache.current().contains("test:default=minecraft:emerald"), "replacement is visible on next query");
        BrewingRecipes.remove("test:default");
        check(cache.current().equals(List.of("test:override=minecraft:nether_wart")), "removal is visible on next query");
        try {
            cache.load(List.of(new BrewingDefinitions.Definition("test:failed", BrewingDefinitions.Kind.POTION,
                    "minecraft:water", "minecraft:invalid", "minecraft:awkward")));
            throw new AssertionError("failed compiler did not propagate");
        } catch (IllegalStateException expected) {
            check(cache.current().equals(List.of("test:override=minecraft:nether_wart")), "failed rebuild retains previous snapshot");
        }
        cache.clear();
        check(cache.current().isEmpty(), "server stop clears resolved rules");
        check(BrewingRecipes.find("test:override").isPresent(), "server stop preserves program registrations");
        cache.load(List.of());
        check(cache.current().equals(List.of("test:override=minecraft:sugar")), "new server reload restores program defaults");
    }

    private static BrewingRecipe recipe(String id, String ingredient) {
        return new BrewingRecipe(id, BrewingRecipe.Kind.POTION, "minecraft:water", ingredient, "minecraft:awkward");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
