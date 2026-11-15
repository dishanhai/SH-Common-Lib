// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Coordinates provider selection without linking JEIGuess on installations where it is absent. */
public final class JeiSearchProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("sh_common_lib:jei_search_provider");
    private static JeiSearchProviderState state;
    private static boolean guessInstalled, prompted, syncFailed;
    private static long nextCheck;

    private JeiSearchProvider() {}

    public static void initialize(boolean installed) {
        guessInstalled = installed;
        prompted = false;
        syncFailed = false;
        nextCheck = 0;
        try {
            state = new JeiSearchProviderState(JeiSearchPreferences.directory().resolve("jei-search-provider.properties"),
                    installed, new JeiGuessCompatibility());
        } catch (IOException | RuntimeException failure) {
            state = null;
            LOGGER.warn("Could not read JEI search provider choice", failure);
        }
    }

    public static boolean installed() { return guessInstalled; }
    public static boolean useShclib() { return !guessInstalled || state != null && state.useShclib(); }

    public static void tick() {
        if (!guessInstalled || !JeiSearchPreferences.current().enabled()) return;
        var client = Minecraft.getInstance();
        boolean mayPrompt = client.getOverlay() == null && (client.screen instanceof TitleScreen
                || client.screen == null && client.player != null);
        if (state == null) {
            if (!prompted && mayPrompt) {
                prompted = true;
                Screen parent = client.screen;
                client.setScreen(new AlertScreen(() -> client.setScreen(parent),
                        Component.translatable("sh_common_lib.jei.provider.failed"),
                        Component.translatable("sh_common_lib.jei.provider.failed_detail")));
            }
            return;
        }
        long now = System.nanoTime();
        if (!state.needsChoice() && now >= nextCheck) {
            nextCheck = now + 2_000_000_000L;
            boolean synchronizedSetting = state.reconcile();
            if (!synchronizedSetting && !syncFailed) LOGGER.warn("Could not synchronize the JEIGuess search setting");
            syncFailed = !synchronizedSetting;
        }
        if (!prompted && state.needsChoice() && mayPrompt) {
            prompted = true;
            Screen parent = client.screen;
            open(parent, () -> client.setScreen(parent));
        }
    }

    public static void open(Screen parent, Runnable back) {
        if (!guessInstalled || state == null) { back.run(); return; }
        JeiSearchProviderScreen.open(parent, back, selected -> state.choose(selected
                ? JeiSearchProviderState.Choice.SHCLIB : JeiSearchProviderState.Choice.JEIGUESS));
    }
}
