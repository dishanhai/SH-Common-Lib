// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiWorkbench;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeExportSettings;
import com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeWorkspace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

final class RecipeExportSettingsScreen {
    private final Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
    private final RecipeWorkspace workspace = new RecipeWorkspace(gameDirectory.resolve(RecipeWorkspace.DEFAULT_DIRECTORY));
    private boolean enabled;
    private String directory;
    private Component status;

    private RecipeExportSettingsScreen() {
        var settings = RecipeExportSettings.DEFAULT;
        try { settings = workspace.loadExportSettings(); }
        catch (IOException failure) { status = text("read_failed", failure.getMessage()); }
        enabled = settings.customJsonExport();
        directory = settings.jsonExportRoot();
    }

    static void open(Screen parent, Supplier<? extends GuiEventListener> focus) {
        var settings = new RecipeExportSettingsScreen();
        UiWorkbench.popup(Component.translatable("screen.sh_common_lib.control_center.developer_settings"), parent, 520, 162)
                .onInit(screen -> {
                    screen.onCloseRequest(() -> {
                        Minecraft.getInstance().setScreen(parent);
                        if (parent != null) {
                            var widget = focus.get();
                            if (widget != null && parent.children().contains(widget)) parent.setFocused(widget);
                        }
                        return false;
                    });
                    settings.init(screen);
                }).onForeground(settings::draw).open();
    }

    private void init(UiScreen screen) {
        var area = area(screen);
        int x = area.x(), y = area.y(), width = area.width();
        var toggle = new UiToggleButton(new Bounds(x, y, width, 22), enabled, UiWorkbench.preferences(screen),
                UiStyle.DEFAULT, value -> text(value ? "custom_on" : "custom_off"), value -> {
                    enabled = value;
                    status = null;
                });
        screen.add(toggle.button());
        var field = screen.add(new UiTextField(Minecraft.getInstance().font, new Bounds(x, y + 41, width, 20),
                text("directory"), 4096, UiWorkbench.preferences(screen)));
        field.setValue(directory);
        field.setOnChange(value -> { directory = value; status = null; });
        int half = (width - 4) / 2;
        UiWorkbench.button(screen, x, y + 69, half, 22, text("save"), this::save);
        UiWorkbench.button(screen, x + half + 4, y + 69, width - half - 4, 22, CommonComponents.GUI_BACK, screen::onClose);
    }

    private void draw(UiScreen screen, UiFrame frame) {
        var area = area(screen);
        drawLine(frame, text("directory"), area.x(), area.y() + 27, area.width(), UiWorkbench.textColor());
        drawLine(frame, text("directory_hint"), area.x(), area.y() + 99, area.width(), UiWorkbench.secondaryTextColor());
        if (status != null) drawLine(frame, status, area.x(), area.y() + 114, area.width(), UiWorkbench.accentColor());
    }

    private static Bounds area(UiScreen screen) {
        var panel = UiWorkbench.popupBounds(screen, 520, 162);
        return new Bounds(panel.x() + 12, panel.y() + 26, panel.width() - 24, panel.height() - 26);
    }

    private static void drawLine(UiFrame frame, Component label, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        frame.drawScaledText(font, UiRichText.fit(font, label, width), x, y, color, 1);
    }

    private void save() {
        try {
            var settings = new RecipeExportSettings(enabled, directory);
            if (settings.customJsonExport()) settings.resolveRoot(gameDirectory);
            workspace.saveExportSettings(settings);
            status = text("saved");
        } catch (IllegalArgumentException invalid) {
            status = text("invalid_directory");
        } catch (IOException failure) {
            status = text("save_failed", failure.getMessage());
        }
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.sh_common_lib.recipe_editor.export_settings." + key, args);
    }
}
