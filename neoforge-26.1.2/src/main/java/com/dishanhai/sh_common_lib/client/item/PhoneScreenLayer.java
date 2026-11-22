// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.screen.LayeredScreen;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

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
        minecraft.gui.getChat().openScreen(ChatComponent.ChatMethod.MESSAGE, (initial, draft) -> new ChatScreen(initial, draft) {
            @Override
            public void onClose() {
                super.onClose();
                if (minecraft.screen == null) minecraft.setScreen(parent);
            }
        });
    }

    protected static void showScreenshotResult(Minecraft client, Component result) {
        client.gui.getChat().addClientSystemMessage(result);
    }

    protected final MultiLineEditBox noteField(Bounds bounds, Component hint, Component label, int limit, String value,
                                              Consumer<String> change) {
        var field = MultiLineEditBox.builder().setX(bounds.x()).setY(bounds.y()).setPlaceholder(hint)
                .build(font, bounds.width(), bounds.height(), label);
        field.setCharacterLimit(limit);
        field.setValue(value);
        field.setValueListener(change);
        return field;
    }

    @Override
    protected final void renderBackdrop(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        var panel = panel();
        ClientPanels.strongPopover(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    protected final void renderForeground(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        drawPage((text, left, top, color) -> graphics.text(font, text, left, top, color), graphics::fill,
                ClientPanels.TEXT, ClientPanels.secondaryText());
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
