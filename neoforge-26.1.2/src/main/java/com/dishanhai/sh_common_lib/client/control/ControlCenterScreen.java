// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.GalleryFieldDotMatrix;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerTracker;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiVerticalScrollBar;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import com.dishanhai.sh_better_ui.kernel.api.layout.SplitActionRow;
import com.dishanhai.sh_better_ui.kernel.api.layout.ListEntrance;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.dishanhai.sh_common_lib.client.control.ControlCenterModule.label;

final class ControlCenterScreen extends Screen {
    private static final List<String> FEATURES = List.of("crate", "jei");
    private static final List<UiSectionBar.Section> SECTIONS = List.of(
            new UiSectionBar.Section(label("settings"), GraphicIcon.SETTINGS),
            new UiSectionBar.Section(label("overview"), GraphicIcon.INFO));
    private static final DotMatrixProfile DOTS = DotMatrixProfile.VIOLET
            .withPalette(new DotMatrixProfile.Palette(0xFF0B101B, 0x72FFFFFF, 0xFFFFFFFF, 0f))
            .withGrid(new DotMatrixProfile.Grid(8, 2, 1f, 27L))
            .withMotion(new DotMatrixProfile.Motion(DotMatrixProfile.Effect.TWINKLE,
                    DotMatrixProfile.VIOLET.motion().periodMs(), 0f, 0, 0f))
            .withInteraction(new DotMatrixProfile.Interaction(0, DotMatrixProfile.VIOLET.interaction().rippleRadius()))
            .withPointerLens(DotMatrixProfile.PointerLens.DEFAULT)
            .withGradient(DotMatrixProfile.Gradient.RAINBOW);

    private final Screen parent;
    private final List<SettingsGroup> groups;
    private final PlayerInfoHud hud;
    private final ControlCenterDraft draft;
    private final Map<SettingsGroup.Page, Button> pageButtons = new IdentityHashMap<>();
    private final List<OverviewLine> overviewLines = new ArrayList<>();
    private final GalleryFieldDotMatrix background = new GalleryFieldDotMatrix();
    private SettingsGroup selectedGroup;
    private boolean overview;
    private Component status;
    private ControlCenterLayout layout;
    private Bounds body;
    private int statusY;
    private int entryPage;
    private final VerticalScroll scrolling = new VerticalScroll();
    private final ListEntrance entrance = new ListEntrance();
    private final Map<SettingsGroup.Subgroup, Double> detailOffsets = new IdentityHashMap<>();
    private final List<Placed> placed = new ArrayList<>();
    private SettingsGroup.Subgroup detail;
    private SettingsGroup.Subgroup returnGroup;
    private UiVerticalScrollBar scrollbar;
    private GuiEventListener lastListFocus;
    private double rootOffset;
    private int listTop, listColumns, listColumnWidth;
    private int overviewPage;
    private int overviewPageSize = 1;
    private UiButton resetButton;
    private UiButton applyButton;
    private boolean paging;
    private int pageCount = 1;
    private FocusTarget requestedFocus = FocusTarget.TABS;
    private boolean pointerInside;
    private boolean pointerDown;
    private int lastPointerX;
    private int lastPointerY;
    private long lastPointerMove;
    private long pulseAt = -1;
    private float pulseX;
    private float pulseY;

    ControlCenterScreen(Screen parent, List<SettingsGroup> groups, PlayerInfoHud hud, ControlCenterDraft draft) {
        super(label("title"));
        this.parent = parent;
        this.groups = List.copyOf(groups);
        this.hud = hud;
        this.draft = draft;
        selectedGroup = this.groups.getFirst();
    }

