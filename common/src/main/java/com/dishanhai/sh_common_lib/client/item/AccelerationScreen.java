// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.screen.LayeredScreen;
import com.dishanhai.sh_common_lib.api.ulk.acceleration.AccelerationPackets;
import com.dishanhai.sh_common_lib.api.ulk.acceleration.AccelerationPackets.RangeView;
import com.dishanhai.sh_common_lib.api.ulk.acceleration.AccelerationPackets.Settings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static com.dishanhai.sh_common_lib.client.item.AccelerationClient.text;

final class AccelerationScreen extends LayeredScreen {
    private static final int[] RATES = {0, 2, 4, 16, 32, 64, 128, 256, 512, 1024};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private int left, top, panelWidth, panelHeight;
    private int tab, page;
    private UUID confirm;
    private Settings shownSettings;
    private List<RangeView> shownRanges = List.of();

    AccelerationScreen() { super(text("title")); }

    @Override
    protected void init() {
        panelWidth = Math.max(200, Math.min(360, width - 12));
        panelHeight = Math.min(170, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        var reply = AccelerationClient.snapshot();
        shownSettings = reply == null ? null : reply.settings();
        shownRanges = reply == null ? List.of() : reply.ranges();
        if (shownSettings == null) {
            button(left + 8, top + panelHeight - 24, 62, text("close"), this::onClose);
            return;
        }
        var settings = shownSettings;
        var master = button(left + panelWidth - 128, top + 5, 120,
                text(settings.master() ? "master_on" : "master_off"),
                () -> send(AccelerationPackets.SET_MASTER, settings.master() ? 0 : 1, 0, null));
        master.setSelected(settings.master());
        int tabsWidth = panelWidth - 16;
        int tabWidth = tabsWidth / 4;
        String[] names = {"settings_tab", "range_tab", "lists_tab", "history_tab"};
        for (int i = 0; i < names.length; i++) {
            int target = i;
            var control = button(left + 8 + i * tabWidth, top + 28,
                    i == 3 ? tabsWidth - 3 * tabWidth : tabWidth - 2, text(names[i]), () -> {
                        tab = target;
                        page = 0;
                        confirm = null;
                        rebuildWidgets();
                    });
            control.setSelected(i == tab);
        }
        int y = top + 51;
        switch (tab) {
            case 0 -> settingsPage(settings, y);
            case 1 -> rangePage(settings, y);
            case 2 -> listsPage(settings, y);
            case 3 -> historyPage(y);
            default -> throw new IllegalStateException();
        }
        button(left + 8, top + panelHeight - 23, 56, text("close"), this::onClose);
    }

    private void settingsPage(Settings settings, int y) {
        int x = left + 8;
        int space = panelWidth - 16;
        int w = (space - 8) / 5;
        for (int i = 0; i < RATES.length; i++) {
            int rate = RATES[i];
            var b = button(x + (i % 5) * (w + 2), y + (i / 5) * 21, w,
                    rate == 0 ? text("rate_off") : text("rate", rate),
                    () -> send(AccelerationPackets.SET_RATE, rate, 0, null));
            b.setSelected(settings.rate() == rate);
        }
        int modeWidth = (space - 4) / 3;
        for (int i = 0; i < 3; i++) {
            int mode = i;
            var b = button(x + i * (modeWidth + 2), y + 44, modeWidth,
                    text("mode_" + i), () -> send(AccelerationPackets.SET_MODE, mode, 0, null));
            b.setSelected(settings.mode() == i);
            b.setPreview(text("mode_hint_" + i));
        }
        var ai = button(x, y + 66, space, text(settings.disableAi() ? "ai_off" : "ai_on"),
                () -> send(AccelerationPackets.SET_NO_AI, settings.disableAi() ? 0 : 1, 0, null));
        ai.setSelected(settings.disableAi());
    }

    private void rangePage(Settings settings, int y) {
        int x = left + 8;
        int space = panelWidth - 16;
        int[] values = {settings.x(), settings.y(), settings.z()};
        for (int i = 0; i < 3; i++) {
            int axis = i;
            int value = values[i];
            int row = y + i * 22;
            var minus = button(x, row, 20, text("minus"),
                    () -> send(AccelerationPackets.SET_SIZE, axis, Math.max(1, value - 1), null));
            minus.active = value > 1;
            var slider = ShBetterUiApi.controls().slider(new Bounds(x + 23, row, space - 46, 19),
                    1, 15, 1, value, UiPreferences.configured(),
                    number -> text("axis", "XYZ".substring(axis, axis + 1), number), number -> {});
            slider.setOnCommit(number -> send(AccelerationPackets.SET_SIZE, axis, number, null), number -> {});
            addRenderableWidget(slider);
            var plus = button(x + space - 20, row, 20, text("plus"),
                    () -> send(AccelerationPackets.SET_SIZE, axis, Math.min(15, value + 1), null));
            plus.active = value < 15;
        }
        var place = button(x, y + 68, space, text(settings.placing() ? "placing_on" : "placing_off"),
                () -> send(AccelerationPackets.SET_PLACING, settings.placing() ? 0 : 1, 0, null));
        place.setSelected(settings.placing());
        place.setPreview(text("placing_hint"));
    }

    private void listsPage(Settings settings, int y) {
        int x = left + 8;
        int space = panelWidth - 16;
        var selected = selected(settings.selected());
        int accelerationCount = selected == null ? 0 : selected.accelerationCount();
        int sleepCount = selected == null ? 0 : selected.sleepCount();
        listRow(x, y, space, 1, settings.editing(), settings.accelerationWhitelist(), accelerationCount);
        listRow(x, y + 25, space, 2, settings.editing(), settings.sleepWhitelist(), sleepCount);
        var stop = button(x, y + 51, space, text("stop_marking"),
                () -> send(AccelerationPackets.SET_EDITING, 0, 0, null));
        stop.active = settings.editing() != 0;
    }

    private void listRow(int x, int y, int width, int kind, int editing, boolean whitelist, int count) {
        int right = Math.min(96, width / 3);
        var edit = button(x, y, width - right - 2, text(kind == 1 ? "acceleration_list" : "sleep_list", count),
                () -> send(AccelerationPackets.SET_EDITING, editing == kind ? 0 : kind, 0, null));
        edit.setSelected(editing == kind);
        edit.setPreview(text("marking_hint"));
        var filter = button(x + width - right, y, right, text(whitelist ? "whitelist" : "blacklist"),
                () -> send(AccelerationPackets.SET_FILTER, kind, whitelist ? 0 : 1, null));
        filter.setSelected(whitelist);
    }

    private void historyPage(int y) {
        int rows = Math.max(1, (panelHeight - 79) / 34);
        int pages = Math.max(1, (shownRanges.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int space = panelWidth - 16;
        int x = left + 8;
        for (int i = 0; i < rows && page * rows + i < shownRanges.size(); i++) {
            var range = shownRanges.get(page * rows + i);
            int row = y + i * 34;
            int statusWidth = 43;
            int removeWidth = 46;
            int labelWidth = space - statusWidth - removeWidth - 4;
            if (range.id().equals(confirm)) {
                var prompt = button(x, row, labelWidth, text("confirm_remove"),
                        () -> send(AccelerationPackets.RECLAIM, 0, 0, range.id()));
                prompt.setPreview(location(range));
                button(x + labelWidth + 2, row, statusWidth + removeWidth + 2, text("cancel"),
                        () -> { confirm = null; rebuildWidgets(); });
                continue;
            }
            var select = button(x, row, labelWidth, shortLocation(range),
                    () -> send(AccelerationPackets.SELECT_RANGE, 0, 0, range.id()));
            select.setSelected(shownSettings != null && range.id().equals(shownSettings.selected()));
            select.setPreview(location(range));
            var enabled = button(x + labelWidth + 2, row, statusWidth,
                    text(range.enabled() ? "enabled_short" : "disabled_short"),
                    () -> send(AccelerationPackets.SET_RANGE_ENABLED, range.enabled() ? 0 : 1, 0, range.id()));
            enabled.setSelected(range.enabled());
            button(x + labelWidth + statusWidth + 4, row, removeWidth, text("reclaim"),
                    () -> { confirm = range.id(); rebuildWidgets(); });
        }
        int bottom = top + panelHeight - 23;
        var previous = button(left + panelWidth - 94, bottom, 24, text("previous"),
                () -> { page--; rebuildWidgets(); });
        previous.active = page > 0;
        var next = button(left + panelWidth - 32, bottom, 24, text("next"),
                () -> { page++; rebuildWidgets(); });
        next.active = page + 1 < pages;
    }

    private RangeView selected(UUID id) {
        for (var range : shownRanges) if (range.id().equals(id)) return range;
        return null;
    }

    private static Component shortLocation(RangeView range) {
        var p = range.pos();
        return text("location_short", p.getX(), p.getY(), p.getZ());
    }

    private static Component location(RangeView range) {
        var p = range.pos();
        return text("location", range.dimension(), p.getX(), p.getY(), p.getZ(), date(range));
    }

    private static String date(RangeView range) {
        try { return DATE.format(Instant.ofEpochMilli(range.created())); }
        catch (RuntimeException ignored) { return Long.toString(range.created()); }
    }

    private UiButton button(int x, int y, int width, Component label, Runnable action) {
        return addRenderableWidget(new UiButton(x, y, width, 19, label, ignored -> action.run()));
    }

    private void send(int action, int a, int b, UUID id) {
        AccelerationClient.request(action, a, b, 0, id);
    }

    @Override public void tick() {
        if (!AccelerationClient.holding(minecraft)) {
            onClose();
            return;
        }
        var reply = AccelerationClient.snapshot();
        var settings = reply == null ? null : reply.settings();
        var ranges = reply == null ? List.<RangeView>of() : reply.ranges();
        if (!isDragging() && (!java.util.Objects.equals(shownSettings, settings) || !shownRanges.equals(ranges))) {
            rebuildWidgets();
        }
    }

    @Override protected void renderBackdrop(GuiGraphics graphics, int x, int y, float partialTick) {
        LiquidGlassTheme.popover(graphics, left, top, panelWidth, panelHeight, UiPreferences.configured());
    }

    @Override protected void renderForeground(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.drawString(font, title, left + 8, top + 10, 0xFFFFFFFF);
        if (shownSettings == null) {
            graphics.drawCenteredString(font, text(AccelerationClient.unavailable() ? "unavailable" : "waiting"),
                    width / 2, top + 60, 0xFFFFFFFF);
        } else if (AccelerationClient.waiting() || AccelerationClient.unavailable()) {
            graphics.drawCenteredString(font, text(AccelerationClient.unavailable() ? "unavailable" : "waiting"),
                    left + panelWidth / 2, top + panelHeight - 17, 0xFFFFFFFF);
        } else if (tab == 3 && shownRanges.isEmpty()) {
            graphics.drawCenteredString(font, text("history_empty"), width / 2, top + 67, 0xFFC3CCD8);
        }
        if (shownSettings != null && tab == 2) {
            var range = selected(shownSettings.selected());
            var summary = range == null ? text("no_range_selected") : text("current_range", shortLocation(range));
            graphics.drawString(font, font.plainSubstrByWidth(summary.getString(), panelWidth - 20),
                    left + 10, top + 125, 0xFFFFFFFF);
        }
        if (shownSettings != null && tab == 3) {
            int rows = Math.max(1, (panelHeight - 79) / 34);
            for (int i = 0; i < rows && page * rows + i < shownRanges.size(); i++) {
                var range = shownRanges.get(page * rows + i);
                String details = range.dimension() + "  " + date(range);
                graphics.drawString(font, font.plainSubstrByWidth(details, panelWidth - 20),
                        left + 10, top + 71 + i * 34, 0xFFC3CCD8);
            }
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
