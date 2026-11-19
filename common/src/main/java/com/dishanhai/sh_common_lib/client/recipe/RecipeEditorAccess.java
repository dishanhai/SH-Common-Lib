// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;

/** Keeps the rest of the control center usable with an older SHBUI installation. */
public final class RecipeEditorAccess {
    private RecipeEditorAccess() {}
    public static Component title() { return Component.translatable("screen.sh_common_lib.recipe_editor.title"); }
    public static Component unavailable() {
        try {
            var loader = RecipeEditorAccess.class.getClassLoader();
            var screen = Class.forName("com.dishanhai.sh_better_ui.client.api.UiScreen", false, loader);
            screen.getMethod("onCloseRequest", java.util.function.BooleanSupplier.class);
            var workbench = Class.forName("com.dishanhai.sh_better_ui.client.api.UiWorkbench", false, loader);
            workbench.getMethod("textColor");
            workbench.getMethod("selectItemSequence", screen,
                            Component.class, Supplier.class, int.class, java.util.function.Consumer.class);
            String scaled = "com.dishanhai.sh_better_ui.client.gui.scaled.";
            Class.forName("com.dishanhai.sh_better_ui.client.api.ControlsApi", false, loader)
                    .getMethod("navigationRail", net.minecraft.client.gui.Font.class,
                            Class.forName("com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout$Bounds", false, loader),
                            Component.class, java.util.List.class, int.class,
                            Class.forName(scaled + "UiNavigationRail$Orientation", false, loader),
                            Class.forName(scaled + "UiPreferences", false, loader), java.util.function.IntConsumer.class);
            Class.forName(scaled + "UiTextField", false, loader)
                    .getMethod("setAppearance", Class.forName(scaled + "UiTextField$Appearance", false, loader));
            Class.forName("com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots", false, loader)
                    .getMethod("setAllowDrag", boolean.class);
            Class.forName("com.dishanhai.sh_better_ui.client.gui.scaled.UiCodeEditor", false, loader);
            Class.forName("com.dishanhai.sh_better_ui.client.gui.scaled.UiResourcePicker", false, loader);
            Class.forName("com.dishanhai.sh_better_ui.client.gui.text.UiRichText", false, loader)
                    .getMethod("fit", net.minecraft.client.gui.Font.class, Component.class, int.class);
            Class.forName("com.dishanhai.sh_better_ui.client.gui.text.UiRichText", false, loader)
                    .getMethod("withItem", String.class, Component.class);
            Class.forName("com.dishanhai.sh_better_ui.client.gui.scaled.UiButton", false, loader)
                    .getMethod("setCornerRadius", float.class);
            Class.forName("com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners", false, loader);
            return null;
        } catch (ReflectiveOperationException | LinkageError missing) {
            return Component.translatable("screen.sh_common_lib.recipe_editor.requires_uuk");
        }
    }
    public static void open(Screen parent, Supplier<? extends GuiEventListener> focus) {
        if (unavailable() == null) RecipeEditor.open(parent, focus);
    }
}
