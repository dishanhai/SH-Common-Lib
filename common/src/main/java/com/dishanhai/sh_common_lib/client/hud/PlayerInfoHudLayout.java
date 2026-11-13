// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHudPanel;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHudSections;
import com.dishanhai.sh_better_ui.kernel.api.layout.HudSections;
import com.dishanhai.sh_better_ui.kernel.api.text.TextFit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Locale;

import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud.LANG;

final class PlayerInfoHudLayout {
    private static final float NATIVE_TEXT_SCALE = 1f;
    private static final float WIDTH_SHARE = 0.55f;
    private static final float HEIGHT_SHARE = 0.50f;

    private final Font font;
    private final List<Row> rows;
    private final List<UiHudPanel.Row> shortcuts;
    private final Component pageKeys;
    private final Panel fullPanel;
    private final boolean titles, separators;
    private final Map<HudGroups.Group, Row> headings;
    private Viewport lastViewport;
    private View lastView;

    PlayerInfoHudLayout(Font font, List<Row> rows, List<UiHudPanel.Row> shortcuts) {
        this(font, rows, shortcuts, Component.translatable(LANG + "page_keys_default"), true, true);
    }

    PlayerInfoHudLayout(Font font, List<Row> rows, List<UiHudPanel.Row> shortcuts, Component pageKeys) {
        this(font, rows, shortcuts, pageKeys, true, true);
    }

    PlayerInfoHudLayout(Font font, List<Row> rows, List<UiHudPanel.Row> shortcuts, Component pageKeys,
                        boolean titles, boolean separators) {
        this.font = font;
        this.rows = List.copyOf(rows);
        this.shortcuts = List.copyOf(shortcuts);
        this.pageKeys = pageKeys;
        this.titles = titles;
        this.separators = separators;
        this.headings = headings(Integer.MAX_VALUE);
        fullPanel = panel(this.rows, null, headings);
    }

    View fit(int width, int height, int usableHeight, int scalePercent, double guiScale) {
        return fit(width, height, usableHeight, scalePercent, guiScale, 0);
    }

    View fit(int width, int height, int usableHeight, int scalePercent, double guiScale, int page) {
        var viewport = new Viewport(width, height, usableHeight, scalePercent, guiScale, page);
        if (!viewport.equals(lastViewport)) {
            lastView = layout(viewport);
            lastViewport = viewport;
        }
        return lastView;
    }

    private View layout(Viewport viewport) {
        float requested = viewport.scalePercent() / 100f;
        float minimum = Math.min(requested, NATIVE_TEXT_SCALE);
        int budgetWidth = (int) (viewport.width() * WIDTH_SHARE);
        int budgetHeight = Math.min(viewport.usableHeight(), (int) (viewport.height() * HEIGHT_SHARE));
        if (fits(fullPanel, budgetWidth, budgetHeight, minimum)) {
            return place(fullPanel, viewport, budgetWidth, budgetHeight, requested, 0, 1);
        }

        // Measure the panel's fixed icon column, padding and margins at the minimum readable scale.
        var empty = new UiHudPanel(font, List.of()).fit(budgetWidth, Integer.MAX_VALUE, minimum);
        int textWidth = (int) Math.floor(empty.travelX() / (double) minimum);
        var shortened = rows.stream().map(row -> new Row(truncate(row.content(), textWidth), row.priority(), row.group())).toList();
        var shortHeadings = headings(textWidth);
        var shortenedPanel = panel(shortened, null, shortHeadings);
        if (rows.isEmpty() || fits(shortenedPanel, budgetWidth, budgetHeight, minimum)) {
            return place(shortenedPanel, viewport, budgetWidth, budgetHeight, requested, 0, 1);
        }

        // Reserve one line for the notice before testing each row in retention order.
        // Choose the notice using the largest possible count to keep that width available.
        int hidden = rows.size();
        String noticeKey = "space_limited";
        var notice = notice(noticeKey, hidden);
        if (!fits(panel(List.of(), notice, shortHeadings), budgetWidth, budgetHeight, minimum)) {
            noticeKey = "space_limited_short";
            notice = notice(noticeKey, hidden);
        }
        // UiHudPanel has uniform-height rows and its width is the widest label. Measure each
        // candidate label once instead of rebuilding and remeasuring a panel for every trial.
        var emptyPanel = new UiHudPanel(font, List.of());
        var emptySize = emptyPanel.fit(Integer.MAX_VALUE, Integer.MAX_VALUE, 1f);
        var baseSize = panel(List.of(), null, shortHeadings).body().fit(Integer.MAX_VALUE, Integer.MAX_VALUE, 1f);
        int rowHeight = new UiHudPanel(font, List.of(rows.get(0).content())).fit(Integer.MAX_VALUE, Integer.MAX_VALUE, 1f).height() - emptySize.height();
        // A near-zero scale keeps the empty panel at one pixel and avoids rounding at the viewport edge.
        var available = emptyPanel.fit(budgetWidth, budgetHeight, Float.MIN_NORMAL);
        var rowFit = new HudRowFit<Row>(baseSize.width(), baseSize.height(), emptySize.width(),
                rowHeight,
                available.width() + available.travelX(), available.height() + available.travelY(), minimum,
                row -> font.width(row.content().text()), candidate -> sections(candidate, shortHeadings, false, shortcuts.isEmpty()).rows());
        var footer = new Row(truncate(pageNotice(rows.size(), rows.size(), rows.size()), textWidth), 0, HudGroups.Group.CONTROLS);
        var pages = HudPages.partition(shortened, Row::priority, rowFit, footer);
        if (!pages.pages().isEmpty()) {
            int selected = Math.floorMod(viewport.page(), pages.pages().size());
            var pageFooter = truncate(pageNotice(selected + 1, pages.pages().size(), pages.omitted()), textWidth);
            return place(panel(pages.pages().get(selected), pageFooter, shortHeadings), viewport, budgetWidth, budgetHeight,
                    requested, pages.omitted(), pages.pages().size());
        }
        var result = TextFit.selectRows(shortened, Row::priority, rowFit, new Row(notice, 0, HudGroups.Group.CONTROLS));
        hidden = result.omitted();
        return place(panel(result.visible(), notice(noticeKey, hidden), shortHeadings),
                viewport, budgetWidth, budgetHeight, requested, hidden, 1);
    }

