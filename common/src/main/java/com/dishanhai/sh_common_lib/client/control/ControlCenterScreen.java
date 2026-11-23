// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.FluidMorph;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiRenderUtil;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotBackground;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiFrameHandoff;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScreenService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_common_lib.client.control.ControlCenterModule.label;

final class ControlCenterScreen {
    private static final int SETTINGS = 0;
    private static final int OVERVIEW = 1;
    private static final float BODY_RADIUS = 5f;
    private static final Bounds HIDDEN = new Bounds(0, 0, 0, 0);
    private static final Component TITLE = label("title").copy().withStyle(ChatFormatting.BOLD);
    private static final Component COMPACT_TITLE = label("title.compact").copy().withStyle(ChatFormatting.BOLD);
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
    private final List<AbstractWidget> chrome = new ArrayList<>();
    private final UiDotBackground background = new UiDotBackground();
    private final UiFrameHandoff pageHandoff = new UiFrameHandoff();
    private final InterruptibleAnimation pageMorph = new InterruptibleAnimation(Spring.FLUID_PANEL, 1f);
    private final InterruptibleAnimation pageOrigin = new InterruptibleAnimation(Spring.INDICATOR);
    private final UiScreen.WidgetScroll scrollSettings = this::scrollSettings;
    private UiScreenReturn returning;
    private UiOverlayHost confirmation;
    private ReturnGuard returnGuard;
    private UiCapsuleReveal panel;
    private boolean panelOnly;
    private ScopeHandle tree;
    private static ControlCenterPosition lastPosition = ControlCenterPosition.INITIAL;
    private int selected;
    private int selectedGroup;
    private int overviewPage;
    private int overviewPageSize = 1;
    private ControlCenterLayout layout;
    private Bounds body;
    private UiScrollableSectionBar groupTabs;
    private UiScrollableSectionBar tabs;
    private UiButton previous;
    private UiButton next;
    private UiButton back;
    private UiButton reset;
    private UiButton apply;
    private String status;
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
        selected = lastPosition.overview() ? OVERVIEW : SETTINGS;
        selectedGroup = lastPosition.group();
        overviewPage = lastPosition.overviewPage();
        this.groups.get(selectedGroup).restorePosition(lastPosition.list());
    }

    void init(UiScreen screen) {
        owner = screen;
        if (tree == null || !tree.isOpen()) tree = screens.mount(module, screen, id);
        draft.refreshHud(hud.settings());
        clearSettingsWidgets();
        chrome.clear();
        pageHandoff.clear();
        pageMorph.snapTo(1f);
        var font = Minecraft.getInstance().font;
        var viewport = screen.viewport();
        layout = ControlCenterLayout.of(viewport.width(), viewport.height());
        body = null;
        previous = next = back = reset = apply = null;
        var preferences = screen.context().preferences();
        if (!layout.fits()) {
            int backWidth = Math.min(96, Math.max(0, viewport.width() - 8));
            if (backWidth > 0 && viewport.height() >= ControlCenterLayout.ROW + 4) {
                back = screen.add(button((viewport.width() - backWidth) / 2,
                        viewport.height() - ControlCenterLayout.ROW - 4, backWidth, CommonComponents.GUI_BACK,
                        GraphicIcon.BACK, this::requestClose, screen));
                screen.setFocused(back);
                restoreBackFocus = false;
            }
            return;
        }

        int x = layout.contentX();
        int contentWidth = layout.contentWidth();
        body = pageBody(selected);
        if (panel == null) {
            panel = ShBetterUiApi.motion().capsuleReveal();
            if (returning == null) returning = new UiScreenReturn(screen);
            panel.setSkeletonLayout(ignored -> panelSkeleton());
            panel.startAt(screen.width * 0.5f, layout.top() + 5f);
        } else {
            panel.retargetAt(screen.width * 0.5f, layout.top() + 5f);
        }
        panelOnly = !preferences.reducedMotion() && panel.active();
        tabs = screen.add(ShBetterUiApi.controls().jellySectionBar(
                new Bounds(x, layout.tabsY(), layout.tabsWidth(), ControlCenterLayout.ROW),
                preferences, SECTIONS, selected, this::select));
        tabs.buttons().get(OVERVIEW).setTooltip(Tooltip.create(label("overview.hint")));

        var sections = groups.stream().map(SettingsGroup::section).toList();
        groupTabs = screen.add(ShBetterUiApi.controls().scrollableSectionBar(
                new Bounds(x, layout.groupsY(), contentWidth, ControlCenterLayout.ROW),
                preferences, sections, selectedGroup, this::selectGroup).withJellyRadio());
        for (int index = 0; index < groups.size(); index++) {
            groupTabs.buttons().get(index).setTooltip(Tooltip.create(groups.get(index).title()));
        }
        groupTabs.focus(selectedGroup);
        var groupArea = new Bounds(x, layout.bodyTop(false) + 4, contentWidth, layout.bodyHeight(false) - 4);
        for (SettingsGroup group : groups) {
            group.init(screen, font, groupArea, this::changed,
                    () -> startPageMorph(groupTabs.buttons().get(selectedGroup)));
        }
        updateWidgetViewport(screen);
        prepareOverview(font);

        previous = screen.add(pageButton(layout.pagePreviousX(), label("previous_page"),
                GraphicIcon.PREVIOUS, () -> changePage(-1), screen));
        next = screen.add(pageButton(layout.pageNextX(), label("next_page"),
                GraphicIcon.NEXT, () -> changePage(1), screen));
        int actionWidth = layout.footerActionWidth();
        back = screen.add(button(x, layout.footerY(), actionWidth, CommonComponents.GUI_BACK,
                GraphicIcon.BACK, this::requestClose, screen));
        reset = screen.add(button(layout.footerResetX(), layout.footerY(), actionWidth,
                label("reset"), GraphicIcon.RESET, this::reset, screen));
        apply = screen.add(button(layout.footerApplyX(), layout.footerY(), actionWidth,
                label("apply"), GraphicIcon.SUCCESS, this::apply, screen, true));
        back.setTabOrderGroup(2);
        reset.setTabOrderGroup(2);
        apply.setTabOrderGroup(2);
        chrome.addAll(List.of(tabs, groupTabs, previous, next, back, reset, apply));
        refreshActions();
        updateVisibility();
        screen.setFocused(restoreBackFocus ? back : tabs);
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

    private Bounds pageBody(int section) {
        boolean overviewVisible = section == OVERVIEW;
        return new Bounds(layout.contentX() - 3, layout.bodyTop(overviewVisible),
                layout.contentWidth() + 6, layout.bodyHeight(overviewVisible));
    }

    private void prepareOverview(Font font) {
        overview.clear();
        for (String feature : FEATURES) {
            if (!overview.isEmpty()) overview.add(new OverviewLine(null, false));
            font.split(label(feature).copy().withStyle(ChatFormatting.BOLD), layout.contentWidth())
                    .forEach(line -> overview.add(new OverviewLine(line, true)));
            font.split(label(feature + ".details"), layout.contentWidth())
                    .forEach(line -> overview.add(new OverviewLine(line, false)));
        }
        overviewPageSize = layout.overviewPageSize(font.lineHeight);
        overviewPage = Math.min(overviewPage, overviewPages() - 1);
    }

    private int overviewPages() { return ControlCenterLayout.pages(overview.size(), overviewPageSize); }

    private void select(int section) {
        if (selected == section) return;
        startPageMorph(tabs.buttons().get(section), pageBody(SETTINGS));
        selected = section;
        body = pageBody(selected);
        updateVisibility();
    }

    private void selectGroup(int index) {
        if (selectedGroup == index) return;
        startPageMorph(groupTabs.buttons().get(index));
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
                com.dishanhai.sh_common_lib.client.jei.JeiSearchPreferences::save, ArtisanControlSettings::save,
                com.dishanhai.sh_common_lib.client.item.ArtisanAppearancePreferences::save,
                com.dishanhai.sh_common_lib.client.voice.VoiceItemPreferences::save);
        status = switch (result) {
            case SAVED -> "saved";
            case PARTIAL -> "partially_saved";
            case FAILED -> "save_failed";
        };
        refreshActions();
        if (result != ControlCenterDraft.ApplyResult.SAVED) Minecraft.getInstance().getNarrator().sayNow(label(status));
    }

    private void refreshActions() {
        if (reset == null || apply == null) return;
        boolean undo = draft.canUndoReset();
        String resetKey = undo ? "reset_undo" : "reset";
        reset.setMessage(label(layout.compactFooter() ? resetKey + ".compact" : resetKey));
        reset.setLeadingIcon(undo ? GraphicIcon.BACK : GraphicIcon.RESET);
        reset.setPreview(label(undo ? "reset_undo_hint" : "reset_hint"));
        reset.active = draft.canReset();
        apply.active = draft.dirty();
        apply.setPreview(label(status == null ? "apply_hint" : status));
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
        if (body == null) {
            drawBackdrop(frame, true);
            if (panel != null && panel.closing()) {
                panel.finish();
                return;
            }
            if (screen.width > 8) graphics.drawWordWrap(Minecraft.getInstance().font, label("too_small"), 4, 4,
                    screen.width - 8, LiquidGlassTheme.textColor());
            return;
        }
        var preferences = frame.preferences();
        var panelBounds = new Bounds(layout.left(), layout.top(), layout.width(), layout.height());
        if (selected == SETTINGS) groups.get(selectedGroup).advanceMotion();
        panelOnly = panel.render(graphics, panelBounds, preferences,
                progress -> returning.renderCrossfade(returnGuard.parent, graphics, frame.partialTick(), preferences,
                        progress, () -> drawBackdrop(frame, false)),
                () -> {
                    drawChrome(frame);
                    drawBody(frame, true);
                    for (var widget : chrome) widget.render(graphics, -1, -1, frame.partialTick());
                });
        if (owner != screen) return;
        updateWidgetViewport(screen);
        if (panelOnly) return;
        returning.clearHandoff();
        drawBackdrop(frame, true);
        LiquidGlassTheme.popover(graphics, layout.left(), layout.top(), layout.width(), layout.height(), preferences);
        drawChrome(frame);
        drawBody(frame, false);
    }

    private void drawBackdrop(UiFrame frame, boolean allowPointer) {
        background.renderThemed(owner, frame.graphics(), owner.width, owner.height, frame.preferences(), allowPointer);
    }

    private void drawChrome(UiFrame frame) {
        var graphics = frame.graphics();
        var font = Minecraft.getInstance().font;
        int titleRight = titleRight();
        if (status != null && layout.compact() && selected == SETTINGS) {
            var message = statusText(font, layout.contentWidth() - font.width(COMPACT_TITLE) - ControlCenterLayout.GAP);
            int messageX = titleRight - font.width(message);
            graphics.drawString(font, message, messageX, layout.titleY(), LiquidGlassTheme.textColor(), false);
            titleRight = messageX - ControlCenterLayout.GAP;
        }
        var title = font.width(TITLE) <= titleRight - layout.contentX() ? TITLE : COMPACT_TITLE;
        graphics.enableScissor(layout.contentX(), layout.top(), titleRight, layout.tabsY());
        graphics.drawString(font, title, layout.contentX(), layout.titleY(), LiquidGlassTheme.textColor(), false);
        graphics.disableScissor();
        if (previous != null && previous.visible) {
            int page = selected == SETTINGS ? groups.get(selectedGroup).page() : overviewPage;
            int pages = selected == SETTINGS ? groups.get(selectedGroup).pages() : overviewPages();
            graphics.drawCenteredString(font, Component.translatable("screen.sh_common_lib.control_center.page",
                    page + 1, pages), layout.pagePreviousX() + ControlCenterLayout.PAGE_BUTTON_WIDTH
                    + ControlCenterLayout.PAGE_LABEL_WIDTH / 2, layout.pageY() + (layout.pageHeight() - font.lineHeight) / 2,
                    frame.preferences().highContrast() ? LiquidGlassTheme.textColor() : LiquidGlassTheme.mutedTextColor());
        }
        if (status != null && !layout.compact()) {
            int x = layout.contentX();
            int right = x + layout.contentWidth();
            var message = statusText(font, layout.contentWidth());
            graphics.enableScissor(x, layout.bodyBottom(), right, layout.footerY());
            graphics.drawString(font, message, right - font.width(message), layout.footerStatusY(font.lineHeight),
                    LiquidGlassTheme.textColor(), false);
            graphics.disableScissor();
        }
    }

    private Component statusText(Font font, int availableWidth) {
        var message = label(status);
        return font.width(message) <= availableWidth ? message : label(status + ".compact");
    }

    private int titleRight() {
        return previous != null && previous.visible ? layout.pagePreviousX() - 4
                : layout.contentX() + layout.contentWidth();
    }

    private void drawBody(UiFrame frame, boolean panelTransition) {
        var graphics = frame.graphics();
        var preferences = frame.preferences();
        long now = frame.nowMs();
        float progress = GuiMotion.clamp01(pageMorph.sample(now, preferences));
        boolean morphing = progress < 1f;
        boolean manual = panelTransition || morphing || pageHandoff.active();
        if (selected == SETTINGS) groups.get(selectedGroup).setManualRender(manual);
        int surface = preferences.highContrast() ? DarkModeTheme.backgroundColor() : DarkModeTheme.surfaceColor();
        int edge = preferences.highContrast() ? LiquidGlassTheme.textColor() : GuiMotion.lerpColor(
                0xA0000000 | (LiquidGlassTheme.accentColor() & 0xFFFFFF), DarkModeTheme.borderColor(), progress);
        if (UiTheme.current().hudGlass() && !preferences.highContrast()) {
            surface = surface & 0xFFFFFF | 0x18000000;
            edge = edge & 0xFFFFFF | 0x40000000;
        }
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        try {
            if (!morphing) {
                RoundedCorners.draw(graphics, body.x(), body.y(), body.width(), body.height(), BODY_RADIUS, 1f, edge, surface);
                drawPage(frame, manual);
                return;
            }
            var target = new GuiRect(body.x(), body.y(), body.width(), body.height());
            var seed = FluidMorph.seed(pageOrigin.sample(now, preferences), body.y() + 5f);
            var shape = FluidMorph.between(seed, target, BODY_RADIUS, progress);
            var rect = shape.rect();
            var skeleton = bodySkeleton();
            float opacity = GuiRenderUtil.easeOut((progress - 0.54f) / 0.44f);
            RoundedCorners.draw(graphics, body.x(), body.y(), body.width(), body.height(), BODY_RADIUS, 0f, 0, surface);
            UiSkeleton.draw(graphics, body, skeleton, now, preferences, (1f - opacity) * 0.42f);
            RoundedCorners.draw(graphics, rect.getX(), rect.getY(), rect.getW(), rect.getH(), shape.radius(), 1f, edge, surface);
            float inset = shape.radius() * 0.4f * (1f - progress);
            int left = Math.max(body.x(), Math.round(rect.getX() + inset));
            int top = Math.max(body.y(), Math.round(rect.getY() + inset));
            int right = Math.min(body.right(), Math.round(rect.getX() + rect.getW() - inset));
            int bottom = Math.min(body.bottom(), Math.round(rect.getY() + rect.getH() - inset));
            if (right <= left || bottom <= top) return;
            var clip = new Bounds(left, top, right - left, bottom - top);
            graphics.enableScissor(left, top, right, bottom);
            try {
                if (opacity > 0f) {
                    drawPage(frame, true);
                    if (opacity < 1f) graphics.fill(left, top, right, bottom,
                            GuiMotion.lerpColor(surface, surface & 0xFFFFFF, opacity));
                }
                if (opacity < 1f) UiSkeleton.draw(graphics, clip, skeleton, now, preferences, 1f - opacity);
            } finally {
                graphics.disableScissor();
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawPage(UiFrame frame, boolean manual) {
        var font = Minecraft.getInstance().font;
        if (selected == SETTINGS) {
            var group = groups.get(selectedGroup);
            group.render(frame.graphics(), font);
            if (manual) group.renderWidgets(frame.graphics(), frame.partialTick());
        } else {
            renderOverview(frame.graphics(), font, frame.preferences().highContrast());
        }
    }

    private void renderOverview(GuiGraphics graphics, Font font, boolean highContrast) {
        int start = overviewPage * overviewPageSize;
        int end = Math.min(overview.size(), start + overviewPageSize);
        int y = body.y() + 4;
        for (int index = start; index < end; index++) {
            var line = overview.get(index);
            if (line.text() != null) graphics.drawString(font, line.text(), layout.contentX(), y,
                    highContrast ? LiquidGlassTheme.textColor()
                            : line.heading() ? LiquidGlassTheme.accentColor() : LiquidGlassTheme.mutedTextColor(), false);
            y += font.lineHeight;
        }
        if (status != null && layout.compact()) {
            var message = statusText(font, layout.contentWidth());
            graphics.drawString(font, message, layout.contentX() + layout.contentWidth() - font.width(message),
                    body.bottom() - font.lineHeight - 2, LiquidGlassTheme.textColor(), false);
        }
    }

    void foreground(UiScreen screen, UiFrame frame) {
        if (body != null && !panelOnly) pageHandoff.render(frame.graphics(), frame.preferences());
    }

    private List<UiSkeleton.Block> bodySkeleton() {
        var blocks = new ArrayList<UiSkeleton.Block>();
        blocks.add(new UiSkeleton.Block(new Bounds(layout.contentX(), body.y() + 5, 48, 5)));
        if (selected == SETTINGS) groups.get(selectedGroup).addSkeletonRows(blocks);
        else {
            int lineHeight = Minecraft.getInstance().font.lineHeight;
            int count = Math.min(overviewPageSize, overview.size() - overviewPage * overviewPageSize);
            for (int index = 1; index < count; index++) {
                blocks.add(new UiSkeleton.Block(new Bounds(layout.contentX(), body.y() + 5 + index * lineHeight,
                        layout.contentWidth() * 2 / 3, 5)));
            }
        }
        return blocks;
    }

    private List<UiSkeleton.Block> panelSkeleton() {
        var blocks = bodySkeleton();
        blocks.add(new UiSkeleton.Block(new Bounds(layout.contentX(), layout.titleY() + 2,
                Math.min(64, titleRight() - layout.contentX()), 6)));
        for (var widget : chrome) {
            if (!widget.visible || widget.getWidth() < 8) continue;
            blocks.add(new UiSkeleton.Block(new Bounds(widget.getX() + 2, widget.getY() + 3,
                    widget.getWidth() - 4, widget.getHeight() - 6), 8f));
        }
        return blocks;
    }

    private void updateWidgetViewport(UiScreen screen) {
        screen.widgetViewport(panelOnly ? HIDDEN : screen.viewport(), scrollSettings);
    }

    private boolean scrollSettings(double x, double y, double horizontal, double vertical) {
        return selected == SETTINGS && groups.get(selectedGroup).scroll(x, y, vertical);
    }

    void captureParent(UiScreen screen) {
        returning = ShBetterUiApi.motion().screenCrossfade(screen, returnGuard.parent, screen.context().preferences());
    }

    private void startPageMorph(UiButton source) {
        startPageMorph(source, body);
    }

    private void startPageMorph(UiButton source, Bounds snapshotArea) {
        var preferences = owner.context().preferences();
        long now = PageTransition.nowMs();
        boolean continuing = pageHandoff.active();
        pageHandoff.capture(snapshotArea, preferences);
        float center = source.getX() + source.getWidth() * 0.5f;
        center = Math.max(body.x() + 16f, Math.min(body.right() - 16f, center));
        pageMorph.sample(now, preferences);
        if (preferences.reducedMotion()) {
            pageOrigin.snapTo(center);
        } else if (!pageMorph.running() && !continuing) {
            pageOrigin.snapTo(center);
            pageMorph.snapTo(0f);
            pageMorph.setOpen(true, now);
        } else {
            pageOrigin.animateTo(center, now);
        }
    }

    boolean handleKey(Screen screen, int key, int scanCode) {
        if (owner != screen || confirmation != null && confirmation.isModalOpen()) return false;
        if (panel != null && panel.closing()) {
            if (ControlCenterModule.openKey().matches(key, scanCode) && panel.reopen()) returnGuard.discardAuthorized = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (screen.isDragging() && (tabs != null && tabs.cancelDrag()
                    || groupTabs != null && groupTabs.cancelDrag())) {
                screen.setDragging(false);
                return true;
            }
            requestClose();
            return true;
        }
        boolean activation = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE;
        return activation && (panelOnly || body != null && selected == SETTINGS
                && groups.get(selectedGroup).containsFocus(screen.getFocused())
                && (pageHandoff.active() || pageMorph.running()));
    }

    private void requestClose() {
        if (owner == null) return;
        if (panel != null && panel.closing()) return;
        if (selected == SETTINGS && groups.get(selectedGroup).leaveDetail()) return;
        if (draft.dirty()) {
            if (confirmation != null && confirmation.isModalOpen()) return;
            var preferences = owner.context().preferences();
            confirmation = new UiOverlayHost(owner, preferences);
            var prompt = new UiConfirmation.Prompt(label("discard_title"), label("discard_details"),
                    label("discard_action"), CommonComponents.GUI_CANCEL);
            if (body == null) confirmation.showDialog(prompt, this::closeToParent);
            else {
                panel.finish();
                confirmation.showDialogFrom(back, prompt, this::closeToParent);
            }
        } else closeToParent();
    }

    private void closeToParent() {
        var screen = owner;
        if (screen == null) return;
        returnGuard.discardAuthorized = true;
        var preferences = screen.context().preferences();
        if (body == null || preferences.reducedMotion()) {
            screen.onClose();
            return;
        }
        panelOnly = true;
        tabs.cancelDrag();
        groupTabs.cancelDrag();
        screen.setDragging(false);
        updateWidgetViewport(screen);
        panel.close(preferences, screen::onClose);
    }

    void removed() {
        clearSettingsWidgets();
        lastPosition = new ControlCenterPosition(selected == OVERVIEW, selectedGroup, overviewPage,
                groups.get(selectedGroup).position());
        chrome.clear();
        if (confirmation != null) confirmation.ownerClosed();
        confirmation = null;
        pageHandoff.clear();
        if (returning != null) returning.clearHandoff();
        returning = null;
        background.reset();
        panel = null;
        panelOnly = false;
        owner = null;
        if (tree != null) {
            tree.close();
            tree = null;
        }
    }

    private void clearSettingsWidgets() {
        if (tabs != null) {
            tabs.cancelDrag();
            tabs = null;
        }
        if (groupTabs != null) {
            groupTabs.cancelDrag();
            groupTabs = null;
        }
        groups.forEach(SettingsGroup::clearWidgets);
    }

    private record OverviewLine(FormattedCharSequence text, boolean heading) {}

    ReturnGuard returnGuard(Screen parent) {
        returnGuard = new ReturnGuard(parent);
        return returnGuard;
    }

    final class ReturnGuard extends Screen {
        private final Screen parent;
        private final GuiEventListener returnFocus;
        private UiScreen controlCenter;
        private boolean discardAuthorized;

        private ReturnGuard(Screen parent) {
            super(label("title"));
            this.parent = parent;
            returnFocus = parent == null ? null : parent.getFocused();
        }

        void setControlCenter(UiScreen screen) { controlCenter = screen; }

        @Override
        protected void init() {
            if (discardAuthorized) {
                discardAuthorized = false;
                returnToParent();
                return;
            }
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
