// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScreenService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_common_lib.client.control.ControlCenterModule.label;

final class ControlCenterScreen {
    private static final int SETTINGS = 0;
    private static final int OVERVIEW = 1;
    private static final List<UiSectionBar.Section> SECTIONS = List.of(
            new UiSectionBar.Section(label("settings"), GraphicIcon.SETTINGS),
            new UiSectionBar.Section(label("overview"), GraphicIcon.INFO));
    private static final List<String> FEATURES = List.of("crate", "jei");

    private final List<SettingsGroup> groups;
    private final ModuleContext module;
    private final ScreenService screens;
    private final UiId id;
    private final PlayerInfoHud hud;
    private final ControlCenterDraft draft;
    private final List<OverviewLine> overview = new ArrayList<>();
    private ScopeHandle tree;
    private int selected = SETTINGS;
    private int selectedGroup;
    private int overviewPage;
    private int overviewPageSize = 1;
    private ControlCenterLayout layout;
    private Bounds body;
    private UiScrollableSectionBar groupTabs;
    private UiButton previous;
    private UiButton next;
    private UiButton reset;
    private UiButton apply;
    private Component status;
    private UiScreen owner;
    private boolean restoreBackFocus;

    ControlCenterScreen(List<SettingsGroup> groups, ModuleContext module, ScreenService screens, UiId id,
                        PlayerInfoHud hud, ControlCenterDraft draft) {
        this.groups = List.copyOf(groups);
        this.module = module;
        this.screens = screens;
        this.id = id;
        this.hud = hud;
        this.draft = draft;
    }

    void init(UiScreen screen) {
        owner = screen;
        if (tree == null || !tree.isOpen()) tree = screens.mount(module, screen, id);
        draft.refreshHud(hud.settings());
        clearSettingsWidgets();
        var font = Minecraft.getInstance().font;
        var viewport = screen.viewport();
        layout = ControlCenterLayout.of(viewport.width(), viewport.height());
        body = null;
        previous = next = reset = apply = null;
        var preferences = screen.context().preferences();
        if (!layout.fits()) {
            int backWidth = Math.min(96, Math.max(0, viewport.width() - 8));
            if (backWidth > 0 && viewport.height() >= ControlCenterLayout.ROW + 4) {
                var back = screen.add(button((viewport.width() - backWidth) / 2,
                        viewport.height() - ControlCenterLayout.ROW - 4, backWidth, CommonComponents.GUI_BACK,
                        GraphicIcon.BACK, screen::onClose, screen));
                screen.setFocused(back);
                restoreBackFocus = false;
            }
            return;
        }

        int x = layout.contentX();
        int contentWidth = layout.contentWidth();
        body = new Bounds(x - 3, layout.bodyTop(), contentWidth + 6, layout.bodyHeight());
        var tabs = new UiSectionBar(x, layout.tabsY(), contentWidth, ControlCenterLayout.ROW,
                preferences, SECTIONS, selected, this::select);
        tabs.buttons().get(OVERVIEW).setTooltip(Tooltip.create(label("overview.hint")));
        tabs.buttons().forEach(screen::add);

        var sections = groups.stream().map(SettingsGroup::section).toList();
        groupTabs = screen.add(new UiScrollableSectionBar(
                new Bounds(x, layout.groupsY(), contentWidth, ControlCenterLayout.ROW),
                preferences, sections, selectedGroup, this::selectGroup));
        groupTabs.focus(selectedGroup);
        var groupArea = new Bounds(x, body.y() + 4, contentWidth, Math.max(0, body.height() - 4));
        for (SettingsGroup group : groups) group.init(screen, font, groupArea, this::changed);
        screen.widgetViewport(screen.viewport(), (mouseX, mouseY, horizontal, vertical) ->
                selected == SETTINGS && groups.get(selectedGroup).scroll(mouseX, mouseY, vertical));
        prepareOverview(font, groupArea);

        previous = screen.add(pageButton(layout.pagePreviousX(), label("previous_page"),
                GraphicIcon.PREVIOUS, () -> changePage(-1), screen));
        next = screen.add(pageButton(layout.pageNextX(), label("next_page"),
                GraphicIcon.NEXT, () -> changePage(1), screen));
        int column = (contentWidth - 2 * ControlCenterLayout.GAP) / 3;
        var back = screen.add(button(x, layout.footerY(), column, CommonComponents.GUI_BACK,
                GraphicIcon.BACK, screen::onClose, screen));
        reset = screen.add(button(x + column + ControlCenterLayout.GAP, layout.footerY(), column,
                label("reset"), GraphicIcon.RESET, this::reset, screen));
        apply = screen.add(button(x + 2 * (column + ControlCenterLayout.GAP), layout.footerY(),
                contentWidth - 2 * (column + ControlCenterLayout.GAP), label("apply"), GraphicIcon.SUCCESS,
                this::apply, screen, true));
        back.setTabOrderGroup(2);
        reset.setTabOrderGroup(2);
        apply.setTabOrderGroup(2);
        refreshActions();
        updateVisibility();
        screen.setFocused(restoreBackFocus ? back : tabs.buttons().get(selected));
        if (!restoreBackFocus && selected == SETTINGS) groups.get(selectedGroup).restoreFocus();
        restoreBackFocus = false;
    }

