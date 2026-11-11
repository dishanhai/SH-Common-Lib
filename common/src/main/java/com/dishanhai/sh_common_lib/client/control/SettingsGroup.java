// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.dishanhai.sh_common_lib.client.control.ControlCenterModule.label;

/** Built-in control center settings, declared in display order by SHCLIB. */
final class SettingsGroup {
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_GAP = 6;
    private static final Component SAVED = label("saved");
    private static final Component SAVE_FAILED = label("save_failed");

    sealed interface Entry permits Toggle, Page {}

    record Toggle(Function<Boolean, Component> label, BooleanSupplier read, Predicate<Boolean> save)
            implements Entry {}

    record Page(Component label, GraphicIcon icon, Supplier<Component> unavailable,
                BiConsumer<Screen, Supplier<? extends GuiEventListener>> open) implements Entry {}

    private final UiSectionBar.Section section;
    private final List<Entry> entries;
    private final Map<Entry, UiButton> buttons = new IdentityHashMap<>();
    private final Map<Toggle, UiToggleButton> toggleControls = new IdentityHashMap<>();
    private Component status;
    private Component unavailable;
    private Bounds area;
    private int messageY;

    SettingsGroup(Component title, GraphicIcon icon, List<Entry> entries) {
        this.section = new UiSectionBar.Section(title, icon);
        this.entries = List.copyOf(entries);
    }

    UiSectionBar.Section section() {
        return section;
    }

    int height(Font font, int width) {
        int messageHeight = Math.max(textHeight(font, SAVED, width), textHeight(font, SAVE_FAILED, width));
        for (Entry entry : entries) {
            if (entry instanceof Page page) {
                var reason = page.unavailable().get();
                if (reason != null) messageHeight = Math.max(messageHeight, textHeight(font, reason, width));
            }
        }
        return textHeight(font, section.label(), width) + ROW_GAP
                + entries.size() * (ROW_HEIGHT + ROW_GAP) + messageHeight;
    }

    void init(UiScreen screen, Font font, Bounds area) {
        this.area = area;
        unavailable = null;
        int y = area.y() + textHeight(font, section.label(), area.width()) + ROW_GAP;
        var preferences = screen.context().preferences();
        for (Entry entry : entries) {
            UiButton button;
            if (entry instanceof Toggle toggle) {
                var bounds = new Bounds(area.x(), y, area.width(), ROW_HEIGHT);
                var control = new UiToggleButton(bounds, toggle.read().getAsBoolean(), preferences,
                        UiStyle.LIQUID_GLASS, toggle.label(), enabled -> save(toggle, enabled));
                control.setIcons(GraphicIcon.SHOW, GraphicIcon.HIDE);
                control.setModeIndicatorsVisible(true);
                button = control.button();
                toggleControls.put(toggle, control);
            } else if (entry instanceof Page page) {
                var reason = page.unavailable().get();
                button = new UiButton(area.x(), y, area.width(), ROW_HEIGHT, page.label(), ignored -> {
                    status = null;
                    page.open().accept(screen, () -> buttons.get(page));
                }, preferences, false, UiStyle.LIQUID_GLASS);
                button.setLeadingIcon(page.icon());
                button.active = reason == null;
                if (reason != null) {
                    button.setPreview(reason);
                    if (unavailable == null) unavailable = reason;
                }
            } else {
                throw new IllegalStateException("Unknown settings entry");
            }
            button.setSampleBackground(false);
            buttons.put(entry, screen.add(button));
            y += ROW_HEIGHT + ROW_GAP;
        }
        messageY = y;
    }

    private void save(Toggle toggle, boolean enabled) {
        if (toggle.save().test(enabled)) {
            toggleControls.get(toggle).setValue(toggle.read().getAsBoolean());
            status = SAVED;
        } else {
            status = SAVE_FAILED;
            Minecraft.getInstance().getNarrator().sayNow(status);
        }
    }

    void setVisible(boolean visible) {
        buttons.values().forEach(button -> button.visible = visible);
    }

    void clearWidgets() {
        buttons.clear();
        toggleControls.clear();
    }

    void render(GuiGraphics graphics, Font font) {
        graphics.drawWordWrap(font, section.label(), area.x(), area.y(), area.width(), LiquidGlassTheme.textColor());
        Component message = status == null ? unavailable : status;
        if (message != null) {
            graphics.drawWordWrap(font, message, area.x(), messageY, area.width(), LiquidGlassTheme.textColor());
        }
    }

    private static int textHeight(Font font, Component text, int width) {
        return font.split(text, width).size() * font.lineHeight;
    }
}
