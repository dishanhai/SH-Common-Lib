// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeDocument;
import com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeIssue;
import com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.PotionItem;

/** Checks registry-backed references using the current client world and synchronized tags. */
public final class RecipeRegistryValidation {
    private RecipeRegistryValidation() { }

    public static List<RecipeIssue> validate(RecipeDocument doc) {
        Objects.requireNonNull(doc);
        if (doc.type() == RecipeType.CUSTOM) return List.of();
        var issues = new ArrayList<RecipeIssue>();
        int slots = switch (doc.type()) {
            case SHAPED, SHAPELESS -> 9;
            case SMITHING_TRANSFORM, SMITHING_TRIM -> 3;
            case BREWING, BREWING_CONTAINER -> 2;
            default -> 1;
        };
        for (int i = 0; i < slots; i++) {
            String value = doc.inputs().get(i);
            if (value.isEmpty()) continue;
            String field = "input" + i;
            if (i == 0 && doc.type() == RecipeType.BREWING) checkPotion(value, field, issues);
            else if (i == 0 && doc.type() == RecipeType.BREWING_CONTAINER) checkItem(value, field, true, issues);
            else checkIngredient(value, field, issues);
        }
        if (!doc.result().isEmpty()) {
            if (doc.type() == RecipeType.BREWING) checkPotion(doc.result(), "result", issues);
            else if (doc.type() == RecipeType.BREWING_CONTAINER) checkItem(doc.result(), "result", true, issues);
            else if (doc.type() == RecipeType.SMITHING_TRIM) checkPattern(doc.result(), issues);
            else checkItem(doc.result(), "result", false, issues);
        }
        return List.copyOf(issues);
    }

    private static void checkIngredient(String value, String field, List<RecipeIssue> issues) {
        if (!value.startsWith("#")) {
            checkItem(value, field, false, issues);
            return;
        }
        Identifier id = Identifier.tryParse(value.substring(1));
        if (id == null) return; // Structural validation reports invalid identifiers.
        var level = Minecraft.getInstance().level;
        if (level == null) {
            issues.add(new RecipeIssue("registry_unavailable", field));
            return;
        }
        var items = level.registryAccess().lookup(Registries.ITEM).orElse(BuiltInRegistries.ITEM);
        if (!items.getTagOrEmpty(TagKey.create(Registries.ITEM, id)).iterator().hasNext()) {
            issues.add(new RecipeIssue("empty_tag", field));
        }
    }

    private static void checkItem(String value, String field, boolean container, List<RecipeIssue> issues) {
        Identifier id = Identifier.tryParse(value);
        if (id == null || value.startsWith("#")) return;
        if (id.toString().equals("minecraft:air")) {
            issues.add(new RecipeIssue("unknown_item", field));
            return;
        }
        var item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty()) issues.add(new RecipeIssue("unknown_item", field));
        else if (container && !(item.get() instanceof PotionItem)) issues.add(new RecipeIssue("invalid_container", field));
    }

    private static void checkPotion(String value, String field, List<RecipeIssue> issues) {
        Identifier id = Identifier.tryParse(value);
        if (id == null || value.startsWith("#")) return;
        if (BuiltInRegistries.POTION.getOptional(id).isEmpty()) issues.add(new RecipeIssue("unknown_potion", field));
    }

    private static void checkPattern(String value, List<RecipeIssue> issues) {
        Identifier id = Identifier.tryParse(value);
        if (id == null || value.startsWith("#")) return;
        var level = Minecraft.getInstance().level;
        if (level == null) {
            issues.add(new RecipeIssue("registry_unavailable", "result"));
            return;
        }
        var registry = level.registryAccess().lookup(Registries.TRIM_PATTERN);
        if (registry.isEmpty()) issues.add(new RecipeIssue("registry_unavailable", "result"));
        else if (!registry.get().containsKey(id)) issues.add(new RecipeIssue("unknown_pattern", "result"));
    }
}
