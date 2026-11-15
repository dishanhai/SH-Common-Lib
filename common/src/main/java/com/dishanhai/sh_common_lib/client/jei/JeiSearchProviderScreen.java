// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

final class JeiSearchProviderScreen {
    private static final String KEY = "sh_common_lib.jei.provider.";
    private JeiSearchProviderScreen() {}

    static void open(Screen parent, Runnable back, Predicate<Boolean> choose) {
        var client = Minecraft.getInstance();
        client.setScreen(new ConfirmScreen(shclib -> {
            if (choose.test(shclib)) back.run();
            else client.setScreen(new AlertScreen(() -> open(parent, back, choose),
                    Component.translatable(KEY + "failed"), Component.translatable(KEY + "failed_detail")));
        }, Component.translatable(KEY + "title"), Component.translatable(KEY + "detail"),
                Component.translatable(KEY + "shclib"), Component.translatable(KEY + "jeiguess")) {
            @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
                if (key == GLFW.GLFW_KEY_ESCAPE) { back.run(); return true; }
                return super.keyPressed(key, scanCode, modifiers);
            }
        });
    }
}
