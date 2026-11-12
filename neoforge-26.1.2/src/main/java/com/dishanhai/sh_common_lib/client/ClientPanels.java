// Author: dishanhai
package com.dishanhai.sh_common_lib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ClientPanels {
    public static final int TEXT = 0xFFF7F7FA;
    public static final int ACCENT = 0xFF91D9FF;

    private ClientPanels() {}

    public static int secondaryText() {
        return highContrast() ? TEXT : 0xFFC3CCD8;
    }

    public static int border() {
        return highContrast() ? TEXT : 0xCC8393A7;
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, highContrast() ? 0xFF000000 : 0xE018202C);
        graphics.outline(x, y, width, height, border());
    }

    /** Opaque upper bound for the dark panel composited on white. */
    public static int panelTextBackdrop() {
        return highContrast() ? 0xFF000000 : 0xFF343B46;
    }

    public static void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF303B4B);
        graphics.outline(x, y, 18, 18, border());
    }

    private static boolean highContrast() {
        return Minecraft.getInstance().options.highContrast().get();
    }
}
