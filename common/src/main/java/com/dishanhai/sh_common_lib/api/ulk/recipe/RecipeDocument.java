// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import java.util.List;
import java.util.Objects;

/** Nine slots preserve grid positions; other layouts use the slots from index zero. */
public record RecipeDocument(String id, RecipeType type, List<String> inputs, String result, int count,
                             float experience, int cookingTime, String group, String rawJson, RecipeTarget customTarget) {
    /** An unpinned recipe uses the caller's target. Preserves the original constructor for existing users. */
    public RecipeDocument(String id, RecipeType type, List<String> inputs, String result, int count,
                          float experience, int cookingTime, String group, String rawJson) {
        this(id, type, inputs, result, count, experience, cookingTime, group, rawJson, null);
    }

    public RecipeDocument {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        inputs = List.copyOf(inputs);
        if (inputs.size() != 9) throw new IllegalArgumentException("Exactly nine inputs required");
        Objects.requireNonNull(result);
        Objects.requireNonNull(group);
        Objects.requireNonNull(rawJson);
        if (customTarget != null && type != RecipeType.CUSTOM)
            throw new IllegalArgumentException("Only generic recipes can pin a target");
    }

    /** Effective game format for editing, previewing and exporting this draft on the given client. */
    public RecipeTarget editingTarget(RecipeTarget clientTarget) {
        Objects.requireNonNull(clientTarget);
        if (type == RecipeType.GTCEU) return RecipeTarget.FORGE_1_20_1;
        return customTarget == null ? clientTarget : customTarget;
    }

    public RecipeDocument withCustomTarget(RecipeTarget selected) {
        return new RecipeDocument(id, type, inputs, result, count, experience, cookingTime, group, rawJson, selected);
    }

    public static RecipeDocument empty() {
        return new RecipeDocument("example:recipe", RecipeType.SHAPED,
                List.of("", "", "", "", "", "", "", "", ""), "", 1, 0, 200, "", "");
    }
}
