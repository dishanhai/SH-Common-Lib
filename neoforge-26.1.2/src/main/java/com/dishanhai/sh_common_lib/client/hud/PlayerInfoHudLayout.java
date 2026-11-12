// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_better_ui.kernel.api.text.TextFit;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud.LANG;

final class PlayerInfoHudLayout {
    private static final float NATIVE_TEXT_SCALE = 1f;
    private static final float WIDTH_SHARE = 0.55f;
    private static final float HEIGHT_SHARE = 0.50f;

    private final Font font;
    private final List<Row> rows;
    private final HudPanel.Row shortcut;
    private final HudPanel fullPanel;
    private Viewport lastViewport;
    private View lastView;

    PlayerInfoHudLayout(Font font, List<Row> rows, HudPanel.Row shortcut) {
        this.font = font;
        this.rows = List.copyOf(rows);
        this.shortcut = shortcut;
        fullPanel = panel(this.rows, null, shortcut);
    }

    boolean isEmpty() {
        return fullPanel.isEmpty();
    }

    View fit(int width, int height, int usableHeight, int scalePercent, double guiScale) {
        var viewport = new Viewport(width, height, usableHeight, scalePercent, guiScale);
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
            return place(fullPanel, viewport, budgetWidth, budgetHeight, requested, 0);
        }

        // Measure the panel's fixed icon column, padding and margins at the minimum readable scale.
        var empty = new HudPanel(font, List.of()).fit(budgetWidth, Integer.MAX_VALUE, minimum);
        int textWidth = (int) Math.floor(empty.travelX() / (double) minimum);
        var shortened = rows.stream().map(row -> new Row(truncate(row.content(), textWidth), row.priority())).toList();
        var fittedShortcut = shortcut == null ? null : truncate(shortcut, textWidth);
        var shortenedPanel = panel(shortened, null, fittedShortcut);
        if (rows.isEmpty() || fits(shortenedPanel, budgetWidth, budgetHeight, minimum)) {
            return place(shortenedPanel, viewport, budgetWidth, budgetHeight, requested, 0);
        }

        // Reserve one line for the notice before testing each row in retention order.
        // Choose the notice using the largest possible count to keep that width available.
        int hidden = rows.size();
        String noticeKey = "space_limited";
        var notice = notice(noticeKey, hidden);
        if (!fits(panel(List.of(), notice, fittedShortcut), budgetWidth, budgetHeight, minimum)) {
            noticeKey = "space_limited_short";
            notice = notice(noticeKey, hidden);
        }
        var result = TextFit.selectRows(shortened, Row::priority, candidate ->
                fits(panel(candidate, null, fittedShortcut), budgetWidth, budgetHeight, minimum), new Row(notice, 0));
        hidden = result.omitted();
        return place(panel(result.visible(), notice(noticeKey, hidden), fittedShortcut),
                viewport, budgetWidth, budgetHeight, requested, hidden);
    }

    private HudPanel.Row truncate(HudPanel.Row row, int width) {
        var text = HudText.truncate(font, row.text(), width);
        return text == row.text() ? row : new HudPanel.Row(row.icon(), row.color(), text);
    }

    private HudPanel panel(List<Row> rows, HudPanel.Row notice, HudPanel.Row shortcut) {
        var visible = new ArrayList<HudPanel.Row>(rows.size() + 2);
        for (Row row : rows) visible.add(row.content());
        if (notice != null) visible.add(notice);
        if (shortcut != null) visible.add(shortcut);
        return new HudPanel(font, visible);
    }

    private static boolean fits(HudPanel panel, int width, int height, float scale) {
        return panel.fit(width, height, scale).scale() >= scale;
    }

    private static View place(HudPanel panel, Viewport viewport, int budgetWidth, int budgetHeight,
                              float requested, int hidden) {
        float limit = panel.fit(budgetWidth, budgetHeight, requested).scale();
        float minimum = Math.min(NATIVE_TEXT_SCALE, limit);
        float scale = new TextFit.Scale(requested, minimum, viewport.guiScale()).within(limit);
        // The budget sets the panel size. The entire usable viewport determines its travel.
        return new View(panel, panel.fit(viewport.width(), viewport.usableHeight(), scale), hidden);
    }

    private static HudPanel.Row notice(String key, int hidden) {
        return new HudPanel.Row(HudPanel.icon("info"), 0xFFFFD36A, Component.translatable(LANG + key, hidden));
    }

    record Row(HudPanel.Row content, int priority) {}
    record View(HudPanel panel, HudPanel.Placement placement, int hidden) {}
    private record Viewport(int width, int height, int usableHeight, int scalePercent, double guiScale) {}
}
