// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_common_lib.client.ClientPanels;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class HudPanel {
    private static final int MARGIN = 4;
    private static final int PADDING = 6;
    private static final int ICON_SIZE = 8;
    private static final int TEXT_X = PADDING + ICON_SIZE + 4;

    private final List<Row> rows;
    private final List<GraphicIcon> icons;
    private final int width;
    private final int height;
    private final TextReadabilityPalette palette = new TextReadabilityPalette();

    public HudPanel(Font font, List<Row> rows) {
        this(font, rows, 0);
    }

    public HudPanel(Font font, List<Row> rows, int minimumTextWidth) {
        this.rows = List.copyOf(rows);
        this.icons = this.rows.stream().map(row -> new GraphicIcon(row.icon())).toList();
        int textWidth = this.rows.stream().mapToInt(row -> font.width(row.text())).max().orElse(0);
        width = TEXT_X + PADDING + Math.max(textWidth, minimumTextWidth);
        height = rows.size() * rowHeight(font) + 2 * PADDING;
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    public Placement fit(int viewportWidth, int viewportHeight, float requested) {
        int availableWidth = Math.max(1, viewportWidth - 2 * MARGIN);
        int availableHeight = Math.max(1, viewportHeight - 2 * MARGIN);
        float scale = Math.min(requested, Math.min((float) availableWidth / width, (float) availableHeight / height));
        int drawnWidth = (int) Math.ceil(width * scale);
        int drawnHeight = (int) Math.ceil(height * scale);
        return new Placement(drawnWidth, drawnHeight, Math.max(0, availableWidth - drawnWidth),
                Math.max(0, availableHeight - drawnHeight), scale);
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale) {
        draw(graphics, font, x, y, scale, true);
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale, boolean glass) {
        draw(graphics, font, x, y, scale, glass, false, -1);
    }

    public void drawSelected(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale, boolean glass, int selected) {
        draw(graphics, font, x, y, scale, glass, false, selected);
    }

    public void drawPopover(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale, boolean glass) {
        draw(graphics, font, x, y, scale, glass, true, -1);
    }

    private void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale, boolean glass, boolean popover, int selected) {
        if (isEmpty()) return;
        var preferences = UiPreferences.configured();
        int rowHeight = rowHeight(font);
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y).scale(scale);
            boolean clearGlass = false;
            if (glass && popover) ClientPanels.strongPopover(graphics, 0, 0, width, height);
            else if (glass) clearGlass = ClientPanels.hud(graphics, 0, 0, width, height);
            else ClientPanels.surface(graphics, new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(0, 0, width, height), null, 0xFF18202C);
            if (selected >= 0 && selected < rows.size()) {
                int rowY = PADDING + selected * rowHeight;
                if (glass) ClientPanels.strongPopover(graphics, PADDING - 2, rowY, width - 2 * PADDING + 4, rowHeight);
                else ClientPanels.surface(graphics, new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(PADDING - 2, rowY, width - 2 * PADDING + 4, rowHeight), null, 0xFF18202C);
            }
            // The clear surface refracts the world. Keep text protection on bright terrain as well.
            palette.update(null, 0xff000000, clearGlass ? 0xffffffff : ClientPanels.panelTextBackdrop());
            int textColor = palette.color(ClientPanels.TEXT);
            int rowY = PADDING;
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                if ((row.color() >>> 24) != 0) {
                    pose.pushMatrix();
                    try {
                        pose.translate(PADDING, rowY + (rowHeight - ICON_SIZE) / 2).scale((float) ICON_SIZE / GraphicIcon.PIXELS);
                        icons.get(i).draw(graphics, 0, 0, 1, preferences.highContrast() ? textColor : row.color(), preferences);
                    } finally {
                        pose.popMatrix();
                    }
                }
                UiHudRichText.draw(graphics, font, row.text().getVisualOrderText(),
                        TEXT_X, rowY + (rowHeight - font.lineHeight) / 2, textColor, preferences, palette);
                rowY += rowHeight;
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static int rowHeight(Font font) {
        return Math.max(ICON_SIZE, font.lineHeight) + 3;
    }

    public static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("sh_better_ui", "textures/gui/display/" + name + ".png");
    }

    public record Row(Identifier icon, int color, Component text) {
        public Row { text = UiHudRichText.prepare(text); }
    }

    public record Placement(int width, int height, int travelX, int travelY, float scale) {
        int x(double fraction) { return MARGIN + (int) Math.round(travelX * fraction); }
        int y(double fraction) { return MARGIN + (int) Math.round(travelY * fraction); }
        double moveX(double coordinate, double current) { return fraction(coordinate, travelX, current); }
        double moveY(double coordinate, double current) { return fraction(coordinate, travelY, current); }

        private static double fraction(double coordinate, int travel, double current) {
            return travel == 0 ? current : Math.clamp((coordinate - MARGIN) / travel, 0, 1);
        }
    }
}
