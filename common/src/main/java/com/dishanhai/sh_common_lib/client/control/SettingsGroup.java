// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiVerticalScrollBar;
import com.dishanhai.sh_better_ui.kernel.api.layout.SplitActionRow;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Built-in control center settings, declared in display order by SHCLIB. */
final class SettingsGroup {
    sealed interface Entry permits Toggle, Page, Subgroup, Heading {}
    record Heading(Component title) implements Entry {}
    record Toggle(Function<Boolean, Component> label, BooleanSupplier read, Consumer<Boolean> edit,
                  BooleanSupplier available) implements Entry {
        Toggle(Function<Boolean, Component> label, BooleanSupplier read, Consumer<Boolean> edit) {
            this(label, read, edit, () -> true);
        }
        Component text(boolean enabled) {
            var value = label.apply(enabled);
            return available.getAsBoolean() ? value : Component.translatable(
                    "screen.sh_common_lib.control_center.not_enabled", value);
        }
    }
    record Page(Component label, GraphicIcon icon, Supplier<Component> unavailable,
                BiConsumer<Screen, Supplier<? extends GuiEventListener>> open) implements Entry {}
    record Subgroup(Component title, Toggle toggle, List<Entry> entries) implements Entry {
        Subgroup { entries = List.copyOf(entries); }
        Subgroup(Component title, List<Entry> entries) { this(title, null, entries); }
        boolean hasToggle() { return toggle != null; }
    }

    private final Component title;
    private final UiSectionBar.Section section;
    private final List<Entry> entries;
    private final boolean twoColumns;
    private final Map<Entry, UiButton> buttons = new IdentityHashMap<>();
    private final Map<Toggle, UiToggleButton> toggles = new IdentityHashMap<>();
    private final Map<Subgroup, Double> offsets = new IdentityHashMap<>();
    private final List<Placed> placed = new ArrayList<>();
    private final List<PlacedHeading> headings = new ArrayList<>();
    private final VerticalScroll scroll = new VerticalScroll();
    private Subgroup detail;
    private Subgroup returnFocus;
    private UiScreen screen;
    private Bounds area;
    private UiVerticalScrollBar scrollbar;
    private int firstRow, columns, rowWidth, viewportHeight;
    private double rootOffset;
    private boolean visible;
    private Runnable beforeDetailChange;
    private boolean manualRender;
    private GuiEventListener lastFocus;

    SettingsGroup(Component title, GraphicIcon icon, List<Entry> entries) { this(title, icon, entries, false); }
    SettingsGroup(Component title, GraphicIcon icon, List<Entry> entries, boolean twoColumns) {
        this(title, title, icon, entries, twoColumns);
    }
    SettingsGroup(Component title, Component navigationTitle, GraphicIcon icon, List<Entry> entries, boolean twoColumns) {
        this.title = title;
        this.section = new UiSectionBar.Section(navigationTitle, icon);
        this.entries = List.copyOf(entries);
        this.twoColumns = twoColumns;
    }
    Component title() { return title; }
    UiSectionBar.Section section() { return section; }

    void restorePosition(ControlCenterPosition.ListPosition position) {
        detail = position.resolve(entries);
        rootOffset = position.rootOffset();
        if (detail != null) offsets.put(detail, position.detailOffset());
    }

    ControlCenterPosition.ListPosition position() {
        return new ControlCenterPosition.ListPosition(detail == null ? -1 : entries.indexOf(detail),
                rootOffset, detail == null ? 0d : offsets.getOrDefault(detail, 0d));
    }

    int page() { return 0; }
    int pages() { return 1; }
    void changePage(int direction) {}

