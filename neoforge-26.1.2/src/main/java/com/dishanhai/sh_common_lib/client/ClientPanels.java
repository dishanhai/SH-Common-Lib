// Author: dishanhai
package com.dishanhai.sh_common_lib.client;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

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
        surface(graphics, new Bounds(x, y, width, height), GlassVariant.REGULAR, 0xB018202C);
    }

    public static void strongPopover(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        var base = GlassMaterial.preset(GlassVariant.REGULAR).optics();
        var optics = new GlassOptics(base.transmission(), base.refraction(), base.lensing(), 0.9f,
                base.edgeHighlight(), base.thickness(), 20f);
        if (ShBetterUiApi.blur().prepareBackdrop(graphics)
                && LiquidGlassSurface.drawPrepared(graphics, x, y, width, height, 6, 0xD018202C,
                border(), optics, UiPreferences.configured(), 1)) return;
        RoundedCorners.draw(graphics, x, y, width, height, 6, 1, border(), 0xFF18202C);
    }

    public static void surface(GuiGraphicsExtractor graphics, Bounds area, GlassVariant variant, int tint) {
        var preferences = UiPreferences.configured();
        if (variant != null && ShBetterUiApi.blur().prepareBackdrop(graphics)
                && LiquidGlassSurface.drawPrepared(graphics, area.x(), area.y(), area.width(), area.height(),
                6, tint, border(), GlassMaterial.preset(variant).optics(), preferences, 1)) return;
        RoundedCorners.draw(graphics, area.x(), area.y(), area.width(), area.height(), 6, 1,
                border(), highContrast() ? 0xFF000000 : 0xFF18202C);
    }

    public static UiButton button(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        var button = ShBetterUiApi.controls().button(new Bounds(x, y, width, height), label, onPress,
                UiPreferences.configured(), false, UiStyle.LIQUID_GLASS);
        button.setSampleBackground(false);
        return button;
    }

    public static void restoreFocus(Screen parent, Supplier<? extends GuiEventListener> focus) {
        if (parent == null || focus == null) return;
        var widget = focus.get();
        if (widget != null && parent.children().contains(widget)) parent.setFocused(widget);
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
        return UiPreferences.configured().highContrast();
    }
}
