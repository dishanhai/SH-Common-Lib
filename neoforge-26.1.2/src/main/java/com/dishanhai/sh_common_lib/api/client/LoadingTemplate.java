// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Loading card for Minecraft 26.1.2. The caller owns screen lifecycle and input. */
public final class LoadingTemplate {
    private LoadingTemplate() {}

    /** Uses the same color-only pipeline as vanilla before the initial shader reload. */
    public static void renderStartupProgress(GuiGraphicsExtractor graphics, float progress) {
        int width = Math.min(220, graphics.guiWidth() - 16);
        if (width < 24) return;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() * 3 / 4;
        graphics.fill(x, y, x + width, y + 9, 0xFF18202C);
        graphics.outline(x, y, width, 9, 0xCC8393A7);
        int length = Math.round((width - 4) * Math.clamp(progress, 0f, 1f));
        if (length > 0) {
            graphics.fill(x + 2, y + 2, x + 2 + length, y + 7, ClientPanels.ACCENT);
        }
    }

    /**
     * Draws a card at the given top edge. Progress is a fraction in [0, 1].
     * Pass NaN for an indeterminate stage. Elapsed time drives only the indicator.
     */
    public static void render(GuiGraphicsExtractor graphics, Font font, int top, Component title, Component detail,
                              float progress, long elapsedMillis) {
        int width = Math.min(280, graphics.guiWidth() - 16);
        if (width < 40 || top < 0 || top + 48 > graphics.guiHeight()) return;
        int x = (graphics.guiWidth() - width) / 2;
        int track = width - 20;
        ClientPanels.panel(graphics, x, top, width, 48);
        var language = Language.getInstance();
        graphics.text(font, language.getVisualOrder(font.substrByWidth(title, track)),
                x + 10, top + 8, ClientPanels.TEXT, false);
        graphics.text(font, language.getVisualOrder(font.substrByWidth(detail, track)), x + 10, top + 21,
                ClientPanels.secondaryText(), false);
        int y = top + 36;
        graphics.fill(x + 10, y, x + 10 + track, y + 4, ClientPanels.border());
        int length;
        int offset = 0;
        if (Float.isFinite(progress)) {
            length = Math.round(track * Math.clamp(progress, 0f, 1f));
        } else {
            length = Math.max(1, track / 4);
            double position = UiPreferences.configured().reducedMotion()
                    ? 0.5 : (1 - Math.cos(elapsedMillis * Math.PI / 800)) / 2;
            offset = (int) Math.round((track - length) * position);
        }
        if (length > 0) {
            int start = x + 10 + offset;
            graphics.fill(start, y, start + length, y + 4, ClientPanels.ACCENT);
        }
    }

    public static void renderStatus(GuiGraphicsExtractor graphics, Font font, int top, List<Component> lines) {
        int width = Math.min(280, graphics.guiWidth() - 16);
        int height = 12 + lines.size() * 13;
        if (lines.isEmpty() || width < 40 || top < 0 || top + height > graphics.guiHeight()) return;
        int x = (graphics.guiWidth() - width) / 2;
        ClientPanels.panel(graphics, x, top, width, height);
        var language = Language.getInstance();
        for (Component line : lines) {
            graphics.text(font, language.getVisualOrder(font.substrByWidth(line, width - 20)),
                    x + 10, top + 7, ClientPanels.TEXT, false);
            top += 13;
        }
    }
}
