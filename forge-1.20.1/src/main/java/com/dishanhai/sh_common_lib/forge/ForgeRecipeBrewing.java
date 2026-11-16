// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.internal.recipe.BrewingDefinitions;
import com.dishanhai.sh_common_lib.internal.recipe.BrewingDatapack;
import com.dishanhai.sh_common_lib.internal.recipe.BrewingRuleCache;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

final class ForgeRecipeBrewing implements IBrewingRecipe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final BrewingRuleCache<Rule> rules = new BrewingRuleCache<>(ForgeRecipeBrewing::compile);

    static void register(IEventBus modBus) {
        var recipes = new ForgeRecipeBrewing();
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> BrewingRecipeRegistry.addRecipe(recipes)));
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new SimplePreparableReloadListener<List<BrewingDefinitions.Definition>>() {
            @Override protected List<BrewingDefinitions.Definition> prepare(ResourceManager manager, ProfilerFiller profiler) {
                Path folder = FMLPaths.CONFIGDIR.get().resolve("sh_common_lib/recipe_editor/brewing");
                return BrewingDatapack.loadWithConfig(manager, folder, message -> LOGGER.warn("Invalid brewing definition: {}", message));
            }
            @Override protected void apply(List<BrewingDefinitions.Definition> definitions, ResourceManager manager, ProfilerFiller profiler) {
                recipes.rules.load(definitions);
            }
        }));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> recipes.rules.clear());
    }

    private static Rule compile(BrewingDefinitions.Definition definition) {
        try {
            ResourceLocation inputId = ResourceLocation.tryParse(definition.input());
            ResourceLocation outputId = ResourceLocation.tryParse(definition.output());
            String ingredientText = definition.ingredient();
            boolean tag = ingredientText.startsWith("#");
            ResourceLocation ingredientId = ResourceLocation.tryParse(tag ? ingredientText.substring(1) : ingredientText);
            Item item = tag ? null : BuiltInRegistries.ITEM.getOptional(ingredientId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown ingredient " + ingredientId));
            TagKey<Item> itemTag = tag ? TagKey.create(Registries.ITEM, ingredientId) : null;
            if (definition.kind() == BrewingDefinitions.Kind.CONTAINER) {
                Item input = BuiltInRegistries.ITEM.getOptional(inputId)
                        .orElseThrow(() -> new IllegalArgumentException("unknown input container " + inputId));
                Item output = BuiltInRegistries.ITEM.getOptional(outputId)
                        .orElseThrow(() -> new IllegalArgumentException("unknown output container " + outputId));
                if (!(input instanceof PotionItem) || !(output instanceof PotionItem)) {
                    throw new IllegalArgumentException("input and output must be potion containers");
                }
                return new ContainerRule(input, item, itemTag, output);
            } else {
                Potion input = BuiltInRegistries.POTION.getOptional(inputId)
                        .orElseThrow(() -> new IllegalArgumentException("unknown input potion " + inputId));
                Potion output = BuiltInRegistries.POTION.getOptional(outputId)
                        .orElseThrow(() -> new IllegalArgumentException("unknown output potion " + outputId));
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

    private record PotionRule(Potion input, Item ingredient, TagKey<Item> ingredientTag, Potion output) implements Rule {
        @Override public boolean isInput(ItemStack stack) { return PotionUtils.getPotion(stack) == input; }
        @Override public ItemStack result(ItemStack stack) { return PotionUtils.setPotion(new ItemStack(stack.getItem()), output); }
    }

    private record ContainerRule(Item input, Item ingredient, TagKey<Item> ingredientTag, Item output) implements Rule {
        @Override public boolean isInput(ItemStack stack) { return stack.is(input); }
        @Override public ItemStack result(ItemStack stack) {
            ItemStack result = new ItemStack(output);
            if (stack.hasTag()) result.setTag(stack.getTag().copy());
            return result;
        }
    }
}