    private static UiButton button(int x, int y, int width, net.minecraft.network.chat.Component text,
                                   GraphicIcon icon, Runnable action, UiScreen screen) {
        return button(x, y, width, text, icon, action, screen, false);
    }

    private static UiButton button(int x, int y, int width, Component text, GraphicIcon icon,
                                   Runnable action, UiScreen screen, boolean primary) {
        var button = new UiButton(x, y, width, ControlCenterLayout.ROW, text, ignored -> action.run(),
                screen.context().preferences(), primary, UiStyle.LIQUID_GLASS);
        button.setSampleBackground(false);
        button.setLeadingIcon(icon);
        return button;
    }

    private UiButton pageButton(int x, Component text, GraphicIcon icon, Runnable action, UiScreen screen) {
        var button = new UiButton(x, layout.pageY(), ControlCenterLayout.PAGE_BUTTON_WIDTH, layout.pageHeight(),
                text, ignored -> action.run(), screen.context().preferences(), false, UiStyle.LIQUID_GLASS);
        button.setSampleBackground(false);
        button.setIcon(icon);
        return button;
    }

    private void prepareOverview(Font font, Bounds area) {
        overview.clear();
        for (String feature : FEATURES) {
            if (!overview.isEmpty()) overview.add(new OverviewLine(null, false));
            font.split(label(feature), area.width()).forEach(line -> overview.add(new OverviewLine(line, true)));
            font.split(label(feature + ".details"), area.width()).forEach(line -> overview.add(new OverviewLine(line, false)));
        }
        overviewPageSize = Math.max(1, (area.height() - font.lineHeight - 2) / font.lineHeight);
        overviewPage = Math.min(overviewPage, overviewPages() - 1);
    }

    private int overviewPages() { return ControlCenterLayout.pages(overview.size(), overviewPageSize); }

    private void select(int section) {
        selected = section;
        updateVisibility();
    }

    private void selectGroup(int index) {
        selectedGroup = index;
        updateVisibility();
    }

    private void changed() {
        status = null;
        refreshActions();
    }

    private void reset() {
        draft.restoreDefaults();
        groups.forEach(SettingsGroup::syncControls);
        changed();
    }

    private void apply() {
        var result = draft.apply(hud::applySettings, ClientLoading::applySettings,
                com.dishanhai.sh_common_lib.client.jei.JeiSearchPreferences::save);
        status = label(switch (result) {
            case SAVED -> "saved";
            case PARTIAL -> "partially_saved";
            case FAILED -> "save_failed";
        });
        refreshActions();
        if (result != ControlCenterDraft.ApplyResult.SAVED) Minecraft.getInstance().getNarrator().sayNow(status);
    }

    private void refreshActions() {
        if (reset == null || apply == null) return;
        boolean undo = draft.canUndoReset();
        reset.setMessage(label(undo ? "reset_undo" : "reset"));
        reset.setLeadingIcon(undo ? GraphicIcon.BACK : GraphicIcon.RESET);
        reset.setPreview(label(undo ? "reset_undo_hint" : "reset_hint"));
        reset.active = draft.canReset();
        apply.active = draft.dirty();
        apply.setPreview(status == null ? label("apply_hint") : status);
    }

    private void changePage(int direction) {
        if (selected == OVERVIEW) {
            overviewPage = Math.max(0, Math.min(overviewPages() - 1, overviewPage + direction));
        } else {
            groups.get(selectedGroup).changePage(direction);
        }
        updateVisibility();
        if (owner != null && previous != null && next != null) {
            if (owner.getFocused() == previous && !previous.active && next.active) owner.setFocused(next);
            else if (owner.getFocused() == next && !next.active && previous.active) owner.setFocused(previous);
        }
    }

    private void updateVisibility() {
        boolean settingsVisible = selected == SETTINGS;
        if (groupTabs != null) {
            if (!settingsVisible) groupTabs.cancelDrag();
            groupTabs.visible = settingsVisible;
        }
        for (int index = 0; index < groups.size(); index++) {
            groups.get(index).setVisible(settingsVisible && selectedGroup == index);
        }
        if (previous != null && next != null) {
            int page = settingsVisible ? groups.get(selectedGroup).page() : overviewPage;
            int pages = settingsVisible ? groups.get(selectedGroup).pages() : overviewPages();
            previous.visible = next.visible = pages > 1;
            previous.active = page > 0;
            next.active = page + 1 < pages;
        }
    }

