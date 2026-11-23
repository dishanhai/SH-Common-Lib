// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.blur.BackgroundBlur;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiVerticalScrollBar;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import com.dishanhai.sh_better_ui.kernel.api.layout.SplitActionRow;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.dishanhai.sh_common_lib.client.control.ControlCenterModule.label;

final class ControlCenterScreen extends Screen {
    private static final Component TITLE = label("title").copy().withStyle(ChatFormatting.BOLD);
    private static final Component COMPACT_TITLE = label("title.compact").copy().withStyle(ChatFormatting.BOLD);
    private static final List<String> FEATURES = List.of("crate", "jei");
    private static final List<UiSectionBar.Section> SECTIONS = List.of(
            new UiSectionBar.Section(label("settings"), GraphicIcon.SETTINGS),
            new UiSectionBar.Section(label("overview"), GraphicIcon.INFO));
    private static ControlCenterPosition lastPosition = ControlCenterPosition.INITIAL;
    private final Screen parent;
    private final GuiEventListener returnFocus;
    private final List<SettingsGroup> groups;
    private final PlayerInfoHud hud;
    private final ControlCenterDraft draft;
    private final Map<SettingsGroup.Page, Button> pageButtons = new IdentityHashMap<>();
    private final List<OverviewLine> overviewLines = new ArrayList<>();
    private SettingsGroup selectedGroup;
    private boolean overview;
    private String status;
    private ControlCenterLayout layout;
    private Bounds body;
    private final VerticalScroll scrolling = new VerticalScroll();
    private final Map<SettingsGroup.Subgroup, Double> detailOffsets = new IdentityHashMap<>();
    private final List<Placed> placed = new ArrayList<>();
    private final List<PlacedHeading> headings = new ArrayList<>();
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
    private UiScrollableSectionBar tabs;
    private UiScrollableSectionBar groupTabs;
    private boolean paging;
    private int pageCount = 1;
    private FocusTarget requestedFocus = FocusTarget.TABS;

    ControlCenterScreen(Screen parent, List<SettingsGroup> groups, PlayerInfoHud hud, ControlCenterDraft draft) {
        super(label("title"));
        this.parent = parent;
        returnFocus = parent == null ? null : parent.getFocused();
        this.groups = List.copyOf(groups);
        this.hud = hud;
        this.draft = draft;
        overview = lastPosition.overview();
        overviewPage = lastPosition.overviewPage();
        selectedGroup = this.groups.get(lastPosition.group());
        var listPosition = lastPosition.list();
        detail = listPosition.resolve(selectedGroup.entries());
        rootOffset = listPosition.rootOffset();
        if (detail != null) detailOffsets.put(detail, listPosition.detailOffset());
    }