    @Override
    protected void init() {
        draft.refreshHud(hud.settings());
        pageButtons.clear();
        placed.clear();
        lastListFocus = null;
        scrollbar = null;
        toggleButtons.clear();
        resetButton = applyButton = null;
        paging = false;
        statusY = -1;
        layout = ControlCenterLayout.of(width, height);
        body = null;
        if (!layout.fits()) {
            int backWidth = Math.min(96, Math.max(0, width - 8));
            if (backWidth > 0 && height >= ControlCenterLayout.ROW + 4) {
                setInitialFocus(addRenderableWidget(button((width - backWidth) / 2,
                        height - ControlCenterLayout.ROW - 4, backWidth, CommonComponents.GUI_BACK,
                        GraphicIcon.BACK, this::onClose)));
            }
            return;
        }

        int x = layout.contentX();
        int contentWidth = layout.contentWidth();
        body = new Bounds(x - 3, layout.bodyTop(), contentWidth + 6, layout.bodyHeight());
        var preferences = UiPreferences.configured();
        var tabs = ShBetterUiApi.controls().sectionBar(new Bounds(x, layout.tabsY(), contentWidth,
                ControlCenterLayout.ROW), preferences, SECTIONS, overview ? 1 : 0, index -> show(index == 1));
        tabs.buttons().get(1).setTooltip(Tooltip.create(label("overview.hint")));
        tabs.buttons().forEach(this::addRenderableWidget);
        GuiEventListener focus = tabs.buttons().get(overview ? 1 : 0);
        int pages;
        int currentPage;
        if (overview) {
            prepareOverview(contentWidth);
            pages = ControlCenterLayout.pages(overviewLines.size(), overviewPageSize);
            overviewPage = Math.min(overviewPage, pages - 1);
            currentPage = overviewPage;
        } else {
            var sections = groups.stream().map(group -> new UiSectionBar.Section(group.title(), group.icon())).toList();
            int selected = groups.indexOf(selectedGroup);
            var selector = addRenderableWidget(ShBetterUiApi.controls().scrollableSectionBar(
                    new Bounds(x, layout.groupsY(), contentWidth, ControlCenterLayout.ROW),
                    preferences, sections, selected, index -> {
                        saveOffset();
                        selectedGroup = groups.get(index);
                        detail = null;
                        rootOffset = 0;
                        detailOffsets.clear();
                        entryPage = 0;
                        status = null;
                        queueRebuild(FocusTarget.GROUPS);
                    }));
            selector.focus(selected);
            if (requestedFocus == FocusTarget.GROUPS) focus = selector;
            listTop = body.y() + 4 + font.lineHeight + 4;
            listColumns = selectedGroup.twoColumns() && contentWidth >= 164 ? 2 : 1;
            int listWidth = Math.max(0, contentWidth - 12);
            listColumnWidth = (listWidth - (listColumns - 1) * ControlCenterLayout.GAP) / listColumns;
            var entries = detail == null ? selectedGroup.entries() : detail.entries();
            int rows = (entries.size() + listColumns - 1) / listColumns + (detail == null ? 0 : 1);
            scrolling.resize(Math.max(0, body.bottom() - listTop),
                    Math.max(0, rows * (ControlCenterLayout.ROW + ControlCenterLayout.GAP) - ControlCenterLayout.GAP));
            scrolling.scrollTo(detail == null ? rootOffset : detailOffsets.getOrDefault(detail, 0d));
            entrance.start(now());
            if (detail != null) {
                var backDetail = addClippedButton(button(x, listTop, listWidth, CommonComponents.GUI_BACK,
                        GraphicIcon.BACK, this::leaveDetail));
                backDetail.setTabOrderGroup(1);
                placed.add(new Placed(backDetail, -1, 0));
                if (requestedFocus == FocusTarget.DETAIL) focus = backDetail;
            }
            for (int index = 0; index < entries.size(); index++) {
                var entry = entries.get(index);
                var split = entry instanceof SettingsGroup.Subgroup
                        ? SplitActionRow.of(listColumnWidth, ControlCenterLayout.ROW, 2) : null;
                int entryWidth = split == null ? listColumnWidth : split.actionWidth();
                var toggle = entry instanceof SettingsGroup.Toggle t ? t
                        : entry instanceof SettingsGroup.Subgroup g ? g.toggle() : null;
                if (toggle != null) {
                    var control = addClippedButton(button(x, listTop, entryWidth,
                            toggle.text(toggle.read().getAsBoolean()),
                            toggle.read().getAsBoolean() ? GraphicIcon.SHOW : GraphicIcon.HIDE, () -> edit(toggle)));
                    control.setSelected(toggle.read().getAsBoolean());
                    control.setModeIndicators(2, toggle.read().getAsBoolean() ? 1 : 0);
                    control.active = toggle.available().getAsBoolean();
                    if (!control.active) control.setSelected(false);
                    control.setTabOrderGroup(1);
                    toggleButtons.put(toggle, control);
                    placed.add(new Placed(control, index, 0));
                } else if (entry instanceof SettingsGroup.Page page) {
                    var control = addClippedButton(button(x, listTop, entryWidth, page.label(), page.icon(),
                            () -> page.open().accept(this, () -> pageButtons.get(page))));
                    control.setTabOrderGroup(1);
                    pageButtons.put(page, control);
                    var unavailable = page.unavailable().get();
                    control.active = unavailable == null;
                    if (unavailable != null) control.setTooltip(Tooltip.create(unavailable));
                    placed.add(new Placed(control, index, 0));
                }
                if (entry instanceof SettingsGroup.Subgroup group) {
                    var arrow = addClippedButton(button(x, listTop, split.disclosureWidth(),
                            Component.translatable("screen.sh_common_lib.control_center.enter_group", group.title()),
                            GraphicIcon.NEXT, () -> enterDetail(group)));
                    arrow.setIcon(GraphicIcon.NEXT);
                    arrow.active = group.toggle().available().getAsBoolean();
                    arrow.setTabOrderGroup(1);
                    placed.add(new Placed(arrow, index, split.disclosureX()));
                    if (requestedFocus == FocusTarget.DETAIL_RETURN && returnGroup == group) focus = arrow;
                }
            }
            scrollbar = addRenderableWidget(ShBetterUiApi.controls().verticalScrollBar(
                    new Bounds(x + contentWidth - 8, listTop, 8, Math.max(0, body.bottom() - listTop)),
                    label("scrollbar"), scrolling, ControlCenterLayout.ROW + ControlCenterLayout.GAP, this::positionRows));
            scrollbar.setTabOrderGroup(1);
            scrollbar.setSmoothScrolling(() -> UiPreferences.configured().reducedMotion());
            positionRows();
            statusY = body.bottom();
            pages = 1;
            currentPage = 0;
        }

        pageCount = pages;
        if (pages > 1) {
            paging = true;
            var previous = addRenderableWidget(pageButton(layout.pagePreviousX(), label("previous_page"),
                    GraphicIcon.PREVIOUS, () -> movePage(-1)));
            var next = addRenderableWidget(pageButton(layout.pageNextX(), label("next_page"),
                    GraphicIcon.NEXT, () -> movePage(1)));
            previous.active = currentPage > 0;
            next.active = currentPage + 1 < pages;
            if (requestedFocus == FocusTarget.PREVIOUS || requestedFocus == FocusTarget.NEXT) {
                focus = requestedFocus == FocusTarget.PREVIOUS && previous.active ? previous
                        : requestedFocus == FocusTarget.NEXT && next.active ? next
                        : previous.active ? previous : next;
            }
        }
        int column = (contentWidth - 2 * ControlCenterLayout.GAP) / 3;
        var back = addRenderableWidget(button(x, layout.footerY(), column, CommonComponents.GUI_BACK,
                GraphicIcon.BACK, this::onClose));
        resetButton = addRenderableWidget(button(x + column + ControlCenterLayout.GAP, layout.footerY(), column,
                label("reset"), GraphicIcon.RESET, this::reset));
        applyButton = addRenderableWidget(button(x + 2 * (column + ControlCenterLayout.GAP), layout.footerY(),
                contentWidth - 2 * (column + ControlCenterLayout.GAP), label("apply"), GraphicIcon.SUCCESS, this::apply, true));
        back.setTabOrderGroup(2);
        resetButton.setTabOrderGroup(2);
        applyButton.setTabOrderGroup(2);
        if (requestedFocus == FocusTarget.BACK) focus = back;
        refreshActions();
        setInitialFocus(focus);
        requestedFocus = FocusTarget.TABS;
    }