    void init(UiScreen screen, Font font, Bounds area, Runnable onChange, Runnable beforeDetailChange) {
        this.screen = screen;
        this.area = area;
        this.beforeDetailChange = beforeDetailChange;
        firstRow = area.y() + font.lineHeight + 4;
        viewportHeight = Math.max(0, area.bottom() - firstRow);
        columns = ControlCenterLayout.settingsColumns(twoColumns, area.width());
        int contentWidth = Math.max(0, area.width() - ControlCenterLayout.SETTINGS_SCROLL_GUTTER);
        rowWidth = (contentWidth - (columns - 1) * ControlCenterLayout.GAP) / columns;
        var preferences = screen.context().preferences();
        var rows = SettingsRows.of(entries, columns);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            addEntry(entry, null, rows.slot(i), onChange);
            if (entry instanceof Subgroup subgroup) {
                var back = new UiButton(0, 0, contentWidth, ControlCenterLayout.ROW, CommonComponents.GUI_BACK,
                        ignored -> leaveDetail(), preferences, false, UiStyle.LIQUID_GLASS);
                back.setSampleBackground(false);
                back.setLeadingIcon(GraphicIcon.BACK);
                back.setTabOrderGroup(1);
                placed.add(new Placed(screen.addClipped(back, this::nativeRowClip), subgroup, -1, 0));
                var detailRows = SettingsRows.of(subgroup.entries(), columns);
                for (int child = 0; child < subgroup.entries().size(); child++) {
                    addEntry(subgroup.entries().get(child), subgroup, detailRows.slot(child), onChange);
                }
            }
        }
        scrollbar = screen.addClipped(new UiVerticalScrollBar(new Bounds(area.right() - 8, firstRow, 8, viewportHeight),
                ControlCenterModule.label("scrollbar"), scroll, ControlCenterLayout.ROW + ControlCenterLayout.GAP,
                this::layoutButtons), () -> manualRender ? new Bounds(0, 0, 0, 0) : area);
        scrollbar.setTabOrderGroup(1);
        scrollbar.setSmoothScrolling(() -> screen.context().preferences().reducedMotion());
        resizeScroll();
        updateButtons();
    }

    private void addEntry(Entry entry, Subgroup view, int index, Runnable onChange) {
        if (entry instanceof Heading heading) {
            headings.add(new PlacedHeading(heading.title(), view, index));
            return;
        }
        var preferences = screen.context().preferences();
        var subgroup = entry instanceof Subgroup group ? group : null;
        var split = subgroup != null && subgroup.hasToggle()
                ? SplitActionRow.of(rowWidth, ControlCenterLayout.ROW, 2) : null;
        int width = split == null ? rowWidth : split.actionWidth();
        Toggle toggle = entry instanceof Toggle value ? value : subgroup == null ? null : subgroup.toggle();
        UiButton button;
        if (toggle != null) {
            var control = new UiToggleButton(new Bounds(0, 0, width, ControlCenterLayout.ROW),
                    toggle.read().getAsBoolean(), preferences, UiStyle.LIQUID_GLASS, toggle::text, enabled -> {
                        toggle.edit().accept(enabled);
                        syncControls();
                        onChange.run();
                    });
            control.setIcons(GraphicIcon.SHOW, GraphicIcon.HIDE);
            control.setModeIndicatorsVisible(true);
            button = control.button();
            button.active = toggle.available().getAsBoolean();
            if (!button.active) button.setSelected(false);
            toggles.put(toggle, control);
        } else if (entry instanceof Page page) {
            var reason = page.unavailable().get();
            button = new UiButton(0, 0, width, ControlCenterLayout.ROW, page.label(), ignored ->
                    page.open().accept(screen, () -> buttons.get(page)), preferences, false, UiStyle.LIQUID_GLASS);
            button.setLeadingIcon(page.icon());
            button.active = reason == null;
            if (reason != null) button.setPreview(reason);
        } else if (subgroup != null) {
            button = new UiButton(0, 0, Math.max(0, area.width() - ControlCenterLayout.SETTINGS_SCROLL_GUTTER), ControlCenterLayout.ROW,
                    subgroup.title(), ignored -> enterDetail(subgroup), preferences, false, UiStyle.LIQUID_GLASS);
            button.setLeadingIcon(GraphicIcon.NEXT);
        } else throw new IllegalStateException("Unknown settings entry");
        button.setSampleBackground(false);
        button.setTabOrderGroup(1);
        buttons.put(entry, screen.addClipped(button, this::nativeRowClip));
        placed.add(new Placed(button, view, index, 0));
        if (split != null) {
            var arrow = new UiButton(0, 0, split.disclosureWidth(), ControlCenterLayout.ROW,
                    Component.translatable("screen.sh_common_lib.control_center.enter_group", subgroup.title()),
                    ignored -> enterDetail(subgroup), preferences, false, UiStyle.LIQUID_GLASS);
            arrow.setSampleBackground(false);
            arrow.setIcon(GraphicIcon.NEXT);
            arrow.active = subgroup.toggle().available().getAsBoolean();
            arrow.setTabOrderGroup(1);
            buttons.put(subgroup, screen.addClipped(arrow, this::nativeRowClip));
            placed.add(new Placed(arrow, view, index, split.disclosureX()));
        }
    }

    private void saveOffset() { if (detail == null) rootOffset = scroll.offset(); else offsets.put(detail, scroll.offset()); }
    private void resizeScroll() {
        var rowsLayout = SettingsRows.of(detail == null ? entries : detail.entries(), columns);
        int rows = rowsLayout.rowCount() + (detail == null ? 0 : 1);
        scroll.resize(viewportHeight, Math.max(0, rows * (ControlCenterLayout.ROW + ControlCenterLayout.GAP) - ControlCenterLayout.GAP));
        scroll.scrollTo(detail == null ? rootOffset : offsets.getOrDefault(detail, 0d));
        if (scrollbar != null) scrollbar.synchronizeMotion();
    }
    private void enterDetail(Subgroup group) {
        beforeDetailChange.run();
        saveOffset(); detail = group; resizeScroll(); updateButtons();
        placed.stream().filter(p -> p.view() == group && p.index() == -1).findFirst().ifPresent(p -> screen.setFocused(p.button()));
    }
    boolean leaveDetail() {
        if (detail == null) return false;
        if (screen != null) beforeDetailChange.run();
        Subgroup previous = detail;
        returnFocus = previous;
        if (scrollbar != null) saveOffset();
        detail = null;
        if (scrollbar != null) {
            resizeScroll();
            updateButtons();
        }
        var entry = buttons.get(previous);
        if (screen != null && entry != null && entry.visible) { screen.setFocused(entry); returnFocus = null; }
        return true;
    }
    boolean restoreFocus() {
        if (screen == null || returnFocus == null) return false;
        var entry = buttons.get(returnFocus);
        returnFocus = null;
        if (entry == null || !entry.visible) return false;
        screen.setFocused(entry);
        return true;
    }
    boolean scroll(double x, double y, double delta) {
        return !manualRender && visible && area != null && x >= area.x() && x < area.right() && y >= firstRow && y < area.bottom()
                && scrollbar.scrollByWheel(delta);
    }
    void syncControls() {
        toggles.forEach((toggle, control) -> {
            control.setValue(toggle.read().getAsBoolean());
            if (!toggle.available().getAsBoolean()) control.button().setSelected(false);
        });
    }
    void setVisible(boolean value) {
        visible = value;
        updateButtons();
    }

    private Bounds rowClip() { return new Bounds(area.x(), firstRow, Math.max(0, area.width() - 12), viewportHeight); }
    private Bounds nativeRowClip() { return manualRender ? new Bounds(0, 0, 0, 0) : rowClip(); }
    void setManualRender(boolean manualRender) { this.manualRender = manualRender; }

    boolean containsFocus(GuiEventListener focus) {
        return focus != null && (focus == scrollbar || placed.stream().anyMatch(row -> row.button() == focus));
    }

    void advanceMotion() {
        if (!visible || scrollbar == null) return;
        scrollbar.advanceMotion();
        layoutButtons();
        var focused = screen.getFocused();
        if (focused != lastFocus) {
            lastFocus = focused;
            for (var p : placed) {
                if (p.button() != focused || p.view() != detail) continue;
                int y = p.button().getY();
                int adjustment = y < firstRow ? y - firstRow
                        : Math.max(0, y + ControlCenterLayout.ROW - area.bottom());
                if (adjustment != 0 && scroll.scrollBy(adjustment)) {
                    scrollbar.synchronizeMotion();
                    layoutButtons();
                }
                break;
            }
        }
    }
    private void layoutButtons() {
        if (area == null) return;
        int offset = (int) Math.round(scroll.offset());
        for (var p : placed) {
            int col = p.index() < 0 ? 0 : p.index() % columns;
            int y = rowY(p.index(), p.view(), offset);
            p.button().setX(area.x() + col * (rowWidth + ControlCenterLayout.GAP) + p.xOffset());
            p.button().setY(y);
            p.button().visible = visible && p.view() == detail && y < area.bottom()
                    && y + ControlCenterLayout.ROW > firstRow;
        }
        if (scrollbar != null) scrollbar.visible = visible && scroll.maxOffset() > 0;
    }

    private int rowY(int slot, Subgroup view, int offset) {
        int row = slot < 0 ? 0 : slot / columns + (view == null ? 0 : 1);
        return firstRow + row * (ControlCenterLayout.ROW + ControlCenterLayout.GAP) - offset;
    }
    private void updateButtons() { layoutButtons(); }
    void clearWidgets() {
        if (scrollbar != null) saveOffset();
        buttons.clear(); toggles.clear(); placed.clear(); headings.clear(); scrollbar = null; screen = null;
        beforeDetailChange = null;
        visible = false; lastFocus = null; manualRender = false;
    }
    void render(GuiGraphics graphics, Font font) {
        Component heading = detail == null ? title : detail.title();
        boolean highContrast = screen.context().preferences().highContrast();
        graphics.enableScissor(area.x(), area.y(), area.right(), area.y() + font.lineHeight);
        try {
            graphics.drawString(font, heading.copy().withStyle(ChatFormatting.BOLD),
                    area.x(), area.y(), LiquidGlassTheme.textColor(), false);
        } finally {
            graphics.disableScissor();
        }
        int dividerY = area.y() + font.lineHeight + 1;
        graphics.fill(area.x(), dividerY, area.right(), dividerY + 1,
                highContrast ? LiquidGlassTheme.textColor() : DarkModeTheme.borderColor());
        if (headings.isEmpty()) return;
        var clip = rowClip();
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        try {
            int offset = (int) Math.round(scroll.offset());
            for (var sectionHeading : headings) {
                if (sectionHeading.view() != detail) continue;
                int y = rowY(sectionHeading.slot(), sectionHeading.view(), offset);
                graphics.drawString(font, sectionHeading.title(), area.x(),
                        y + (ControlCenterLayout.ROW - font.lineHeight) / 2,
                        highContrast ? LiquidGlassTheme.textColor() : LiquidGlassTheme.accentColor(), false);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    void renderWidgets(GuiGraphics graphics, float partialTick) {
        var clip = rowClip();
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        try {
            for (var row : placed) row.button().render(graphics, -1, -1, partialTick);
        } finally {
            graphics.disableScissor();
        }
        scrollbar.render(graphics, -1, -1, partialTick);
    }

    void addSkeletonRows(List<UiSkeleton.Block> blocks) {
        for (var row : placed) {
            var button = row.button();
            if (!button.visible) continue;
            int left = button.getX() + 2, right = button.getX() + button.getWidth() - 2;
            int top = Math.max(firstRow, button.getY() + 3);
            int bottom = Math.min(area.bottom(), button.getY() + button.getHeight() - 3);
            if (right > left && bottom > top) blocks.add(new UiSkeleton.Block(
                    new Bounds(left, top, right - left, bottom - top), 8f));
        }
    }
    private record Placed(UiButton button, Subgroup view, int index, int xOffset) {}
    private record PlacedHeading(Component title, Subgroup view, int slot) {}
}
