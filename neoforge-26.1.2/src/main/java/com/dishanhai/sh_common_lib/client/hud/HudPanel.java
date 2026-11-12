// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_common_lib.client.ClientPanels;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

final class HudPanel {
    private static final int MARGIN = 4;
    private static final int PADDING = 6;
    private static final int ICON_SIZE = 8;
    private static final int TEXT_X = PADDING + ICON_SIZE + 4;

    private final List<Row> rows;
    private final int width;
    private final int height;
    private final TextReadabilityPalette palette = new TextReadabilityPalette();

    HudPanel(Font font, List<Row> rows) {
        this.rows = List.copyOf(rows);
        width = TEXT_X + PADDING + rows.stream().mapToInt(row -> font.width(row.text())).max().orElse(0);
        height = rows.size() * (font.lineHeight + 3) + 2 * PADDING;
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    Placement fit(int viewportWidth, int viewportHeight, float requested) {
        int availableWidth = Math.max(1, viewportWidth - 2 * MARGIN);
        int availableHeight = Math.max(1, viewportHeight - 2 * MARGIN);
        float scale = Math.min(requested, Math.min((float) availableWidth / width, (float) availableHeight / height));
        int drawnWidth = (int) Math.ceil(width * scale);
        int drawnHeight = (int) Math.ceil(height * scale);
        return new Placement(drawnWidth, drawnHeight, Math.max(0, availableWidth - drawnWidth),
                Math.max(0, availableHeight - drawnHeight), scale);
    }

    void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale) {
        if (isEmpty()) return;
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y).scale(scale);
            ClientPanels.panel(graphics, 0, 0, width, height);
            palette.update(null, 0xff000000, ClientPanels.panelTextBackdrop());
            int textColor = palette.color(ClientPanels.TEXT);
            int lineY = PADDING + 1;
            for (var row : rows) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, row.icon(), PADDING, lineY, 0f, 0f,
                        ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, row.color());
                var glyphs = palette.glyphs(row.text().getVisualOrderText());
                graphics.text(font, glyphs, TEXT_X, lineY, textColor, false);
                lineY += font.lineHeight + 3;
            }
        } finally {
            pose.popMatrix();
        }
    }

    static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("sh_better_ui", "textures/gui/display/" + name + ".png");
    }

    record Row(Identifier icon, int color, Component text) {}

    record Placement(int width, int height, int travelX, int travelY, float scale) {
        int x(double fraction) { return MARGIN + (int) Math.round(travelX * fraction); }
        int y(double fraction) { return MARGIN + (int) Math.round(travelY * fraction); }
        double moveX(double coordinate, double current) { return fraction(coordinate, travelX, current); }
        double moveY(double coordinate, double current) { return fraction(coordinate, travelY, current); }

        private static double fraction(double coordinate, int travel, double current) {
            return travel == 0 ? current : Math.clamp((coordinate - MARGIN) / travel, 0, 1);
        }
    }
}
