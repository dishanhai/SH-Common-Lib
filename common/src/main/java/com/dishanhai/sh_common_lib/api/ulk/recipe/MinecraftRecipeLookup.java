// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import java.util.Objects;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.PotionItem;

/** Live registry adapter. Use on the owning server thread; create again after world/reload changes. */
public final class MinecraftRecipeLookup {
    private MinecraftRecipeLookup() {}

    /** Supplies authoritative registries, e.g. server.registryAccess(), without loading client classes. */
    public static RecipeReferences.Lookup of(RegistryAccess registries) {
        Objects.requireNonNull(registries, "registries");
        return lookup(registries);
    }

    /** Built-in items/potions only. Tags and trim patterns report UNAVAILABLE, not empty. */
    public static RecipeReferences.Lookup builtInOnly() { return lookup(null); }

    private static RecipeReferences.Lookup lookup(RegistryAccess registries) {
        return (kind, name) -> {
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id == null) throw new IllegalArgumentException("Invalid registry identifier: " + name);
            return switch (kind) {
                case FLUID -> registries == null ? presence(BuiltInRegistries.FLUID.getOptional(id).isPresent())
                        : registries.registry(Registries.FLUID).map(fluids -> presence(fluids.getOptional(id).isPresent()))
                                .orElse(RecipeReferences.Presence.UNAVAILABLE);
                case GT_MACHINE -> registries == null ? presence(BuiltInRegistries.RECIPE_TYPE.getOptional(id).filter(RecipeReferences::isGtMachine).isPresent())
                        : registries.registry(Registries.RECIPE_TYPE).map(types -> presence(types.getOptional(id).filter(RecipeReferences::isGtMachine).isPresent()))
                                .orElse(RecipeReferences.Presence.UNAVAILABLE);
                case ITEM -> registries == null ? presence(BuiltInRegistries.ITEM.getOptional(id).isPresent())
                        : registries.registry(Registries.ITEM).map(items -> presence(items.getOptional(id).isPresent()))
                                .orElse(RecipeReferences.Presence.UNAVAILABLE);
                case POTION_CONTAINER -> registries == null ? presence(BuiltInRegistries.ITEM.getOptional(id).filter(PotionItem.class::isInstance).isPresent())
                        : registries.registry(Registries.ITEM).map(items -> presence(items.getOptional(id).filter(PotionItem.class::isInstance).isPresent()))
                                .orElse(RecipeReferences.Presence.UNAVAILABLE);
                case POTION -> registries == null ? presence(BuiltInRegistries.POTION.getOptional(id).isPresent())
                        : registries.registry(Registries.POTION).map(potions -> presence(potions.getOptional(id).isPresent()))
                                .orElse(RecipeReferences.Presence.UNAVAILABLE);
                case ITEM_TAG -> registries == null ? RecipeReferences.Presence.UNAVAILABLE
                        : registries.registry(Registries.ITEM).map(items -> presence(
                                items.getTagOrEmpty(TagKey.create(Registries.ITEM, id)).iterator().hasNext()))
                                .orElse(RecipeReferences.Presence.UNAVAILABLE);
                case TRIM_PATTERN -> registries == null ? RecipeReferences.Presence.UNAVAILABLE
                        : registries.registry(Registries.TRIM_PATTERN).map(patterns -> presence(patterns.containsKey(id)))
                                .orElse(RecipeReferences.Presence.UNAVAILABLE);
            };
        };
    }

    private static RecipeReferences.Presence presence(boolean present) {
        return present ? RecipeReferences.Presence.PRESENT : RecipeReferences.Presence.MISSING;
    }
}