    private final Map<SettingsGroup.Toggle, UiButton> toggleButtons = new IdentityHashMap<>();

    private UiButton addClippedButton(UiButton button) {
        addWidget(button);
        addRenderableOnly((graphics, mouseX, mouseY, partialTick) -> {
            if (body == null || overview || !button.visible) return;
            int left = layout.contentX(), right = left + Math.max(0, layout.contentWidth() - 12);
            graphics.enableScissor(left, listTop, right, body.bottom());
            try {
                boolean inside = mouseX >= left && mouseX < right && mouseY >= listTop && mouseY < body.bottom();
                button.extractRenderState(graphics, inside ? mouseX : -1, inside ? mouseY : -1, partialTick);
            } finally {
                graphics.disableScissor();
            }
        });
        return button;
    }

    @Override
    public Optional<GuiEventListener> getChildAt(double x, double y) {
        for (var child : children()) {
            boolean clipped = placed.stream().anyMatch(row -> row.button() == child);
            if (clipped && (body == null || x < layout.contentX()
                    || x >= layout.contentX() + Math.max(0, layout.contentWidth() - 12)
                    || y < listTop || y >= body.bottom())) continue;
            if (child.isMouseOver(x, y)) return Optional.of(child);
        }
        return Optional.empty();
    }

    private UiButton button(int x, int y, int width, Component text, GraphicIcon icon, Runnable action) {
        return button(x, y, width, text, icon, action, false);
    }

