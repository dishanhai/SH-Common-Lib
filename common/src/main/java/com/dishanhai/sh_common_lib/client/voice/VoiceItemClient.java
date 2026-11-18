// Author: dishanhai
package com.dishanhai.sh_common_lib.client.voice;

import com.dishanhai.sh_common_lib.api.client.ulk.OfflineSpeechRecognition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import javax.sound.sampled.LineUnavailableException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class VoiceItemClient {
    public static final String KEY = "key.sh_common_lib.voice_item";
    private static final System.Logger LOGGER = System.getLogger(VoiceItemClient.class.getName());
    private static final long DOUBLE_TAP = TimeUnit.MILLISECONDS.toNanos(325);
    private static final long MESSAGE_REFRESH = TimeUnit.MILLISECONDS.toNanos(500);

    private final KeyMapping key;
    private final Predicate<LocalPlayer> operator;
    private long pressedAt;
    private boolean armed;
    private Session listening;
    private Thread worker;
    private long lastMessage;

    public VoiceItemClient(KeyMapping key, Predicate<LocalPlayer> operator) {
        this.key = key;
        this.operator = operator;
    }

    public void tick() {
        var client = Minecraft.getInstance();
        int clicks = 0;
        while (key.consumeClick()) clicks++;
        if (!VoiceItemPreferences.current().enabled() || !client.isWindowActive() || client.player == null
                || client.level == null || client.screen != null || client.getOverlay() != null) {
            cancel();
            return;
        }
        if (!canGive(client)) {
            if (clicks > 0 || armed || listening != null) show(client, "op_required", ChatFormatting.RED);
            cancel();
            return;
        }

        long now = System.nanoTime();
        if (listening != null) {
            if (listening.player != client.player || listening.level != client.level) cancel();
            else if (listening.result.isDone()) finish(client);
            else if (now - lastMessage >= MESSAGE_REFRESH) showProgress(client, now);
            return;
        }
        if (worker != null && worker.isAlive()) return;
        if (armed && now - pressedAt >= DOUBLE_TAP) {
            start(client, false, now);
        } else if (clicks > 1 || (clicks == 1 && armed)) {
            start(client, true, now);
        } else if (clicks == 1) {
            armed = true;
            pressedAt = now;
            show(client, "single_armed", ChatFormatting.AQUA);
        }
    }

    private boolean canGive(Minecraft client) {
        var connection = client.getConnection();
        return client.player != null && operator.test(client.player) && connection != null
                && connection.getCommands().getRoot().getChild("give") != null;
    }

    private void start(Minecraft client, boolean stack, long now) {
        armed = false;
        var recognizer = VoiceItemPreferences.recognizer();
        if (!recognizer.installed()) {
            show(client, "setup_required", ChatFormatting.RED);
            return;
        }
        listening = new Session(client.player, client.level, stack, recognizer);
        worker = new Thread(listening.result, "SHCLIB voice recognition");
        worker.setDaemon(true);
        worker.start();
        showProgress(client, now);
    }

    private void showProgress(Minecraft client, long now) {
        lastMessage = now;
        show(client, listening.recognizing ? "recognizing" : "listening",
                listening.stack ? ChatFormatting.GOLD : ChatFormatting.AQUA, mode(listening.stack));
    }

    private void finish(Minecraft client) {
        var session = listening;
        listening = null;
        try {
            var transcript = session.result.get();
            if (transcript.isEmpty()) {
                show(client, "no_speech", ChatFormatting.RED, mode(session.stack));
                return;
            }
            var item = VoiceItemNames.resolve(transcript.get(), client.getResourceManager(),
                    VoiceItemPreferences.current().fuzzy()).getDefaultInstance();
            if (item.isEmpty() || !item.getItem().isEnabled(client.level.enabledFeatures())) {
                show(client, "no_item", ChatFormatting.RED, mode(session.stack));
                return;
            }
            int amount = session.stack ? item.getMaxStackSize() : 1;
            var itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
            // Only a registry ID and calculated count enter the command. The server enforces /give permissions.
            client.getConnection().sendCommand("give @s " + itemId + " " + amount);
            show(client, "requested", ChatFormatting.GREEN, mode(session.stack), amount, item.getHoverName());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException error) {
            var cause = error.getCause();
            boolean microphone = cause instanceof LineUnavailableException || cause instanceof IllegalArgumentException;
            LOGGER.log(System.Logger.Level.WARNING, "Voice item recognition failed", cause);
            show(client, microphone ? "microphone_error" : "recognition_error", ChatFormatting.RED, mode(session.stack));
        }
    }

    private void cancel() {
        armed = false;
        if (listening != null) {
            listening.result.cancel(true);
            listening = null;
        }
    }

    private static Component mode(boolean stack) {
        return Component.translatable("message.sh_common_lib.voice_item." + (stack ? "stack" : "single"));
    }

    private static void show(Minecraft client, String key, ChatFormatting color, Object... arguments) {
        client.gui.setOverlayMessage(Component.translatable("message.sh_common_lib.voice_item." + key, arguments)
                .withStyle(color), false);
    }

    private static final class Session {
        final LocalPlayer player;
        final ClientLevel level;
        final boolean stack;
        final FutureTask<Optional<String>> result;
        volatile boolean recognizing;

        Session(LocalPlayer player, ClientLevel level, boolean stack, OfflineSpeechRecognition recognizer) {
            this.player = player;
            this.level = level;
            this.stack = stack;
            result = new FutureTask<>(() -> recognizer.listen(() -> recognizing = true));
        }
    }
}
