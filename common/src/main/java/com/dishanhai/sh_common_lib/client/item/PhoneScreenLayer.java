// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.screen.LayeredScreen;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.function.Consumer;

abstract class PhoneScreenLayer extends LayeredScreen {
    PhoneScreenLayer(Component title) {
        super(title);
    }

    protected abstract Bounds panel();
    protected abstract void drawPage(TextDraw text, RectDraw fill, int foreground, int secondary);

    protected static void openFile(File file) { Util.getPlatform().openFile(file); }

    protected final void openMessages(Screen parent) {
        var status = minecraft.getChatStatus();
        if (!status.isChatAllowed(minecraft.isLocalServer())) {
            minecraft.gui.setOverlayMessage(status.getMessage(), false);
            minecraft.getNarrator().sayNow(status.getMessage());
            return;
        }
        minecraft.setScreen(new ChatScreen("") {
            @Override
            public void onClose() { Minecraft.getInstance().setScreen(parent); }

            @Override
            public boolean keyPressed(int key, int scan, int modifiers) {
                boolean handled = super.keyPressed(key, scan, modifiers);
                if (key == GLFW.GLFW_KEY_ESCAPE && minecraft.screen == null) minecraft.setScreen(parent);
                return handled;
            }
        });
    }

    protected static void showScreenshotResult(Minecraft client, Component result) {
        client.gui.getChat().addMessage(result);
    }

    protected final MultiLineEditBox noteField(Bounds bounds, Component hint, Component label, int limit, String value,
                                              Consumer<String> change) {
        var field = new MultiLineEditBox(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(), hint, label);
        field.setCharacterLimit(limit);
        field.setValue(value);
        field.setValueListener(change);
        return field;
    }

    @Override
    protected final void renderBackdrop(GuiGraphics graphics, int x, int y, float partialTick) {
        var panel = panel();
        LiquidGlassTheme.popover(graphics, panel.x(), panel.y(), panel.width(), panel.height(), UiPreferences.configured());
    }

    @Override
    protected final void renderForeground(GuiGraphics graphics, int x, int y, float partialTick) {
        drawPage((text, left, top, color) -> graphics.drawString(font, text, left, top, color), graphics::fill,
                LiquidGlassTheme.textColor(), LiquidGlassTheme.mutedTextColor());
    }

    @FunctionalInterface
    protected interface TextDraw {
        void draw(Component text, int x, int y, int color);
    }

    @FunctionalInterface
    protected interface RectDraw {
        void fill(int left, int top, int right, int bottom, int color);
    }
}