    private UiButton button(int x, int y, int width, Component text, GraphicIcon icon, Runnable action, boolean primary) {
        var control = ShBetterUiApi.controls().button(new Bounds(x, y, width, ControlCenterLayout.ROW), text,
                ignored -> action.run(), UiPreferences.configured(), primary, UiStyle.LIQUID_GLASS);
        control.setSampleBackground(false);
        control.setLeadingIcon(icon);
        return control;
    }

    private UiButton pageButton(int x, Component text, GraphicIcon icon, Runnable action) {
        var control = ClientPanels.button(x, layout.pageY(), ControlCenterLayout.PAGE_BUTTON_WIDTH,
                layout.pageHeight(), text, ignored -> action.run());
        control.setIcon(icon);
        return control;
    }

    private void prepareOverview(int width) {
        overviewLines.clear();
        for (String feature : FEATURES) {
            if (!overviewLines.isEmpty()) overviewLines.add(new OverviewLine(null, false));
            font.split(label(feature), width).forEach(line -> overviewLines.add(new OverviewLine(line, true)));
            font.split(label(feature + ".details"), width).forEach(line -> overviewLines.add(new OverviewLine(line, false)));
        }
        overviewPageSize = Math.max(1, (body.height() - 6 - font.lineHeight) / font.lineHeight);
    }

    private void movePage(int direction) {
        if (overview) overviewPage += direction;
        else entryPage += direction;
        status = null;
        queueRebuild(direction < 0 ? FocusTarget.PREVIOUS : FocusTarget.NEXT);
    }

    private void queueRebuild(FocusTarget focus) {
        requestedFocus = focus;
        minecraft.schedule(minecraft.wrapRunnable(() -> {
            if (minecraft.screen == this) rebuildWidgets();
        }));
    }

    private void show(boolean overview) {
        if (this.overview == overview) return;
        if (!this.overview) saveOffset();
        this.overview = overview;
        status = null;
        queueRebuild(FocusTarget.TABS);
    }

    private void edit(SettingsGroup.Toggle toggle) {
        toggle.edit().accept(!toggle.read().getAsBoolean());
        syncToggles();
        status = null;
        refreshActions();
    }

    private void reset() {
        draft.restoreDefaults();
        syncToggles();
        status = null;
        refreshActions();
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
        if (result != ControlCenterDraft.ApplyResult.SAVED) minecraft.getNarrator().saySystemNow(status);
    }

    private void syncToggles() {
        toggleButtons.forEach((toggle, control) -> {
            boolean enabled = toggle.read().getAsBoolean();
            control.setMessage(toggle.text(enabled));
            control.setSelected(enabled && toggle.available().getAsBoolean());
            control.setLeadingIcon(enabled ? GraphicIcon.SHOW : GraphicIcon.HIDE);
            control.setModeIndicators(2, enabled ? 1 : 0);
        });
    }

