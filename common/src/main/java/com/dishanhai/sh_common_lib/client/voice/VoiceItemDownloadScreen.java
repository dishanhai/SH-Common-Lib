// Author: dishanhai
package com.dishanhai.sh_common_lib.client.voice;

import com.dishanhai.sh_common_lib.api.client.ulk.WhisperInstallation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.function.Supplier;

public final class VoiceItemDownloadScreen {
    private static final System.Logger LOGGER = System.getLogger(VoiceItemDownloadScreen.class.getName());
    private static Progress progress;

    private VoiceItemDownloadScreen() {}

    public static Component unavailable() {
        return WhisperInstallation.supported() ? null : label("unsupported");
    }

    public static void open(Screen parent, Supplier<? extends GuiEventListener> focus) {
        if (unavailable() != null) return;
        var client = Minecraft.getInstance();
        Runnable back = () -> {
            client.setScreen(parent);
            var widget = focus.get();
            if (widget != null && parent.children().contains(widget)) parent.setFocused(widget);
        };
        if (progress != null) {
            showProgress(client, back);
            return;
        }
        if (VoiceItemPreferences.recognizer().installed()) {
            client.setScreen(new AlertScreen(back, label("installed"), label("ready")));
            return;
        }
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) start(client, back);
            else back.run();
        }, label("title"), label("confirm"), label("action"), CommonComponents.GUI_CANCEL));
    }

    private static void start(Minecraft client, Runnable back) {
        var directory = VoiceItemPreferences.recognizer().executable().getParent();
        showProgress(client, back);
        var worker = new Thread(() -> {
            try {
                WhisperInstallation.install(directory);
                client.execute(() -> finish(client, true));
            } catch (IOException | RuntimeException error) {
                LOGGER.log(System.Logger.Level.WARNING, "Whisper quick download failed", error);
                client.execute(() -> finish(client, false));
            }
        }, "SHCLIB Whisper download");
        worker.setDaemon(true);
        worker.start();
    }

    private static void showProgress(Minecraft client, Runnable back) {
        var screen = new AlertScreen(back, label("downloading"), label("progress"));
        progress = new Progress(screen, back);
        client.setScreen(screen);
    }

    private static void finish(Minecraft client, boolean success) {
        var view = progress;
        progress = null;
        if (client.screen == view.screen()) {
            client.setScreen(new AlertScreen(view.back(), label(success ? "installed" : "failed"),
                    label(success ? "ready" : "retry")));
        }
    }

    private static Component label(String name) {
        return Component.translatable("screen.sh_common_lib.voice_download." + name);
    }

    private record Progress(Screen screen, Runnable back) {}
}
