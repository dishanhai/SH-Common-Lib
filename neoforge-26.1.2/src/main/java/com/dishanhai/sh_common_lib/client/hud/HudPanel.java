// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_common_lib.client.ClientPanels;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class HudPanel {
    private static final int MARGIN = 4;
    private static final int PADDING = 6;
    private static final int ICON_SIZE = 8;
    private static final int TEXT_X = PADDING + ICON_SIZE + 4;

    private final List<Row> rows;
    private final int width;
    private final int height;
    private final TextReadabilityPalette palette = new TextReadabilityPalette();

    public HudPanel(Font font, List<Row> rows) {
        this.rows = List.copyOf(rows);
        width = TEXT_X + PADDING + rows.stream().mapToInt(row -> font.width(row.text())).max().orElse(0);
        height = rows.size() * (font.lineHeight + 3) + 2 * PADDING;
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
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y).scale(scale);
            if (glass && popover) ClientPanels.strongPopover(graphics, 0, 0, width, height);
            else if (glass) ClientPanels.panel(graphics, 0, 0, width, height);
            else ClientPanels.surface(graphics, new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(0, 0, width, height), null, 0xFF18202C);
            if (selected >= 0 && selected < rows.size()) {
                int rowY = PADDING + selected * (font.lineHeight + 3);
                if (glass) ClientPanels.strongPopover(graphics, PADDING - 2, rowY, width - 2 * PADDING + 4, font.lineHeight + 3);
                else ClientPanels.surface(graphics, new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(PADDING - 2, rowY, width - 2 * PADDING + 4, font.lineHeight + 3), null, 0xFF18202C);
            }
            palette.update(null, 0xff000000, ClientPanels.panelTextBackdrop());
            int textColor = palette.color(ClientPanels.TEXT);
            int lineY = PADDING + 1;
            for (var row : rows) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, row.icon(), PADDING, lineY, 0f, 0f,
                        ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, row.color());
                UiHudRichText.draw(graphics, font, row.text().getVisualOrderText(), TEXT_X, lineY,
                        textColor, UiPreferences.configured(), palette);
                lineY += font.lineHeight + 3;
            }
        } finally {
            pose.popMatrix();
        }
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
