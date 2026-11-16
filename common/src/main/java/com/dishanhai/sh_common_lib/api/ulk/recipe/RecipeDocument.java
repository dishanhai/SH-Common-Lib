// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import java.util.List;
import java.util.Objects;

/** Nine slots preserve grid positions; other layouts use the slots from index zero. */
public record RecipeDocument(String id, RecipeType type, List<String> inputs, String result, int count,
                             float experience, int cookingTime, String group, String rawJson) {
    public RecipeDocument {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        inputs = List.copyOf(inputs);
        if (inputs.size() != 9) throw new IllegalArgumentException("Exactly nine inputs required");
        Objects.requireNonNull(result);
        Objects.requireNonNull(group);
        Objects.requireNonNull(rawJson);
    }

    public static RecipeDocument empty() {
        return new RecipeDocument("example:recipe", RecipeType.SHAPED,
                List.of("", "", "", "", "", "", "", "", ""), "", 1, 0, 200, "", "");
    }
}
