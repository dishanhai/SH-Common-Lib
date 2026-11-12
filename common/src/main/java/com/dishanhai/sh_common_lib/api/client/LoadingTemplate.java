// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client;

import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.List;

/** SHBUI-themed loading card. The caller owns screen lifecycle and input. */
public final class LoadingTemplate {
    private LoadingTemplate() {}

    /**
     * Draws a card at the given top edge. Progress is a fraction in [0, 1].
     * Pass NaN for an indeterminate stage. Elapsed time drives only the indicator.
     */
    public static void render(GuiGraphics graphics, Font font, int top, Component title, Component detail,
                              float progress, long elapsedMillis, UiPreferences preferences) {
        int width = Math.min(280, graphics.guiWidth() - 16);
        if (width < 40 || top < 0 || top + 48 > graphics.guiHeight()) return;
        int x = (graphics.guiWidth() - width) / 2;
        int track = width - 20;
        drawPlate(graphics, x, top, width, 48, preferences);
        var language = Language.getInstance();
        graphics.drawString(font, language.getVisualOrder(font.substrByWidth(title, track)),
                x + 10, top + 8, LiquidGlassTheme.textColor(), false);
        graphics.drawString(font, language.getVisualOrder(font.substrByWidth(detail, track)), x + 10, top + 21,
                preferences.highContrast() ? LiquidGlassTheme.textColor() : LiquidGlassTheme.mutedTextColor(), false);
        drawProgress(graphics, x + 10, top + 34, track, progress, elapsedMillis, preferences);
    }

    /** Draws a progress indicator that can be used before the client font is loaded. */
    public static void renderIndicator(GuiGraphics graphics, int top, float progress, long elapsedMillis, UiPreferences preferences) {
        int width = Math.min(280, graphics.guiWidth() - 16);
        if (width < 40 || top < 0 || top + 24 > graphics.guiHeight()) return;
        int x = (graphics.guiWidth() - width) / 2;
        drawPlate(graphics, x, top, width, 24, preferences);
        drawProgress(graphics, x + 10, top + 8, width - 20, progress, elapsedMillis, preferences);
    }

    /** Draws supplemental loading status without a progress indicator. */
    public static void renderStatus(GuiGraphics graphics, Font font, int top, List<Component> lines, UiPreferences preferences) {
        int width = Math.min(280, graphics.guiWidth() - 16);
        int height = 12 + lines.size() * 13;
        if (lines.isEmpty() || width < 40 || top < 0 || top + height > graphics.guiHeight()) return;
        int x = (graphics.guiWidth() - width) / 2;
        drawPlate(graphics, x, top, width, height, preferences);
        var language = Language.getInstance();
        int color = LiquidGlassTheme.textColor();
        for (Component line : lines) {
            graphics.drawString(font, language.getVisualOrder(font.substrByWidth(line, width - 20)),
                    x + 10, top + 7, color, false);
            top += 13;
        }
    }

    private static void drawPlate(GuiGraphics graphics, int x, int y, int width, int height, UiPreferences preferences) {
        LiquidGlassTheme.plate(graphics, x, y, width, height,
                DarkModeTheme.borderColor(), DarkModeTheme.surfaceColor(), preferences);
    }

    private static void drawProgress(GuiGraphics graphics, int left, int y, int track, float progress, long elapsedMillis,
                                     UiPreferences preferences) {
        int accent = LiquidGlassTheme.accentColor();
        graphics.fill(left, y, left + track, y + 8, DarkModeTheme.borderColor());

        boolean determinate = Float.isFinite(progress);
        int length = determinate ? Math.round(track * Math.clamp(progress, 0f, 1f)) : Math.max(1, track / 3);
        double phase = preferences.reducedMotion() ? 0.5 : (1 - Math.cos(elapsedMillis * Math.PI / 700)) / 2;
        int start = determinate ? left : left + (int) Math.round((track - length) * phase);
        if (length == 0) return;

        graphics.fill(start, y, start + length, y + 8, accent);
        if (!preferences.reducedMotion()) {
            int highlight = Math.min(36, Math.max(1, length / 3));
            int sweep = start + (int) Math.round((length - highlight) * phase);
            graphics.fillGradient(sweep, y, sweep + highlight, y + 8, 0x88FFFFFF, 0x18FFFFFF);
        }
    }
}
