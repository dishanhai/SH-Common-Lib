package com.dishanhai.sh_common_lib.compat.jei;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import com.dishanhai.sh_common_lib.client.loading.ModLoadingStatus;
import com.mojang.datafixers.util.Either;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRuntimeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Client-only entry point discovered by JEI on either loader. */
@JeiPlugin
public final class JeiShortcuts implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.tryParse(ShCommonLib.MOD_ID + ":shortcuts");
    private static final String MESSAGE = "message.sh_common_lib.jei.";
    private static final String HINT = "tooltip.sh_common_lib.jei.";
    private static final int MAX_CHAT_LENGTH = 256;
    private static final Pattern DISALLOWED_CHAT_CHARACTERS = Pattern.compile("[\\x00-\\x1F\\x7F\\u00A7]");
    private static volatile IJeiRuntime runtime;

    /** Client-thread access for the optional search enhancement. */
    public static IJeiRuntime runtime() { return runtime; }

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        ModLoadingStatus.jeiStage("jei_subtypes");
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        ModLoadingStatus.jeiStage("jei_ingredients");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        ModLoadingStatus.jeiStage("jei_categories");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ModLoadingStatus.jeiStage("jei_recipes");
    }

    @Override
    public void registerRuntime(IRuntimeRegistration registration) {
        ModLoadingStatus.jeiStage("jei_runtime");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime newRuntime) {
        runtime = newRuntime;
        if (ClientLoading.animation() || ClientLoading.profiling()) {
            var manager = newRuntime.getIngredientManager();
            long ingredients = manager.getRegisteredIngredientTypes().stream()
                    .mapToLong(type -> manager.getAllIngredients(type).size())
                    .sum();
            long categories = newRuntime.getRecipeManager().createRecipeCategoryLookup().includeHidden().get().count();
            ModLoadingStatus.jeiReady(ingredients, categories);
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        com.dishanhai.sh_common_lib.client.jei.JeiSearchAssistant.runtimeUnavailable();
        runtime = null;
        ModLoadingStatus.clearJei();
    }

    public static boolean keyPressed(Screen screen, int key, int modifiers, boolean ownsShortcuts,
                                     String dataKind, Function<Object, String> serializeData) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0 && !Screen.hasControlDown()) return false;
        Action action = switch (key) {
            case GLFW.GLFW_KEY_A -> Action.DATA;
            case GLFW.GLFW_KEY_C -> Action.NAME;
            case GLFW.GLFW_KEY_D -> Action.TAG;
            case GLFW.GLFW_KEY_X -> Action.ID;
            case GLFW.GLFW_KEY_L -> Action.CHAT;
            default -> null;
        };
        IJeiRuntime current = runtime;
        Minecraft client = Minecraft.getInstance();
        if (action == null || (!ownsShortcuts && action != Action.DATA) || current == null
                || client.player == null || isEditingText(screen)
                || current.getIngredientListOverlay().hasKeyboardFocus()) return false;

        return hovered(current, client, screen)
                .map(typed -> handle(client, current, typed, action, dataKind, serializeData)).orElse(false);
    }

    public static void appendTooltipHints(ItemStack tooltipStack, List<Either<FormattedText, TooltipComponent>> elements,
                                          boolean includeRecipes, String dataKind, Predicate<Object> supportsData,
                                          Predicate<Object> hasInfinityCellShortcut) {
        IJeiRuntime current = runtime;
        Minecraft client = Minecraft.getInstance();
        Screen screen = client.screen;
        if (current == null || client.player == null || screen == null || elements.isEmpty()) return;

        hovered(current, client, screen).ifPresent(typed -> {
            Object ingredient = typed.getIngredient();
            if (ingredient instanceof ItemStack stack ? !tooltipStack.is(stack.getItem()) : !tooltipStack.isEmpty()) return;
            if (includeRecipes || screen != current.getRecipesGui()) {
                addHint(elements, "copy_name_tag");
                addHint(elements, "copy_id_chat");
            }
            if (supportsData.test(ingredient)) addHint(elements, "copy_" + dataKind);
            if (hasInfinityCellShortcut.test(ingredient)) addHint(elements, "infinity_cell");
        });
    }

    private static void addHint(List<Either<FormattedText, TooltipComponent>> elements, String key) {
        Either<FormattedText, TooltipComponent> hint =
                Either.left(Component.translatable(HINT + key).withStyle(ChatFormatting.GRAY));
        if (!elements.contains(hint)) elements.add(hint);
    }

    private static Optional<ITypedIngredient<?>> hovered(IJeiRuntime current, Minecraft client, Screen screen) {
        double x = client.mouseHandler.xpos() * screen.width / client.getWindow().getScreenWidth();
        double y = client.mouseHandler.ypos() * screen.height / client.getWindow().getScreenHeight();
        return hovered(current, screen, x, y);
    }

    private static boolean isEditingText(Screen screen) {
        if (screen instanceof ChatScreen) return true;
        GuiEventListener focused = screen.getFocused();
        while (focused instanceof ContainerEventHandler container) {
            focused = container.getFocused();
        }
        return focused instanceof EditBox;
    }

    public static Optional<ITypedIngredient<?>> hovered(Screen screen, double x, double y) {
        IJeiRuntime current = runtime;
        return current == null ? Optional.empty() : hovered(current, screen, x, y);
    }

    private static Optional<ITypedIngredient<?>> hovered(IJeiRuntime current, Screen screen, double x, double y) {
        if (Minecraft.getInstance().screen != screen) return Optional.empty();
        return current.getIngredientListOverlay().getIngredientUnderMouse()
                .or(() -> current.getBookmarkOverlay().getIngredientUnderMouse())
                .or(() -> {
                    if (screen != current.getRecipesGui()) return Optional.empty();
                    return current.getIngredientManager().getRegisteredIngredientTypes().stream()
                            .<ITypedIngredient<?>>flatMap(type -> recipeIngredient(current, type).stream())
                            .findFirst();
                })
                .or(() -> current.getScreenHelper().getClickableIngredientUnderMouse(screen, x, y)
                        .<ITypedIngredient<?>>map(clickable -> clickable.getTypedIngredient()).findFirst())
                .or(() -> {
                    if (!(screen instanceof AbstractContainerScreen<?> container)) return Optional.empty();
                    Slot slot = container.getSlotUnderMouse();
                    if (slot == null || !slot.hasItem()) return Optional.empty();
                    return current.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, slot.getItem(), false)
                            .map(typed -> typed);
                });
    }

    private static <T> Optional<ITypedIngredient<T>> recipeIngredient(IJeiRuntime current, IIngredientType<T> type) {
        return current.getRecipesGui().getIngredientUnderMouse(type)
                .flatMap(value -> current.getIngredientManager().createTypedIngredient(type, value, false));
    }

    private static <T> boolean handle(Minecraft client, IJeiRuntime current, ITypedIngredient<T> typed,
                                      Action action, String dataKind, Function<Object, String> serializeData) {
        T value = typed.getIngredient();
        var helper = current.getIngredientManager().getIngredientHelper(typed.getType());
        String text = switch (action) {
            case DATA -> serializeData.apply(value);
            case NAME -> displayName(value, helper);
            case TAG -> firstTag(value, helper);
            case ID -> idText(helper.getResourceLocation(value));
            case CHAT -> {
                String name = displayName(value, helper);
                String id = idText(helper.getResourceLocation(value));
                yield sanitizeChat(name.isEmpty() ? id : id.isEmpty() ? name : "[" + name + "] " + id);
            }
        };
        if (text == null) return false;
        Component label = Component.translatable(MESSAGE + (action == Action.DATA ? dataKind : action.label));
        if (text.isBlank()) {
            client.player.displayClientMessage(Component.translatable(MESSAGE + "empty", label)
                    .withStyle(ChatFormatting.RED), true);
        } else if (action == Action.CHAT) {
            client.setScreen(new ChatScreen(text));
        } else {
            client.keyboardHandler.setClipboard(text);
            var feedback = action == Action.DATA
                    ? Component.translatable(MESSAGE + "data_copied", label)
                    : Component.translatable(MESSAGE + "copied", label,
                            Component.literal(text).withStyle(ChatFormatting.YELLOW));
            client.player.displayClientMessage(feedback.withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private static <T> String displayName(T value, IIngredientHelper<T> helper) {
        return value instanceof ItemStack stack ? stack.getHoverName().getString() : helper.getDisplayName(value);
    }

    private static <T> String firstTag(T value, IIngredientHelper<T> helper) {
        Stream<ResourceLocation> tags = value instanceof ItemStack stack
                ? stack.getTags().map(TagKey::location) : helper.getTagStream(value);
        return tags.map(id -> "#" + id).sorted().findFirst().orElse("");
    }

    private static String idText(ResourceLocation id) {
        return id == null ? "" : id.toString();
    }

    private static String sanitizeChat(String text) {
        String filtered = DISALLOWED_CHAT_CHARACTERS.matcher(text).replaceAll("");
        if (filtered.length() <= MAX_CHAT_LENGTH) return filtered;
        int end = Character.isHighSurrogate(filtered.charAt(MAX_CHAT_LENGTH - 1))
                ? MAX_CHAT_LENGTH - 1 : MAX_CHAT_LENGTH;
        return filtered.substring(0, end);
    }

    private enum Action {
        NAME("name"), TAG("tag"), ID("id"), CHAT("chat"), DATA(null);

        private final String label;

        Action(String label) {
            this.label = label;
        }
    }
}
