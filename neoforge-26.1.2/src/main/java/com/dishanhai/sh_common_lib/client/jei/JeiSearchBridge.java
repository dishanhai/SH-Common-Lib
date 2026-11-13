// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import com.dishanhai.sh_common_lib.compat.jei.JeiShortcuts;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Adapts JEI 29 data to the same client search and preview concepts as older UUK bridges. */
final class JeiSearchBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("sh_common_lib:jei_search");
    enum Direction { OBTAINING }
    private static Backend backend;
    private static long generation;

    static void clear() { backend = null; generation++; }

    static long revision() {
        var current = JeiShortcuts.runtime();
        if (backend == null ? current != null : backend.runtime != current) {
            backend = current == null ? null : new Backend(current);
            generation++;
        }
        return generation * 1_000_000_000L + (backend == null ? 0 : backend.revision.get());
    }

    static List<Ingredient> ingredients() {
        revision();
        if (backend == null) return List.of();
        var result = new ArrayList<Ingredient>();
        for (var type : backend.manager.getRegisteredIngredientTypes()) {
            try { collect(backend, type, result); }
            catch (RuntimeException error) { LOGGER.debug("Skipping JEI ingredient type that could not be indexed", error); }
        }
        return List.copyOf(result);
    }

    private static <T> void collect(Backend owner, IIngredientType<T> type, List<Ingredient> result) {
        var helper = owner.manager.getIngredientHelper(type);
        for (var typed : owner.manager.getAllTypedIngredients(type)) {
            try {
                if (!owner.visibility.isIngredientVisible(typed)) continue;
                T value = typed.getIngredient();
                Identifier id = helper.getIdentifier(value);
                String name = helper.getDisplayName(value);
                if (id == null || name == null || name.isBlank()) continue;
                String mod = owner.runtime.getJeiHelpers().getModIdHelper().getModNameForModId(helper.getDisplayModId(value));
                result.add(new Ingredient(owner, typed, generation + ":" + result.size(), name, id, mod == null ? "" : mod,
                        List.copyOf(owner.manager.getIngredientAliases(typed)), helper.getTagStream(value).toList()));
            } catch (RuntimeException error) {
                LOGGER.debug("Skipping JEI ingredient that could not be indexed", error);
            }
        }
    }

    static List<Recipe> recipes(Ingredient ingredient, Direction direction, int limit) {
        if (backend == null || ingredient.owner() != backend || !ingredient.visible() || limit <= 0) return List.of();
        var factory = backend.runtime.getJeiHelpers().getFocusFactory();
        List<IFocus<?>> focus = List.of(factory.createFocus(RecipeIngredientRole.OUTPUT, ingredient.typed()));
        var manager = backend.runtime.getRecipeManager();
        return manager.createRecipeCategoryLookup().limitFocus(focus).get()
                .flatMap(category -> recipes(backend, category, focus)).limit(limit).toList();
    }

    static boolean open(Ingredient ingredient) {
        if (backend == null || ingredient.owner() != backend || !ingredient.visible()) return false;
        var focus = backend.runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, ingredient.typed());
        if (backend.runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(focus)).get().findAny().isEmpty()) return false;
        backend.runtime.getRecipesGui().show(focus);
        return true;
    }

    private static <T> Stream<Recipe> recipes(Backend owner, IRecipeCategory<T> category, List<IFocus<?>> focus) {
        var manager = owner.runtime.getRecipeManager();
        return manager.createRecipeLookup(category.getRecipeType()).limitFocus(focus).get().map(recipe ->
                new Recipe(category.getTitle(), () -> {
                    var group = owner.runtime.getJeiHelpers().getFocusFactory().createFocusGroup(focus);
                    return manager.createRecipeLayoutDrawable(category, recipe, group).map(RecipeLayout::new);
                }));
    }

    record Ingredient(Backend owner, ITypedIngredient<?> typed, String key, String name, Identifier id,
                      String modName, List<String> aliases, List<Identifier> tags) {
        boolean visible() { return owner == backend && owner.visibility.isIngredientVisible(typed); }
    }

    record Recipe(Component title, Supplier<Optional<RecipeLayout>> createLayout) {
        Optional<RecipeLayout> layout() { return createLayout.get(); }
    }

    record RecipeLayout(IRecipeLayoutDrawable<?> drawable) {
        int width() { return drawable.getRect().getWidth(); }
        int height() { return drawable.getRect().getHeight(); }
        int outerWidth() { return drawable.getRectWithBorder().getWidth(); }
        int outerHeight() { return drawable.getRectWithBorder().getHeight(); }
        int contentOffsetX() { return drawable.getRect().getX() - drawable.getRectWithBorder().getX(); }
        int contentOffsetY() { return drawable.getRect().getY() - drawable.getRectWithBorder().getY(); }
        void render(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
            drawable.setPosition(x, y);
            drawable.drawRecipe(graphics, mouseX, mouseY);
        }
        void renderOverlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (drawable.isMouseOver(mouseX, mouseY)) drawable.drawOverlays(graphics, mouseX, mouseY);
        }
        void tick() { drawable.tick(); }
    }

    private static final class Backend implements IIngredientManager.IIngredientListener, IIngredientVisibility.IListener {
        private final IJeiRuntime runtime;
        private final IIngredientManager manager;
        private final IIngredientVisibility visibility;
        private final AtomicLong revision = new AtomicLong();
        private Backend(IJeiRuntime runtime) {
            this.runtime = runtime;
            manager = runtime.getIngredientManager();
            visibility = runtime.getJeiHelpers().getIngredientVisibility();
            manager.registerIngredientListener(this);
            visibility.registerListener(this);
        }
        @Override public <T> void onIngredientsAdded(IIngredientHelper<T> helper, Collection<ITypedIngredient<T>> added) { revision.incrementAndGet(); }
        @Override public <T> void onIngredientsRemoved(IIngredientHelper<T> helper, Collection<ITypedIngredient<T>> removed) { revision.incrementAndGet(); }
        @Override public <T> void onIngredientVisibilityChanged(ITypedIngredient<T> ingredient, boolean visible) { revision.incrementAndGet(); }
    }
}