    @Override
    protected void repositionElements() {
        if (scrollbar != null) saveOffset();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        draft.refreshHud(hud.settings());
        pageButtons.clear();
        placed.clear();
        headings.clear();
        lastListFocus = null;
        scrollbar = null;
        toggleButtons.clear();
        cancelDrag();
        resetButton = applyButton = null;
        paging = false;
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
        body = new Bounds(x - 3, layout.bodyTop(overview), contentWidth + 6, layout.bodyHeight(overview));
        var preferences = UiPreferences.configured();
        var tabArea = new Bounds(x, layout.tabsY(), layout.tabsWidth(), ControlCenterLayout.ROW);
        if (tabs == null || tabs.getWidth() != layout.tabsWidth()) {
            tabs = ShBetterUiApi.controls().jellySectionBar(tabArea, preferences, SECTIONS,
                    overview ? 1 : 0, index -> show(index == 1));
            tabs.buttons().get(1).setTooltip(Tooltip.create(label("overview.hint")));
        } else {
            tabs.setBounds(tabArea);
            tabs.setPreferences(preferences);
        }
        addRenderableWidget(tabs);
        GuiEventListener focus = tabs;
        int pages;
        int currentPage;
        if (overview) {
            prepareOverview(contentWidth);
            pages = ControlCenterLayout.pages(overviewLines.size(), overviewPageSize);
            overviewPage = Math.min(overviewPage, pages - 1);
            currentPage = overviewPage;
        } else {
            var groupArea = new Bounds(x, layout.groupsY(), contentWidth, ControlCenterLayout.ROW);
            if (groupTabs == null) {
                var sections = groups.stream().map(group -> new UiSectionBar.Section(group.navigationTitle(), group.icon())).toList();
                groupTabs = ShBetterUiApi.controls().scrollableSectionBar(groupArea, preferences, sections,
                        groups.indexOf(selectedGroup), this::showGroup).withJellyRadio();
                for (int index = 0; index < groups.size(); index++) {
                    groupTabs.buttons().get(index).setTooltip(Tooltip.create(groups.get(index).title()));
                }
            } else {
                groupTabs.setBounds(groupArea);
                groupTabs.setPreferences(preferences);
            }
            addRenderableWidget(groupTabs);
            if (requestedFocus == FocusTarget.GROUPS) focus = groupTabs;
            listTop = body.y() + 4 + font.lineHeight + 4;
            listColumns = ControlCenterLayout.settingsColumns(selectedGroup.twoColumns(), contentWidth);
            int listWidth = Math.max(0, contentWidth - ControlCenterLayout.SETTINGS_SCROLL_GUTTER);
            listColumnWidth = (listWidth - (listColumns - 1) * ControlCenterLayout.GAP) / listColumns;
            var entries = detail == null ? selectedGroup.entries() : detail.entries();
            var rowsLayout = SettingsRows.of(entries, listColumns);
            int rows = rowsLayout.rowCount() + (detail == null ? 0 : 1);
            scrolling.resize(Math.max(0, body.bottom() - listTop),
                    Math.max(0, rows * (ControlCenterLayout.ROW + ControlCenterLayout.GAP) - ControlCenterLayout.GAP));
            scrolling.scrollTo(detail == null ? rootOffset : detailOffsets.getOrDefault(detail, 0d));
            if (detail != null) {
                var backDetail = addClippedButton(button(x, listTop, listWidth, CommonComponents.GUI_BACK,
                        GraphicIcon.BACK, this::leaveDetail));
                backDetail.setTabOrderGroup(1);
                placed.add(new Placed(backDetail, -1, 0));
                if (requestedFocus == FocusTarget.DETAIL) focus = backDetail;
            }
            for (int index = 0; index < entries.size(); index++) {
                var entry = entries.get(index);
                int slot = rowsLayout.slot(index);
                if (entry instanceof SettingsGroup.Heading heading) {
                    headings.add(new PlacedHeading(heading.title(), slot));
                    continue;
                }
                var subgroup = entry instanceof SettingsGroup.Subgroup group ? group : null;
                var split = subgroup != null && subgroup.hasToggle()
                        ? SplitActionRow.of(listColumnWidth, ControlCenterLayout.ROW, 2) : null;
                int entryWidth = split == null ? listColumnWidth : split.actionWidth();
                var toggle = entry instanceof SettingsGroup.Toggle t ? t
                        : subgroup == null ? null : subgroup.toggle();
                if (toggle != null) {
                    var toggleControl = ShBetterUiApi.controls().toggle(
                            new Bounds(x, listTop, entryWidth, ControlCenterLayout.ROW),
                            toggle.read().getAsBoolean(), preferences, UiStyle.LIQUID_GLASS, toggle::text,
                            enabled -> edit(toggle, enabled));
                    toggleControl.setIcons(GraphicIcon.SHOW, GraphicIcon.HIDE);
                    var control = addClippedButton(toggleControl.button());
                    control.setSampleBackground(false);
                    control.active = toggle.available().getAsBoolean();
                    if (!control.active) control.setSelected(false);
                    control.setTabOrderGroup(1);
                    toggleButtons.put(toggle, toggleControl);
                    placed.add(new Placed(control, slot, 0));
                } else if (entry instanceof SettingsGroup.Page page) {
                    var control = addClippedButton(button(x, listTop, entryWidth, page.label(), page.icon(),
                            () -> page.open().accept(this, () -> pageButtons.get(page))));
                    control.setTabOrderGroup(1);
                    pageButtons.put(page, control);
                    var unavailable = page.unavailable().get();
                    control.active = unavailable == null;
                    if (unavailable != null) control.setTooltip(Tooltip.create(unavailable));
                    placed.add(new Placed(control, slot, 0));
                }
                if (subgroup != null) {
                    var title = split == null ? subgroup.title()
                            : Component.translatable("screen.sh_common_lib.control_center.enter_group", subgroup.title());
                    var link = addClippedButton(button(x, listTop, split == null ? listWidth : split.disclosureWidth(),
                            title, GraphicIcon.NEXT, () -> enterDetail(subgroup)));
                    if (split != null) {
                        link.setIcon(GraphicIcon.NEXT);
                        link.active = subgroup.toggle().available().getAsBoolean();
                    }
                    link.setTabOrderGroup(1);
                    placed.add(new Placed(link, slot, split == null ? 0 : split.disclosureX()));
                    if (requestedFocus == FocusTarget.DETAIL_RETURN && returnGroup == subgroup) focus = link;
                }
            }
            scrollbar = addRenderableWidget(ShBetterUiApi.controls().verticalScrollBar(
                    new Bounds(x + contentWidth - 8, listTop, 8, Math.max(0, body.bottom() - listTop)),
                    label("scrollbar"), scrolling, ControlCenterLayout.ROW + ControlCenterLayout.GAP, this::positionRows));
            scrollbar.setTabOrderGroup(1);
            scrollbar.setSmoothScrolling(() -> UiPreferences.configured().reducedMotion());
            positionRows();
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
        int actionWidth = layout.footerActionWidth();
        var back = addRenderableWidget(button(x, layout.footerY(), actionWidth, CommonComponents.GUI_BACK,
                GraphicIcon.BACK, this::onClose));
        resetButton = addRenderableWidget(button(layout.footerResetX(), layout.footerY(), actionWidth,
                label("reset"), GraphicIcon.RESET, this::reset));
        applyButton = addRenderableWidget(button(layout.footerApplyX(), layout.footerY(), actionWidth,
                label("apply"), GraphicIcon.SUCCESS, this::apply, true));
        back.setTabOrderGroup(2);
        resetButton.setTabOrderGroup(2);
        applyButton.setTabOrderGroup(2);
        if (requestedFocus == FocusTarget.BACK) focus = back;
        refreshActions();
        setInitialFocus(focus);
        requestedFocus = FocusTarget.TABS;
    }

    private final Map<SettingsGroup.Toggle, UiToggleButton> toggleButtons = new IdentityHashMap<>();

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
            font.split(label(feature).copy().withStyle(ChatFormatting.BOLD), width)
                    .forEach(line -> overviewLines.add(new OverviewLine(line, true)));
            font.split(label(feature + ".details"), width).forEach(line -> overviewLines.add(new OverviewLine(line, false)));
        }
        overviewPageSize = layout.overviewPageSize(font.lineHeight);
    }

    private void movePage(int direction) {
        overviewPage += direction;
        status = null;
        queueRebuild(direction < 0 ? FocusTarget.PREVIOUS : FocusTarget.NEXT);
    }

    private void queueRebuild(FocusTarget focus) {
        requestedFocus = focus;
        minecraft.schedule(minecraft.wrapRunnable(() -> {
            if (minecraft.screen == this) rebuildWidgets();
        }));
    }

    private void showGroup(int index) {
        if (selectedGroup == groups.get(index) && detail == null) return;
        saveOffset();
        selectedGroup = groups.get(index);
        detail = null;
        rootOffset = 0;
        detailOffsets.clear();
        status = null;
        queueRebuild(FocusTarget.GROUPS);
    }

    private void show(boolean overview) {
        if (this.overview == overview) return;
        if (!this.overview) saveOffset();
        this.overview = overview;
        status = null;
        queueRebuild(FocusTarget.TABS);
    }

    private void edit(SettingsGroup.Toggle toggle, boolean enabled) {
        toggle.edit().accept(enabled);
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
                com.dishanhai.sh_common_lib.client.jei.JeiSearchPreferences::save, ArtisanControlSettings::save,
                com.dishanhai.sh_common_lib.client.item.ArtisanAppearancePreferences::save,
                com.dishanhai.sh_common_lib.client.voice.VoiceItemPreferences::save);
        status = switch (result) {
            case SAVED -> "saved";
            case PARTIAL -> "partially_saved";
            case FAILED -> "save_failed";
        };
        refreshActions();
        if (result != ControlCenterDraft.ApplyResult.SAVED) minecraft.getNarrator().saySystemNow(label(status));
    }

    private void syncToggles() {
        toggleButtons.forEach((toggle, control) -> {
            control.setValue(toggle.read().getAsBoolean());
            control.button().active = toggle.available().getAsBoolean();
            if (!control.button().active) control.button().setSelected(false);
        });
    }

    private void refreshActions() {
        if (resetButton == null || applyButton == null) return;
        boolean undo = draft.canUndoReset();
        String resetKey = undo ? "reset_undo" : "reset";
        resetButton.setMessage(label(layout.compactFooter() ? resetKey + ".compact" : resetKey));
        resetButton.setLeadingIcon(undo ? GraphicIcon.BACK : GraphicIcon.RESET);
        resetButton.setPreview(label(undo ? "reset_undo_hint" : "reset_hint"));
        resetButton.active = draft.canReset();
        applyButton.active = draft.dirty();
        applyButton.setPreview(label(status == null ? "apply_hint" : status));
    }

    private Component statusText(int availableWidth) {
        var message = label(status);
        return font.width(message) <= availableWidth ? message : label(status + ".compact");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var preferences = UiPreferences.configured();
        if (minecraft.level == null) extractPanorama(graphics, partialTick);
        BackgroundBlur.request(graphics, preferences);
        graphics.fill(0, 0, width, height, preferences.highContrast() ? 0xFF000000 : 0x30121A26);
        graphics.nextStratum();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (body == null) {
            graphics.textWithWordWrap(font, label("too_small"), 4, 4, Math.max(1, width - 8), ClientPanels.TEXT);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }
        var preferences = UiPreferences.configured();
        var theme = UiTheme.current();
        int textColor = preferences.highContrast() ? 0xFFFFFFFF : theme.text();
        int accentColor = preferences.highContrast() ? textColor : theme.accent();
        int secondaryColor = preferences.highContrast() ? textColor : theme.secondaryText(ClientPanels.secondaryText(), .34f);
        int edge = preferences.highContrast() ? textColor : theme.border();
        if (!overview && scrollbar != null) advanceListMotion();
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
        int contentRight = layout.contentX() + layout.contentWidth();
        int titleRight = paging ? layout.pagePreviousX() - 4 : contentRight;
        if (status != null && layout.compact() && !overview) {
            var message = statusText(layout.contentWidth() - font.width(COMPACT_TITLE) - ControlCenterLayout.GAP);
            int messageX = titleRight - font.width(message);
            graphics.text(font, message, messageX, layout.titleY(), textColor);
            titleRight = messageX - ControlCenterLayout.GAP;
        }
        var screenTitle = font.width(TITLE) <= titleRight - layout.contentX() ? TITLE : COMPACT_TITLE;
        graphics.enableScissor(layout.contentX(), layout.top(), titleRight, layout.tabsY());
        graphics.text(font, screenTitle, layout.contentX(), layout.titleY(), textColor);
        graphics.disableScissor();
        if (paging) {
            graphics.centeredText(font, Component.translatable("screen.sh_common_lib.control_center.page", overviewPage + 1, pageCount),
                    layout.pagePreviousX() + ControlCenterLayout.PAGE_BUTTON_WIDTH + ControlCenterLayout.PAGE_LABEL_WIDTH / 2,
                    layout.pageY() + (layout.pageHeight() - font.lineHeight) / 2, secondaryColor);
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
                            line.heading() ? accentColor : secondaryColor);
                    y += font.lineHeight;
                }
                if (layout.compact() && status != null) {
                    var message = statusText(layout.contentWidth());
                    graphics.text(font, message, contentRight - font.width(message),
                            body.bottom() - font.lineHeight - 2, textColor);
                }
            } else {
                Component heading = detail == null ? selectedGroup.title() : detail.title();
                graphics.enableScissor(layout.contentX(), body.y(), contentRight, listTop);
                graphics.text(font, heading.copy().withStyle(ChatFormatting.BOLD), layout.contentX(), body.y() + 4, textColor);
                graphics.disableScissor();
                int separatorY = body.y() + 4 + font.lineHeight + 1;
                graphics.fill(layout.contentX(), separatorY, layout.contentX() + layout.contentWidth(), separatorY + 1, edge);
                renderSettingsHeadings(graphics, accentColor);
            }
        } finally {
            graphics.disableScissor();
        }
        if (!layout.compact() && status != null) {
            int statusY = layout.footerStatusY(font.lineHeight);
            var message = statusText(layout.contentWidth());
            graphics.enableScissor(layout.contentX(), statusY, contentRight, layout.footerY());
            graphics.text(font, message, contentRight - font.width(message), statusY, textColor);
            graphics.disableScissor();
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSettingsHeadings(GuiGraphicsExtractor graphics, int color) {
        if (headings.isEmpty()) return;
        int left = layout.contentX();
        graphics.enableScissor(left, listTop, left + Math.max(0, layout.contentWidth() - 12), body.bottom());
        try {
            int offset = (int) Math.round(scrolling.offset());
            for (var heading : headings) {
                int y = rowY(heading.slot(), offset);
                graphics.text(font, heading.title(), left, y + (ControlCenterLayout.ROW - font.lineHeight) / 2, color);
            }
        } finally {
            graphics.disableScissor();
        }
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
            int c = row.index() < 0 ? 0 : row.index() % listColumns;
            int y = rowY(row.index(), offset);
            row.button().setX(layout.contentX() + c * (listColumnWidth + ControlCenterLayout.GAP) + row.xOffset());
            row.button().setY(y);
            row.button().visible = y < body.bottom() && y + ControlCenterLayout.ROW > listTop;
        }
        if (scrollbar != null) scrollbar.visible = scrolling.maxOffset() > 0;
    }

    private int rowY(int slot, int offset) {
        int row = slot < 0 ? 0 : slot / listColumns + (detail == null ? 0 : 1);
        return listTop + row * (ControlCenterLayout.ROW + ControlCenterLayout.GAP) - offset;
    }

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
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && getFocused() instanceof UiScrollableSectionBar bar && bar.mouseReleased(event)) {
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && getFocused() instanceof UiScrollableSectionBar bar && bar.cancelDrag()) {
            setDragging(false);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (!overview && body != null && scrollbar != null && x >= body.x() && x < body.right()
                && y >= listTop && y < body.bottom() && scrollbar.scrollByWheel(dy)) return true;
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public void removed() {
        if (scrollbar != null) saveOffset();
        var listPosition = new ControlCenterPosition.ListPosition(detail == null ? -1 : selectedGroup.entries().indexOf(detail),
                rootOffset, detail == null ? 0d : detailOffsets.getOrDefault(detail, 0d));
        lastPosition = new ControlCenterPosition(overview, groups.indexOf(selectedGroup), overviewPage, listPosition);
        cancelDrag();
        super.removed();
    }


    @Override
    public void onClose() {
        cancelDrag();
        if (!overview && detail != null) { leaveDetail(); return; }
        if (!draft.dirty()) {
            returnToParent();
            return;
        }
        requestedFocus = FocusTarget.BACK;
        minecraft.setScreen(new ConfirmScreen(discard -> {
            if (discard) returnToParent();
            else minecraft.setScreen(this);
        },
                label("discard_title"), label("discard_details"), label("discard_action"), CommonComponents.GUI_CANCEL));
    }

    private void cancelDrag() {
        if (tabs != null) tabs.cancelDrag();
        if (groupTabs != null) groupTabs.cancelDrag();
        setDragging(false);
    }

    private void returnToParent() {
        minecraft.setScreen(parent);
        if (parent != null) ClientPanels.restoreFocus(parent, () -> returnFocus);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private enum FocusTarget { TABS, GROUPS, PREVIOUS, NEXT, BACK, DETAIL, DETAIL_RETURN }
    private record Placed(UiButton button, int index, int xOffset) {}
    private record PlacedHeading(Component title, int slot) {}
    private record OverviewLine(FormattedCharSequence text, boolean heading) {}
}
