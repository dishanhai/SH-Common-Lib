// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.internal.recipe.BrewingDefinitions;
import com.dishanhai.sh_common_lib.internal.recipe.BrewingDatapack;
import com.dishanhai.sh_common_lib.internal.recipe.BrewingRuleCache;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

final class NeoForgeRecipeBrewing implements IBrewingRecipe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final NeoForgeRecipeBrewing INSTANCE = new NeoForgeRecipeBrewing();
    private final BrewingRuleCache<Rule> rules = new BrewingRuleCache<>(NeoForgeRecipeBrewing::compile);

    private NeoForgeRecipeBrewing() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(NeoForgeRecipeBrewing::registerRecipes);
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) -> event.addListener(Identifier.parse("sh_common_lib:brewing"), new SimplePreparableReloadListener<List<BrewingDefinitions.Definition>>() {
            @Override protected List<BrewingDefinitions.Definition> prepare(ResourceManager manager, ProfilerFiller profiler) {
                Path folder = FMLPaths.CONFIGDIR.get().resolve("sh_common_lib/recipe_editor/brewing");
                return BrewingDatapack.loadWithConfig(manager, folder, message -> LOGGER.warn("Invalid brewing definition: {}", message));
            }
            @Override protected void apply(List<BrewingDefinitions.Definition> definitions, ResourceManager manager, ProfilerFiller profiler) {
                INSTANCE.rules.load(definitions);
            }
        }));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> INSTANCE.rules.clear());
    }

    private static void registerRecipes(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addRecipe(INSTANCE);
    }

    private static Rule compile(BrewingDefinitions.Definition definition) {
        try {
            String ingredientText = definition.ingredient();
            boolean tag = ingredientText.startsWith("#");
            Identifier ingredientId = Identifier.parse(tag ? ingredientText.substring(1) : ingredientText);
            Item item = tag ? null : BuiltInRegistries.ITEM.getOptional(ingredientId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown ingredient " + ingredientId));
            TagKey<Item> itemTag = tag ? TagKey.create(Registries.ITEM, ingredientId) : null;
            if (definition.kind() == BrewingDefinitions.Kind.CONTAINER) {
                Item input = BuiltInRegistries.ITEM.getOptional(Identifier.parse(definition.input()))
                        .orElseThrow(() -> new IllegalArgumentException("unknown input container " + definition.input()));
                Item output = BuiltInRegistries.ITEM.getOptional(Identifier.parse(definition.output()))
                        .orElseThrow(() -> new IllegalArgumentException("unknown output container " + definition.output()));
                if (!(input instanceof PotionItem) || !(output instanceof PotionItem)) {
                    throw new IllegalArgumentException("input and output must be potion containers");
                }
                return new ContainerRule(input, item, itemTag, output);
            } else {
                var input = BuiltInRegistries.POTION.get(Identifier.parse(definition.input()))
                        .orElseThrow(() -> new IllegalArgumentException("unknown input potion " + definition.input()));
                var output = BuiltInRegistries.POTION.get(Identifier.parse(definition.output()))
                        .orElseThrow(() -> new IllegalArgumentException("unknown output potion " + definition.output()));
                return new PotionRule(input, item, itemTag, output);
            }
        } catch (RuntimeException error) {
            LOGGER.warn("Invalid brewing definition {}: {}", definition.id(), error.getMessage());
            return null;
        }
    }

    @Override public boolean isInput(ItemStack input) {
        if (!isPotionContainer(input)) return false;
        return rules.current().stream().anyMatch(rule -> rule.isInput(input));
    }

    @Override public boolean isIngredient(ItemStack ingredient) {
        return !ingredient.isEmpty() && rules.current().stream().anyMatch(rule -> rule.matches(ingredient));
    }

    @Override public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (!isPotionContainer(input) || ingredient.isEmpty()) return ItemStack.EMPTY;
        for (Rule rule : rules.current()) {
            if (rule.isInput(input) && rule.matches(ingredient)) return rule.result(input);
        }
        return ItemStack.EMPTY;
    }

    private static boolean isPotionContainer(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PotionItem;
    }

    private interface Rule {
        Item ingredient();
        TagKey<Item> ingredientTag();
        boolean isInput(ItemStack stack);
        ItemStack result(ItemStack stack);

        default boolean matches(ItemStack stack) {
            return ingredientTag() == null ? stack.is(ingredient()) : stack.is(ingredientTag());
        }
    }

    private record PotionRule(Holder<Potion> input, Item ingredient, TagKey<Item> ingredientTag,
                              Holder<Potion> output) implements Rule {
        @Override public boolean isInput(ItemStack stack) {
            return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(input);
        }
        @Override public ItemStack result(ItemStack stack) { return PotionContents.createItemStack(stack.getItem(), output); }
    }

    private record ContainerRule(Item input, Item ingredient, TagKey<Item> ingredientTag, Item output) implements Rule {
        @Override public boolean isInput(ItemStack stack) { return stack.is(input); }
        @Override public ItemStack result(ItemStack stack) {
            return stack.transmuteCopy(output, 1);
        }
    }
}
