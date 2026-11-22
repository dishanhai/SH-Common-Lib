// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_better_ui.kernel.api.text.TextFit;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHudSections;
import com.dishanhai.sh_better_ui.kernel.api.layout.HudSections;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Locale;

import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud.LANG;

final class PlayerInfoHudLayout {
    private static final int ROW_ICON_SIZE = 10;
    private static final float NATIVE_TEXT_SCALE = 1f;
    private static final float WIDTH_SHARE = 0.55f;
    private static final float HEIGHT_SHARE = 0.50f;

    private final Font font;
    private final List<Row> rows;
    private final List<HudPanel.Row> shortcuts;
    private final Component pageKeys;
    private final boolean titles, separators;
    private final Map<HudGroups.Group, Row> headings;
    private Viewport lastViewport;
    private View lastView;

    PlayerInfoHudLayout(Font font, List<Row> rows, List<HudPanel.Row> shortcuts, Component pageKeys,
                        boolean titles, boolean separators) {
        this.font = font;
        this.rows = List.copyOf(rows);
        this.shortcuts = List.copyOf(shortcuts);
        this.pageKeys = pageKeys;
        this.titles = titles;
        this.separators = separators;
        this.headings = headings();
    }

    View fit(int width, int height, int usableHeight, int scalePercent, double guiScale, int page) {
        return fit(width, height, usableHeight, scalePercent, guiScale, page, null);
    }

    View fit(int width, int height, int usableHeight, int scalePercent, double guiScale, int page, HudPages.Widths pageWidths) {
        var viewport = new Viewport(width, height, usableHeight, scalePercent, guiScale, page, pageWidths);
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
        // Measure the panel's fixed icon column, padding and margins at the minimum readable scale.
        var emptyPanel = new HudPanel(font, List.of());
        var emptySize = emptyPanel.fit(Integer.MAX_VALUE, Integer.MAX_VALUE, 1f);
        var available = emptyPanel.fit(budgetWidth, budgetHeight, Float.MIN_NORMAL);
        int availableWidth = available.width() + available.travelX();
        int textWidth = Math.max(0, (int) Math.floor(availableWidth / (double) minimum) - emptySize.width());
        var shortened = rows.stream().map(row -> new Row(truncate(row.content(), textWidth), row.priority(), row.group())).toList();
        var shortHeadings = new EnumMap<HudGroups.Group, Row>(HudGroups.Group.class);
        headings.forEach((group, row) -> shortHeadings.put(group, new Row(truncate(row.content(), textWidth), 0, group)));
        var shortenedPanel = panel(shortened, null, shortHeadings, textWidth);
        var footer = new Row(pageNotice(rows.size(), rows.size(), rows.size(), textWidth), 0, HudGroups.Group.CONTROLS);
        if (!rows.isEmpty() && !shortHeadings.isEmpty() && !fits(shortenedPanel, budgetWidth, budgetHeight, minimum)
                && !fits(panel(shortened.subList(0, 1), footer.content(), shortHeadings, textWidth),
                        budgetWidth, budgetHeight, minimum)) {
            // Let the data and shortcuts use the available height before shrinking the whole panel.
            shortHeadings.clear();
            shortenedPanel = panel(shortened, null, shortHeadings, textWidth);
        }
        if (rows.isEmpty() || fits(shortenedPanel, budgetWidth, budgetHeight, minimum)) {
            return place(shortenedPanel, viewport, budgetWidth, budgetHeight, requested, 0, 1, emptySize.width(), textWidth);
        }

        // Reserve one line for the notice before testing each row in retention order.
        // Choose the notice using the largest possible count to keep that width available.
        int hidden = rows.size();
        String noticeKey = "space_limited";
        var notice = notice(noticeKey, hidden);
        if (font.width(notice.text()) > textWidth) {
            noticeKey = "space_limited_short";
            notice = notice(noticeKey, hidden);
        }
        // HudPanel has uniform-height rows and its width is the widest label. Measure each
        // candidate label once instead of rebuilding and remeasuring a panel for every trial.
        var baseSize = panel(List.of(), null, shortHeadings, textWidth).body().fit(Integer.MAX_VALUE, Integer.MAX_VALUE, 1f);
        int rowHeight = new HudPanel(font, List.of(rows.get(0).content())).fit(Integer.MAX_VALUE, Integer.MAX_VALUE, 1f).height() - emptySize.height();
        var rowFit = new HudRowFit<Row>(baseSize.width(), baseSize.height(), emptySize.width(),
                rowHeight,
                availableWidth, available.height() + available.travelY(), minimum,
                row -> font.width(row.content().text()), candidate -> sections(candidate, shortHeadings, false, shortcuts.isEmpty()).rows());
        var pages = HudPages.partition(shortened, Row::priority, rowFit, footer);
        if (!pages.pages().isEmpty()) {
            int selected = Math.floorMod(viewport.page(), pages.pages().size());
            var pageFooter = pages.pages().size() == 1
                    ? truncate(notice(noticeKey, pages.omitted()), textWidth)
                    : pageNotice(selected + 1, pages.pages().size(), pages.omitted(), textWidth);
            return place(panel(pages.pages().get(selected), pageFooter, shortHeadings, textWidth), viewport, budgetWidth, budgetHeight,
                    requested, pages.omitted(), pages.pages().size(), emptySize.width(), textWidth);
        }
        var result = TextFit.selectRows(shortened, Row::priority, rowFit, new Row(truncate(notice, textWidth), 0, HudGroups.Group.CONTROLS));
        hidden = result.omitted();
        return place(panel(result.visible(), truncate(notice(noticeKey, hidden), textWidth), shortHeadings, textWidth),
                viewport, budgetWidth, budgetHeight, requested, hidden, 1, emptySize.width(), textWidth);
    }