    void render(UiScreen screen, UiFrame frame) {
        var graphics = frame.graphics();
        var font = Minecraft.getInstance().font;
        if (body == null) {
            if (screen.width > 8) graphics.drawWordWrap(font, label("too_small"), 4, 4,
                    screen.width - 8, LiquidGlassTheme.textColor());
            return;
        }
        if (selected == SETTINGS) groups.get(selectedGroup).advanceMotion();
        var preferences = frame.preferences();
        LiquidGlassTheme.popover(graphics, layout.left(), layout.top(), layout.width(), layout.height(), preferences);
        int titleRight = previous != null && previous.visible ? layout.pagePreviousX() - 4
                : layout.contentX() + layout.contentWidth();
        graphics.enableScissor(layout.contentX(), layout.top(), titleRight, layout.tabsY());
        graphics.drawString(font, screen.getTitle(), layout.contentX(), layout.titleY(), LiquidGlassTheme.textColor(), false);
        graphics.disableScissor();
        if (previous != null && previous.visible) {
            int page = selected == SETTINGS ? groups.get(selectedGroup).page() : overviewPage;
            int pages = selected == SETTINGS ? groups.get(selectedGroup).pages() : overviewPages();
            graphics.drawCenteredString(font, Component.translatable("screen.sh_common_lib.control_center.page",
                    page + 1, pages), layout.pagePreviousX() + ControlCenterLayout.PAGE_BUTTON_WIDTH
                    + ControlCenterLayout.PAGE_LABEL_WIDTH / 2, layout.pageY() + (layout.pageHeight() - font.lineHeight) / 2,
                    LiquidGlassTheme.textColor());
        }
        int surface = preferences.highContrast() ? DarkModeTheme.backgroundColor() : DarkModeTheme.surfaceColor();
        int edge = preferences.highContrast() ? LiquidGlassTheme.textColor() : DarkModeTheme.borderColor();
        if (UiTheme.current().hudGlass() && !preferences.highContrast()) {
            surface = surface & 0xFFFFFF | 0x18000000;
            edge = edge & 0xFFFFFF | 0x40000000;
        }
        RoundedCorners.draw(graphics, body.x(), body.y(), body.width(), body.height(), 5f, 1f, edge, surface);
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        try {
            if (selected == SETTINGS) groups.get(selectedGroup).render(graphics, font, status);
            else renderOverview(graphics, font);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderOverview(GuiGraphics graphics, Font font) {
        int start = overviewPage * overviewPageSize;
        int end = Math.min(overview.size(), start + overviewPageSize);
        int y = body.y() + 4;
        for (int index = start; index < end; index++) {
            var line = overview.get(index);
            if (line.text() != null) graphics.drawString(font, line.text(), layout.contentX(), y,
                    line.heading() ? LiquidGlassTheme.accentColor() : LiquidGlassTheme.mutedTextColor(), false);
            y += font.lineHeight;
        }
        if (status != null) graphics.drawString(font, status, layout.contentX(),
                body.bottom() - font.lineHeight - 2, LiquidGlassTheme.textColor(), false);
    }

    void removed() {
        clearSettingsWidgets();
        owner = null;
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

    private record OverviewLine(FormattedCharSequence text, boolean heading) {}

    ReturnGuard returnGuard(Screen parent) { return new ReturnGuard(parent); }

    final class ReturnGuard extends Screen {
        private final Screen parent;
        private final GuiEventListener returnFocus;
        private UiScreen controlCenter;

        private ReturnGuard(Screen parent) {
            super(label("title"));
            this.parent = parent;
            returnFocus = parent == null ? null : parent.getFocused();
        }

        void setControlCenter(UiScreen screen) { controlCenter = screen; }

        @Override
        protected void init() {
            if (selected == SETTINGS && groups.get(selectedGroup).leaveDetail()) {
                minecraft.tell(() -> minecraft.setScreen(controlCenter));
                return;
            }
            if (draft.dirty()) {
                minecraft.setScreen(new ConfirmScreen(discard -> {
                    if (discard) returnToParent();
                    else {
                        restoreBackFocus = true;
                        minecraft.setScreen(controlCenter);
                    }
                }, label("discard_title"), label("discard_details"), label("discard_action"), CommonComponents.GUI_CANCEL));
            } else returnToParent();
        }

        private void returnToParent() {
            minecraft.setScreen(parent);
            if (parent != null && returnFocus != null && parent.children().contains(returnFocus)) parent.setFocused(returnFocus);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}
