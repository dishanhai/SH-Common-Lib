// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import com.dishanhai.sh_common_lib.api.ulk.fumo.FumoRewardDefinition;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Nine slots preserve grid positions; other layouts use the slots from index zero. */
public record RecipeDocument(String id, RecipeType type, List<String> inputs, String result, int count,
                             float experience, int cookingTime, String group, String rawJson,
                             RecipeTarget customTarget, List<FumoRewardDefinition.Entry> rewards) {
    private static final List<String> EMPTY_INPUTS = Collections.nCopies(9, "");
    private static final String DEFAULT_ID = "example:recipe";

    public RecipeDocument(String id, RecipeType type, List<String> inputs, String result, int count,
                          float experience, int cookingTime, String group, String rawJson) {
        this(id, type, inputs, result, count, experience, cookingTime, group, rawJson, null, List.of());
    }

    public RecipeDocument(String id, RecipeType type, List<String> inputs, String result, int count,
                          float experience, int cookingTime, String group, String rawJson, RecipeTarget customTarget) {
        this(id, type, inputs, result, count, experience, cookingTime, group, rawJson, customTarget, List.of());
    }

    public RecipeDocument {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        inputs = List.copyOf(inputs);
        if (inputs.size() != 9) throw new IllegalArgumentException("Exactly nine inputs required");
        Objects.requireNonNull(result);
        Objects.requireNonNull(group);
        Objects.requireNonNull(rawJson);
        rewards = List.copyOf(rewards);
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
        return new RecipeDocument(id, type, inputs, result, count, experience, cookingTime, group, rawJson, selected, rewards);
    }

    /** Updates the single output and may use its ID to fill an empty or placeholder recipe ID. */
    public RecipeDocument withEditedResult(String result, int count, boolean inferId) {
        boolean hasSingleOutput = switch (type) {
            case SMITHING_TRIM, CUSTOM, GTCEU, FUMO_REWARDS -> false;
            default -> true;
        };
        String recipeId = id;
        if (inferId && hasSingleOutput && (id.isBlank() || id.equals(DEFAULT_ID))
                && RecipeGenerator.validId(result) && !result.equals("minecraft:air")) {
            recipeId = result;
        }
        return new RecipeDocument(recipeId, type, inputs, result, count, experience, cookingTime, group, rawJson, customTarget, rewards);
    }

    public static RecipeDocument empty() {
        return new RecipeDocument(DEFAULT_ID, RecipeType.SHAPED,
                EMPTY_INPUTS, "", 1, 0, 200, "", "");
    }

    public static RecipeDocument fumo(String id, List<FumoRewardDefinition.Entry> rewards) {
        return new RecipeDocument(id, RecipeType.FUMO_REWARDS, EMPTY_INPUTS, "", 1, 0, 200, "", "", null, rewards);
    }
}