    private HudPanel.Row truncate(HudPanel.Row row, int width) {
        var text = HudText.truncate(font, row.text(), width);
        return text == row.text() ? row : new HudPanel.Row(row.icon(), row.color(), text);
    }

    private Map<HudGroups.Group, Row> headings() {
        var result = new EnumMap<HudGroups.Group, Row>(HudGroups.Group.class);
        if (!titles) return result;
        var groups = java.util.EnumSet.noneOf(HudGroups.Group.class);
        for (var row : rows) groups.add(row.group());
        groups.add(HudGroups.Group.CONTROLS);
        for (var group : groups) {
            var text = Component.translatable(LANG + "group." + group.name().toLowerCase(Locale.ROOT))
                    .withStyle(net.minecraft.ChatFormatting.BOLD);
            var content = new HudPanel.Row(HudPanel.icon("info"), 0x00000000, text);
            result.put(group, new Row(content, 0, group));
        }
        return result;
    }

    private HudSections.Plan<Row> sections(List<Row> rows, Map<HudGroups.Group, Row> headings, boolean lines, boolean controlsTitle) {
        return HudSections.layout(rows, Row::group, group -> headings.containsKey(group) && (controlsTitle || group != HudGroups.Group.CONTROLS),
                headings::get, lines && separators);
    }

    private Panel panel(List<Row> rows, HudPanel.Row notice, Map<HudGroups.Group, Row> headings, int textWidth) {
        var entries = new ArrayList<Row>(rows);
        for (var shortcut : shortcuts) entries.add(new Row(truncate(shortcut, textWidth), 0, HudGroups.Group.CONTROLS));
        if (notice != null) entries.add(new Row(notice, 0, HudGroups.Group.CONTROLS));
        var plan = sections(entries, headings, true, true);
        var visible = plan.rows().stream().map(Row::content).toList();
        return new Panel(new HudPanel(font, visible, 0, ROW_ICON_SIZE), visible, plan.boundaries());
    }

    private static boolean fits(Panel panel, int width, int height, float scale) {
        return panel.body().fit(width, height, scale).scale() >= scale;
    }

    private View place(Panel panel, Viewport viewport, int budgetWidth, int budgetHeight,
                       float requested, int hidden, int pages, int emptyWidth, int textWidth) {
        var body = panel.body();
        int width = body.fit(Integer.MAX_VALUE, Integer.MAX_VALUE, 1f).width();
        if (viewport.pageWidths() != null) {
            int measured = width - emptyWidth;
            int page = Math.floorMod(viewport.page(), pages);
            int reserved = viewport.pageWidths().reserve(page, pages, measured, textWidth);
            if (reserved > measured) {
                body = new HudPanel(font, panel.rows(), reserved, ROW_ICON_SIZE);
                width = emptyWidth + reserved;
            }
        }
        float limit = body.fit(budgetWidth, budgetHeight, requested).scale();
        float minimum = Math.min(NATIVE_TEXT_SCALE, limit);
        float scale = new TextFit.Scale(requested, minimum, viewport.guiScale()).within(limit);
        // The budget sets the panel size. The entire usable viewport determines its travel.
        return new View(body, body.fit(viewport.width(), viewport.usableHeight(), scale),
                hidden, pages, panel.boundaries(), width);
    }

    private HudPanel.Row pageNotice(int page, int total, int hidden, int textWidth) {
        return new HudPanel.Row(HudPanel.icon("keyboard"), 0xFFDBE4EF,
                HudText.pageNotice(font, textWidth, page, total, hidden, pageKeys));
    }

    private static HudPanel.Row notice(String key, int hidden) {
        return new HudPanel.Row(HudPanel.icon("info"), 0xFFFFD36A, Component.translatable(LANG + key, hidden));
    }

    record Row(HudPanel.Row content, int priority, HudGroups.Group group) {}
    private record Panel(HudPanel body, List<HudPanel.Row> rows, List<Integer> boundaries) {}
    record View(HudPanel panel, HudPanel.Placement placement, int hidden, int pages,
                List<Integer> boundaries, int panelWidth) {
        void drawSeparators(GuiGraphicsExtractor graphics, Font font, int x, int y) {
            UiHudSections.drawDividers(graphics, font, x, y, placement.scale(), panelWidth, boundaries,
                    UiPreferences.configured().highContrast(), ClientPanels.TEXT);
        }
    }
    private record Viewport(int width, int height, int usableHeight, int scalePercent, double guiScale, int page,
                            HudPages.Widths pageWidths) {}
}