    private UiHudPanel.Row truncate(UiHudPanel.Row row, int width) {
        var text = HudText.truncate(font, row.text(), width);
        return text == row.text() ? row : new UiHudPanel.Row(row.icon(), row.iconColor(), text);
    }

    private Map<HudGroups.Group, Row> headings(int width) {
        var result = new EnumMap<HudGroups.Group, Row>(HudGroups.Group.class);
        if (!titles) return result;
        var groups = java.util.EnumSet.noneOf(HudGroups.Group.class);
        for (var row : rows) groups.add(row.group());
        groups.add(HudGroups.Group.CONTROLS);
        for (var group : groups) {
            var text = Component.translatable(LANG + "group." + group.name().toLowerCase(Locale.ROOT)).withStyle(net.minecraft.ChatFormatting.BOLD);
            var content = new UiHudPanel.Row(GraphicIcon.INFO, 0x00000000, text);
            result.put(group, new Row(width == Integer.MAX_VALUE ? content : truncate(content, width), 0, group));
        }
        return result;
    }

    private HudSections.Plan<Row> sections(List<Row> rows, Map<HudGroups.Group, Row> headings, boolean lines, boolean controlsTitle) {
        return HudSections.layout(rows, Row::group, group -> titles && (controlsTitle || group != HudGroups.Group.CONTROLS),
                headings::get, lines && separators);
    }

    private Panel panel(List<Row> rows, UiHudPanel.Row notice, Map<HudGroups.Group, Row> headings) {
        var entries = new ArrayList<Row>(rows);
        if (notice != null) entries.add(new Row(notice, 0, HudGroups.Group.CONTROLS));
        for (var shortcut : shortcuts) entries.add(new Row(shortcut, 0, HudGroups.Group.CONTROLS));
        var plan = sections(entries, headings, true, true);
        return new Panel(new UiHudPanel(font, plan.rows().stream().map(Row::content).toList()), plan.boundaries());
    }

    private static boolean fits(Panel panel, int width, int height, float scale) {
        return panel.body().fit(width, height, scale).scale() >= scale;
    }

    private static View place(Panel panel, Viewport viewport, int budgetWidth, int budgetHeight,
                              float requested, int hidden, int pages) {
        float limit = panel.body().fit(budgetWidth, budgetHeight, requested).scale();
        float minimum = Math.min(NATIVE_TEXT_SCALE, limit);
        float scale = new TextFit.Scale(requested, minimum, viewport.guiScale()).within(limit);
        // The budget sets the panel size. The entire usable viewport determines its travel.
        int width = panel.body().fit(Integer.MAX_VALUE, Integer.MAX_VALUE, 1f).width();
        return new View(panel.body(), panel.body().fit(viewport.width(), viewport.usableHeight(), scale),
                hidden, pages, panel.boundaries(), width);
    }

    private UiHudPanel.Row pageNotice(int page, int total, int hidden) {
        return new UiHudPanel.Row(GraphicIcon.KEYBOARD, 0xFFDBE4EF,
                Component.translatable(LANG + (hidden > 0 ? "page_indicator_hidden" : "page_indicator"),
                        page, total, pageKeys, hidden));
    }

    private static UiHudPanel.Row notice(String key, int hidden) {
        return new UiHudPanel.Row(GraphicIcon.INFO, 0xFFFFD36A, Component.translatable(LANG + key, hidden));
    }

    record Row(UiHudPanel.Row content, int priority, HudGroups.Group group) {}
    private record Panel(UiHudPanel body, List<Integer> boundaries) {}
    record View(UiHudPanel panel, UiHudPanel.Placement placement, int hidden, int pages,
                List<Integer> boundaries, int panelWidth) {
        void drawSeparators(GuiGraphics graphics, Font font, int x, int y, UiPreferences preferences) {
            UiHudSections.drawDividers(graphics, font, x, y, placement.scale(), panelWidth, boundaries,
                    preferences.highContrast(), LiquidGlassTheme.textColor());
        }
    }
    private record Viewport(int width, int height, int usableHeight, int scalePercent, double guiScale, int page) {}
}
