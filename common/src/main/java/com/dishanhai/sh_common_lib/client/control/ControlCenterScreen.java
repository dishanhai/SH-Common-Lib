// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScreenService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.dishanhai.sh_common_lib.client.control.ControlCenterModule.label;

final class ControlCenterScreen {
    private static final int SETTINGS = 0;
    private static final int OVERVIEW = 1;
    private static final int PADDING = 12;
    private static final int GAP = 6;
    private static final int ROW = 22;
    private static final List<UiSectionBar.Section> SECTIONS = List.of(
            new UiSectionBar.Section(label("settings"), GraphicIcon.SETTINGS),
            new UiSectionBar.Section(label("overview"), GraphicIcon.INFO));
    private static final List<String> FEATURES = List.of("crate", "jei");

    private final List<SettingsGroup> groups;
    private final ModuleContext module;
    private final ScreenService screens;
    private final UiId id;
    private ScopeHandle tree;
    private int selected = SETTINGS;
    private int selectedGroup;
    private Bounds panel;
    private UiScrollableSectionBar groupTabs;
    private int bodyY;

    ControlCenterScreen(List<SettingsGroup> groups, ModuleContext module, ScreenService screens, UiId id) {
        this.groups = List.copyOf(groups);
        this.module = module;
        this.screens = screens;
        this.id = id;
    }

    void init(UiScreen screen) {
        if (tree == null || !tree.isOpen()) tree = screens.mount(module, screen, id);
        var client = Minecraft.getInstance();
        var font = client.font;
        var viewport = screen.viewport();
        int width = Math.min(360, viewport.width() - 2 * GAP);
        int contentWidth = width - 2 * PADDING;
        panel = null;
        clearSettingsWidgets();
        if (contentWidth < 2 * GraphicIcon.PIXELS) return;

        int overviewHeight = 0;
        for (String feature : FEATURES) {
            overviewHeight += paragraphHeight(font, label(feature), contentWidth) + 2
                    + paragraphHeight(font, label(feature + ".details"), contentWidth) + GAP;
        }
        int settingsHeight = groups.stream().mapToInt(group -> group.height(font, contentWidth)).max().orElse(0);
        int groupBarHeight = groups.size() > 1 ? ROW + GAP : 0;
        settingsHeight += groupBarHeight;
        int height = 2 * PADDING + ROW + GAP + Math.max(settingsHeight, overviewHeight - GAP);
        if (height > viewport.height() - 2 * GAP) return;

        panel = new Bounds(viewport.x() + (viewport.width() - width) / 2,
                viewport.y() + (viewport.height() - height) / 2, width, height);
        int x = panel.x() + PADDING;
        int tabsY = panel.y() + PADDING;
        bodyY = tabsY + ROW + GAP;
        var preferences = screen.context().preferences();
        var tabs = new UiSectionBar(x, tabsY, contentWidth, ROW, preferences, SECTIONS, selected, this::select);
        tabs.buttons().get(OVERVIEW).setTooltip(Tooltip.create(label("overview.hint")));
        tabs.buttons().forEach(screen::add);
        if (groupBarHeight > 0) {
            var sections = groups.stream().map(SettingsGroup::section).toList();
            groupTabs = screen.add(new UiScrollableSectionBar(new Bounds(x, bodyY, contentWidth, ROW),
                    preferences, sections, selectedGroup, this::selectGroup));
        }
        var groupArea = new Bounds(x, bodyY + groupBarHeight, contentWidth, settingsHeight - groupBarHeight);
        for (SettingsGroup group : groups) group.init(screen, font, groupArea);
        updateSettingsVisibility();
    }

    private void select(int section) {
        selected = section;
        updateSettingsVisibility();
    }

    private void selectGroup(int index) {
        selectedGroup = index;
        updateSettingsVisibility();
    }

    private void updateSettingsVisibility() {
        boolean settingsVisible = selected == SETTINGS;
        if (groupTabs != null) {
            if (!settingsVisible) groupTabs.cancelDrag();
            groupTabs.visible = settingsVisible;
        }
        for (int index = 0; index < groups.size(); index++) {
            groups.get(index).setVisible(settingsVisible && selectedGroup == index);
        }
    }

    void render(UiScreen screen, UiFrame frame) {
        var graphics = frame.graphics();
        var font = Minecraft.getInstance().font;
        DarkModeTheme.background(graphics, 0, 0, screen.width, screen.height);
        if (panel == null) {
            var viewport = frame.viewport();
            if (viewport.width() > 2 * GAP) {
                graphics.drawWordWrap(font, label("too_small"), viewport.x() + GAP, viewport.y() + GAP,
                        viewport.width() - 2 * GAP, DarkModeTheme.textColor());
            }
            return;
        }
        var preferences = frame.preferences();
        LiquidGlassTheme.plate(graphics, panel.x(), panel.y(), panel.width(), panel.height(),
                preferences.highContrast() ? LiquidGlassTheme.textColor() : DarkModeTheme.borderColor(),
                DarkModeTheme.surfaceColor(), preferences);
        int x = panel.x() + PADDING;
        int width = panel.width() - 2 * PADDING;
        if (selected == SETTINGS) {
            if (!groups.isEmpty()) groups.get(selectedGroup).render(graphics, font);
        } else {
            int y = bodyY;
            for (String feature : FEATURES) {
                y += drawParagraph(graphics, font, label(feature), x, y, width, LiquidGlassTheme.textColor()) + 2;
                y += drawParagraph(graphics, font, label(feature + ".details"), x, y, width,
                        LiquidGlassTheme.mutedTextColor()) + GAP;
            }
        }
    }

    void removed() {
        clearSettingsWidgets();
        if (tree != null) {
            tree.close();
            tree = null;
        }
    }

    private void clearSettingsWidgets() {
        if (groupTabs != null) {
            groupTabs.cancelDrag();
            groupTabs = null;
        }
        groups.forEach(SettingsGroup::clearWidgets);
    }

    private static int paragraphHeight(Font font, Component text, int width) {
        return font.split(text, width).size() * font.lineHeight;
    }

    private static int drawParagraph(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        int lineY = y;
        for (var line : font.split(text, width)) {
            graphics.drawString(font, line, x, lineY, color, false);
            lineY += font.lineHeight;
        }
        return lineY - y;
    }
}
