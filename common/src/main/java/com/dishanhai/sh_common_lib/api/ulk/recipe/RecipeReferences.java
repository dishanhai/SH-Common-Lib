// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Registry-reference validation independent of Minecraft client classes and file I/O. */
public final class RecipeReferences {
    private RecipeReferences() {}

    /** UNAVAILABLE is not MISSING: an unloaded registry must not be reported as an unknown item. */
    public enum Presence { PRESENT, MISSING, UNAVAILABLE }
    public enum Kind { ITEM, POTION_CONTAINER, ITEM_TAG, POTION, TRIM_PATTERN, FLUID, GT_MACHINE }

    /** Called only with structurally valid, namespaced identifiers, without a leading '#'. */
    @FunctionalInterface
    public interface Lookup {
        Presence find(Kind kind, String id);
    }

    /**
     * Checks known recipe fields, not recipe execution or custom serializer payloads.
     * Combine with RecipeGenerator.validate for structural checks. CUSTOM documents are intentionally
     * not inspected. Lookup exceptions propagate to the caller, rather than being treated as absence.
     */
    public static List<RecipeIssue> validate(RecipeDocument doc, RecipeTarget target, Lookup lookup) {
        Objects.requireNonNull(doc, "doc");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(lookup, "lookup");
        if (doc.type() == RecipeType.CUSTOM) return List.of();
        var issues = new ArrayList<RecipeIssue>();
        if (doc.type() == RecipeType.GTCEU) {
            GtMachineRecipe recipe;
            try {
                recipe = GtMachineRecipe.parse(doc.rawJson());
            } catch (IllegalArgumentException ignored) {
                return List.of(); // The structural validator reports the unreadable document.
            }
            check(recipe.machine(), "gt_machine", Kind.GT_MACHINE, lookup, issues);
            for (var entry : recipe.itemInputs()) {
                check(entry.id(), "gt_item_inputs", entry.id().startsWith("#") ? Kind.ITEM_TAG : Kind.ITEM, lookup, issues);
            }
            for (var entry : recipe.itemOutputs()) check(entry.id(), "gt_item_outputs", Kind.ITEM, lookup, issues);
            for (var entry : recipe.fluidInputs()) check(entry.id(), "gt_fluid_inputs", Kind.FLUID, lookup, issues);
            for (var entry : recipe.fluidOutputs()) check(entry.id(), "gt_fluid_outputs", Kind.FLUID, lookup, issues);
            return List.copyOf(issues);
        }
        if (doc.type() == RecipeType.FUMO_REWARDS) {
            for (int i = 0; i < doc.rewards().size(); i++) {
                check(doc.rewards().get(i).item(), "reward." + (i + 1), Kind.ITEM, lookup, issues);
            }
            return List.copyOf(issues);
        }
        int slots = switch (doc.type()) {
            case SHAPED, SHAPELESS -> 9;
            case SMITHING_TRANSFORM, SMITHING_TRIM -> 3;
            case BREWING, BREWING_CONTAINER -> 2;
            default -> 1;
        };
        for (int i = 0; i < slots; i++) {
            String value = doc.inputs().get(i);
            Kind kind = i == 0 && doc.type() == RecipeType.BREWING ? Kind.POTION
                    : i == 0 && doc.type() == RecipeType.BREWING_CONTAINER ? Kind.POTION_CONTAINER
                    : value.startsWith("#") ? Kind.ITEM_TAG : Kind.ITEM;
            check(value, "input" + i, kind, lookup, issues);
        }
        if (doc.type() == RecipeType.SMITHING_TRIM) {
            if (target == RecipeTarget.NEOFORGE_26_1_2)
                check(doc.result(), "result", Kind.TRIM_PATTERN, lookup, issues);
        } else {
            Kind kind = doc.type() == RecipeType.BREWING ? Kind.POTION
                    : doc.type() == RecipeType.BREWING_CONTAINER ? Kind.POTION_CONTAINER : Kind.ITEM;
            check(doc.result(), "result", kind, lookup, issues);
        }
        return List.copyOf(issues);
    }

    private static void check(String value, String field, Kind kind, Lookup lookup, List<RecipeIssue> issues) {
        String id = kind == Kind.ITEM_TAG ? value.substring(1) : value;
        if (!RecipeGenerator.validId(id)) return; // Structural validation reports malformed fields.
        if (kind == Kind.ITEM || kind == Kind.POTION_CONTAINER) {
            Presence item = id.equals("minecraft:air") ? Presence.MISSING : require(lookup, Kind.ITEM, id);
            if (item != Presence.PRESENT) {
                issues.add(new RecipeIssue(item == Presence.UNAVAILABLE ? "registry_unavailable" : "unknown_item", field));
                return;
            }
            if (kind == Kind.ITEM) return;
        }
        Presence found = require(lookup, kind, id);
        if (found == Presence.PRESENT) return;
        String code = found == Presence.UNAVAILABLE ? "registry_unavailable" : switch (kind) {
            case ITEM -> "unknown_item";
            case POTION_CONTAINER -> "invalid_container";
            case ITEM_TAG -> "empty_tag";
            case POTION -> "unknown_potion";
            case TRIM_PATTERN -> "unknown_pattern";
            case FLUID -> "unknown_fluid";
            case GT_MACHINE -> "unknown_machine";
        };
        issues.add(new RecipeIssue(code, field));
    }

    private static Presence require(Lookup lookup, Kind kind, String id) {
        return Objects.requireNonNull(lookup.find(kind, id), "Lookup returned null");
    }

    /** Shared by the platform registry adapters without linking the optional GTCEu classes. */
    static boolean isGtMachine(Object registeredType) {
        for (Class<?> type = registeredType.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("com.gregtechceu.gtceu.api.recipe.GTRecipeType")) return true;
        }
        return false;
    }
}