    private void refreshActions() {
        if (resetButton == null || applyButton == null) return;
        boolean undo = draft.canUndoReset();
        resetButton.setMessage(label(undo ? "reset_undo" : "reset"));
        resetButton.setLeadingIcon(undo ? GraphicIcon.BACK : GraphicIcon.RESET);
        resetButton.setPreview(label(undo ? "reset_undo_hint" : "reset_hint"));
        resetButton.active = draft.canReset();
        applyButton.active = draft.dirty();
        applyButton.setPreview(status == null ? label("apply_hint") : status);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var preferences = UiPreferences.configured();
        var theme = UiTheme.current();
        int color = preferences.highContrast() ? 0xFF000000 : theme.background(0xFF0B101B);
        graphics.fill(0, 0, width, height, color);
        if (!preferences.highContrast() && width > 0 && height > 0) {
            var palette = new DotMatrixProfile.Palette(color, theme.text(0x72FFFFFF),
                    theme.text(0xFFFFFFFF), 0f);
            background.setProfile(DOTS.withPalette(palette));
            long now = Util.getMillis();
            boolean inside = minecraft.isWindowActive() && mouseX >= 0 && mouseY >= 0 && mouseX < width && mouseY < height;
            if (inside && (!pointerInside || lastPointerX != mouseX || lastPointerY != mouseY)) lastPointerMove = now;
            lastPointerX = mouseX;
            lastPointerY = mouseY;
            pointerInside = inside;
            if (!inside) {
                pointerDown = false;
                background.clearPointer();
            }
            var pointer = new UiPointerTracker.Frame(mouseX, mouseY, inside, pointerDown, false, false, false,
                    inside ? Math.max(0, now - lastPointerMove) : Long.MAX_VALUE);
            background.draw(graphics, new Bounds(0, 0, width, height), pointer, now,
                    preferences.reducedMotion(), false, pulseX, pulseY, pulseAt < 0 ? -1 : (now - pulseAt) / 1000d);
        }
        graphics.nextStratum();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (body == null) {
            graphics.textWithWordWrap(font, label("too_small"), 4, 4, Math.max(1, width - 8), ClientPanels.TEXT);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }
        if (!overview && scrollbar != null) advanceListMotion();
        var preferences = UiPreferences.configured();
        var theme = UiTheme.current();
        int textColor = preferences.highContrast() ? 0xFFFFFFFF : theme.text();
        int accentColor = preferences.highContrast() ? textColor : theme.accent();
        int edge = preferences.highContrast() ? textColor : theme.border();
        var material = GlassMaterial.preset(theme.hudGlass() ? GlassVariant.CLEAR : GlassVariant.REGULAR);
        if (preferences.useBackdropBlur()) {
            LiquidGlassSurface.draw(graphics, layout.left(), layout.top(), layout.width(), layout.height(), 12,
                    theme.surface() & 0xFFFFFF | 0xA0000000, edge, material.optics(), preferences);
        } else {
            RoundedCorners.draw(graphics, layout.left(), layout.top(), layout.width(), layout.height(), 12, 1,
                    edge, preferences.highContrast() ? 0xFF000000 : theme.surface() | 0xFF000000);
        }
        graphics.nextStratum();
        int bodyFill = preferences.highContrast() ? 0xFF000000
                : theme.surface() & 0xFFFFFF | (theme.hudGlass() ? 0x18000000 : 0xB8000000);
        RoundedCorners.draw(graphics, body.x(), body.y(), body.width(), body.height(), 5, 1, edge, bodyFill);
        graphics.nextStratum();
        int titleRight = paging ? layout.pagePreviousX() - 4 : layout.contentX() + layout.contentWidth();
        graphics.enableScissor(layout.contentX(), layout.top(), titleRight, layout.tabsY());
        graphics.text(font, title, layout.contentX(), layout.titleY(), textColor);
        graphics.disableScissor();
        if (paging) {
            int page = overview ? overviewPage : entryPage;
            graphics.centeredText(font, Component.translatable("screen.sh_common_lib.control_center.page", page + 1, pageCount),
                    layout.pagePreviousX() + ControlCenterLayout.PAGE_BUTTON_WIDTH + ControlCenterLayout.PAGE_LABEL_WIDTH / 2,
                    layout.pageY() + (layout.pageHeight() - font.lineHeight) / 2, textColor);
        }
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        try {
            if (overview) {
                int start = overviewPage * overviewPageSize;
                int end = Math.min(overviewLines.size(), start + overviewPageSize);
                int y = body.y() + 4;
                for (int index = start; index < end; index++) {
                    var line = overviewLines.get(index);
                    if (line.text() != null) graphics.text(font, line.text(), layout.contentX(), y,
                            line.heading() ? accentColor : preferences.highContrast() ? textColor
                                    : theme.secondaryText(ClientPanels.secondaryText(), .34f));
                    y += font.lineHeight;
                }
                if (status != null) graphics.text(font, status, layout.contentX(),
                        body.bottom() - font.lineHeight - 2, textColor);
            } else {
                boolean inlineStatus = status != null && statusY + font.lineHeight > body.bottom();
                graphics.text(font, inlineStatus ? status : detail == null ? selectedGroup.title() : detail.title(), layout.contentX(), body.y() + 4,
                        inlineStatus ? textColor : accentColor);
                if (status != null && !inlineStatus) {
                    graphics.textWithWordWrap(font, status, layout.contentX(), statusY, layout.contentWidth(), textColor);
                }
            }
        } finally {
            graphics.disableScissor();
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void saveOffset() {
        if (detail == null) rootOffset = scrolling.offset();
        else detailOffsets.put(detail, scrolling.offset());
    }

    private void enterDetail(SettingsGroup.Subgroup group) {
        saveOffset();
        detail = group;
        queueRebuild(FocusTarget.DETAIL);
    }

    private void leaveDetail() {
        if (detail == null) return;
        saveOffset();
        returnGroup = detail;
        detail = null;
        queueRebuild(FocusTarget.DETAIL_RETURN);
    }

    private void positionRows() {
        if (body == null) return;
        int offset = (int) Math.round(scrolling.offset());
        for (var row : placed) {
            int r = row.index() < 0 ? 0 : row.index() / listColumns + (detail == null ? 0 : 1);
            int c = row.index() < 0 ? 0 : row.index() % listColumns;
            int reveal = (int) Math.round(entrance.offset(r, now(), UiPreferences.configured().reducedMotion()));
            int y = listTop + r * (ControlCenterLayout.ROW + ControlCenterLayout.GAP) - offset + reveal;
            row.button().setX(layout.contentX() + c * (listColumnWidth + ControlCenterLayout.GAP) + row.xOffset());
            row.button().setY(y);
            row.button().visible = y < body.bottom() && y + ControlCenterLayout.ROW > listTop;
        }
        if (scrollbar != null) scrollbar.visible = scrolling.maxOffset() > 0;
    }

    private static long now() { return System.nanoTime() / 1_000_000L; }

    private void advanceListMotion() {
        scrollbar.advanceMotion();
        positionRows();
        var focused = getFocused();
        if (focused == lastListFocus) return;
        lastListFocus = focused;
        for (var row : placed) {
            if (row.button() != focused) continue;
            int y = row.button().getY();
            int adjustment = y < listTop ? y - listTop : Math.max(0, y + ControlCenterLayout.ROW - body.bottom());
            if (adjustment != 0 && scrolling.scrollBy(adjustment)) {
                scrollbar.synchronizeMotion();
                positionRows();
            }
            break;
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (!overview && body != null && scrollbar != null && x >= body.x() && x < body.right()
                && y >= listTop && y < body.bottom() && scrollbar.scrollByWheel(dy)) return true;
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pointerDown = true;
            pulseAt = Util.getMillis();
            pulseX = (float) event.x();
            pulseY = (float) event.y();
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) pointerDown = false;
        return super.mouseReleased(event);
    }

    @Override
    public void removed() {
        if (!overview && body != null) saveOffset();
        background.clearPointer();
        pointerDown = false;
        pointerInside = false;
        toggleButtons.clear();
        super.removed();
    }

    static void openDeveloperSettings(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        var client = Minecraft.getInstance();
        client.setScreen(new Screen(label("developer_settings")) {
            @Override
            protected void init() {
                setInitialFocus(addRenderableWidget(ClientPanels.button((width - 120) / 2,
                        height - ControlCenterLayout.ROW - 8, 120, ControlCenterLayout.ROW,
                        CommonComponents.GUI_BACK, button -> onClose())));
            }

            @Override
            public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                ClientPanels.panel(graphics, 4, 4, Math.max(1, width - 8), Math.max(1, height - 8));
                graphics.centeredText(font, title, width / 2, 10, ClientPanels.TEXT);
                graphics.textWithWordWrap(font, label("developer_empty"), 12, Math.max(24, height / 2 - 8),
                        Math.max(1, width - 24), ClientPanels.secondaryText());
                super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public void onClose() {
                client.setScreen(parent);
                ClientPanels.restoreFocus(parent, returnFocus);
            }
        });
    }

    @Override
    public void onClose() {
        if (!overview && detail != null) { leaveDetail(); return; }
        if (!draft.dirty()) {
            minecraft.setScreen(parent);
            return;
        }
        requestedFocus = FocusTarget.BACK;
        minecraft.setScreen(new ConfirmScreen(discard -> minecraft.setScreen(discard ? parent : this),
                label("discard_title"), label("discard_details"), label("discard_action"), CommonComponents.GUI_CANCEL));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private enum FocusTarget { TABS, GROUPS, PREVIOUS, NEXT, BACK, DETAIL, DETAIL_RETURN }
    private record Placed(UiButton button, int index, int xOffset) {}
    private record OverviewLine(FormattedCharSequence text, boolean heading) {}
}
