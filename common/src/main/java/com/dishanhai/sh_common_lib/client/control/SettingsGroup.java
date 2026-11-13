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
import com.dishanhai.sh_better_ui.client.gui.scaled.UiVerticalScrollBar;
import com.dishanhai.sh_better_ui.kernel.api.layout.SplitActionRow;
import com.dishanhai.sh_better_ui.kernel.api.layout.ListEntrance;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
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
    sealed interface Entry permits Toggle, Page, Subgroup {}
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
    }

    private final UiSectionBar.Section section;
    private final List<Entry> entries;
    private final boolean twoColumns;
    private final Map<Entry, UiButton> buttons = new IdentityHashMap<>();
    private final Map<Toggle, UiToggleButton> toggles = new IdentityHashMap<>();
    private final Map<Subgroup, Double> offsets = new IdentityHashMap<>();
    private final List<Placed> placed = new ArrayList<>();
    private final VerticalScroll scroll = new VerticalScroll();
    private final ListEntrance entrance = new ListEntrance();
    private Subgroup detail;
    private Subgroup returnFocus;
    private UiScreen screen;
    private Bounds area;
    private UiVerticalScrollBar scrollbar;
    private int firstRow, columns, rowWidth, viewportHeight;
    private double rootOffset;
    private boolean visible;
    private GuiEventListener lastFocus;

    SettingsGroup(Component title, GraphicIcon icon, List<Entry> entries) { this(title, icon, entries, false); }
    SettingsGroup(Component title, GraphicIcon icon, List<Entry> entries, boolean twoColumns) {
        this.section = new UiSectionBar.Section(title, icon);
        this.entries = List.copyOf(entries);
        this.twoColumns = twoColumns;
    }
    UiSectionBar.Section section() { return section; }
    int page() { return 0; }
    int pages() { return 1; }
    void changePage(int direction) {}

    void init(UiScreen screen, Font font, Bounds area, Runnable onChange) {
        this.screen = screen;
        this.area = area;
        firstRow = area.y() + font.lineHeight + 4;
        viewportHeight = Math.max(0, area.bottom() - firstRow);
        columns = twoColumns && area.width() >= 164 ? 2 : 1;
        int contentWidth = Math.max(0, area.width() - 12);
        rowWidth = (contentWidth - (columns - 1) * ControlCenterLayout.GAP) / columns;
        var preferences = screen.context().preferences();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            addEntry(entry, null, i, onChange);
            if (entry instanceof Subgroup subgroup) {
                var back = new UiButton(0, 0, contentWidth, ControlCenterLayout.ROW, CommonComponents.GUI_BACK,
                        ignored -> leaveDetail(), preferences, false, UiStyle.LIQUID_GLASS);
                back.setSampleBackground(false);
                back.setLeadingIcon(GraphicIcon.BACK);
                back.setTabOrderGroup(1);
                placed.add(new Placed(screen.addClipped(back, this::rowClip), subgroup, -1, 0));
                for (int child = 0; child < subgroup.entries().size(); child++) {
                    addEntry(subgroup.entries().get(child), subgroup, child, onChange);
                }
            }
        }
        scrollbar = screen.add(new UiVerticalScrollBar(new Bounds(area.right() - 8, firstRow, 8, viewportHeight),
                ControlCenterModule.label("scrollbar"), scroll, ControlCenterLayout.ROW + ControlCenterLayout.GAP,
                this::layoutButtons));
        scrollbar.setTabOrderGroup(1);
        scrollbar.setSmoothScrolling(() -> screen.context().preferences().reducedMotion());
        resizeScroll();
        updateButtons();
    }

    private void addEntry(Entry entry, Subgroup view, int index, Runnable onChange) {
        var preferences = screen.context().preferences();
        var split = entry instanceof Subgroup ? SplitActionRow.of(rowWidth, ControlCenterLayout.ROW, 2) : null;
        int width = split == null ? rowWidth : split.actionWidth();
        Toggle toggle = entry instanceof Toggle value ? value : entry instanceof Subgroup group ? group.toggle() : null;
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
        } else throw new IllegalStateException("Unknown settings entry");
        button.setSampleBackground(false);
        button.setTabOrderGroup(1);
        buttons.put(entry, screen.addClipped(button, this::rowClip));
        placed.add(new Placed(button, view, index, 0));
        if (entry instanceof Subgroup group) {
            var arrow = new UiButton(0, 0, split.disclosureWidth(), ControlCenterLayout.ROW,
                    Component.translatable("screen.sh_common_lib.control_center.enter_group", group.title()),
                    ignored -> enterDetail(group), preferences, false, UiStyle.LIQUID_GLASS);
            arrow.setSampleBackground(false);
            arrow.setIcon(GraphicIcon.NEXT);
            arrow.active = group.toggle().available().getAsBoolean();
            arrow.setTabOrderGroup(1);
            buttons.put(group.toggle(), screen.addClipped(arrow, this::rowClip));
            placed.add(new Placed(arrow, view, index, split.disclosureX()));
        }
    }

    private void saveOffset() { if (detail == null) rootOffset = scroll.offset(); else offsets.put(detail, scroll.offset()); }
    private void resizeScroll() {
        int count = detail == null ? entries.size() : detail.entries().size();
        int rows = (count + columns - 1) / columns + (detail == null ? 0 : 1);
        scroll.resize(viewportHeight, Math.max(0, rows * (ControlCenterLayout.ROW + ControlCenterLayout.GAP) - ControlCenterLayout.GAP));
        scroll.scrollTo(detail == null ? rootOffset : offsets.getOrDefault(detail, 0d));
        if (scrollbar != null) scrollbar.synchronizeMotion();
    }
    private void enterDetail(Subgroup group) {
        saveOffset(); detail = group; resizeScroll(); entrance.start(now()); updateButtons();
        placed.stream().filter(p -> p.view() == group && p.index() == -1).findFirst().ifPresent(p -> screen.setFocused(p.button()));
    }
    boolean leaveDetail() {
        if (detail == null) return false;
        Subgroup previous = detail;
        returnFocus = previous;
        saveOffset(); detail = null; resizeScroll(); entrance.start(now()); updateButtons();
        var arrow = buttons.get(previous.toggle());
        if (screen != null && arrow != null && arrow.visible) { screen.setFocused(arrow); returnFocus = null; }
        return true;
    }
    boolean restoreFocus() {
        if (screen == null || returnFocus == null) return false;
        var arrow = buttons.get(returnFocus.toggle());
        returnFocus = null;
        if (arrow == null || !arrow.visible) return false;
        screen.setFocused(arrow);
        return true;
    }
    boolean scroll(double x, double y, double delta) {
        return visible && area != null && x >= area.x() && x < area.right() && y >= firstRow && y < area.bottom()
                && scrollbar.scrollByWheel(delta);
    }
    void syncControls() {
        toggles.forEach((toggle, control) -> {
            control.setValue(toggle.read().getAsBoolean());
            if (!toggle.available().getAsBoolean()) control.button().setSelected(false);
        });
    }
    void setVisible(boolean value) {
        if (value && !visible) entrance.start(now());
        visible = value;
        updateButtons();
    }
    private static long now() { return System.nanoTime() / 1_000_000L; }
    private Bounds rowClip() { return new Bounds(area.x(), firstRow, Math.max(0, area.width() - 12), viewportHeight); }
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
            int row = p.index() < 0 ? 0 : p.index() / columns + (p.view() == null ? 0 : 1);
            int col = p.index() < 0 ? 0 : p.index() % columns;
            int reveal = screen == null ? 0 : (int) Math.round(entrance.offset(row, now(),
                    screen.context().preferences().reducedMotion()));
            int y = firstRow + row * (ControlCenterLayout.ROW + ControlCenterLayout.GAP) - offset + reveal;
            p.button().setX(area.x() + col * (rowWidth + ControlCenterLayout.GAP) + p.xOffset());
            p.button().setY(y);
            p.button().visible = visible && p.view() == detail && y < area.bottom()
                    && y + ControlCenterLayout.ROW > firstRow;
        }
        if (scrollbar != null) scrollbar.visible = visible && scroll.maxOffset() > 0;
    }
    private void updateButtons() { layoutButtons(); }
    void clearWidgets() {
        saveOffset(); buttons.clear(); toggles.clear(); placed.clear(); scrollbar = null; screen = null;
        visible = false; lastFocus = null;
    }
    void render(GuiGraphics graphics, Font font, Component status) {
        Component heading = status != null ? status : detail == null ? section.label() : detail.title();
        graphics.enableScissor(area.x(), area.y(), area.right(), area.y() + font.lineHeight);
        graphics.drawString(font, heading, area.x(), area.y(),
                status == null ? LiquidGlassTheme.accentColor() : LiquidGlassTheme.textColor(), false);
        graphics.disableScissor();
    }
    private record Placed(UiButton button, Subgroup view, int index, int xOffset) {}
}
