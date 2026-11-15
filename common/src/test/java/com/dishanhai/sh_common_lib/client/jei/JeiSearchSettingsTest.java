// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import java.nio.file.Files;

public final class JeiSearchSettingsTest {
    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("shclib-jei-settings-");
        var path = directory.resolve("settings.properties");
        try {
            var settings = new JeiSearchSettings(false, true, false, true, false, false, false, true, true, true, true);
            settings.write(path);
            if (!settings.equals(JeiSearchSettings.read(path))) throw new AssertionError("settings persistence");
            Files.writeString(path, "prediction=invalid\nhistory=false\n");
            var loaded = JeiSearchSettings.read(path);
            if (!loaded.prediction() || loaded.history()) throw new AssertionError("invalid value fallback");
            if (!loaded.enabled()) throw new AssertionError("legacy configurations enable the assistant");
            if (loaded.optimizeRendering() || JeiSearchSettings.DEFAULT.optimizeRendering())
                throw new AssertionError("rendering optimization is opt-in");
            if (!loaded.ae2Orderable() || !JeiSearchSettings.DEFAULT.ae2Orderable())
                throw new AssertionError("defaults and legacy configurations show AE2 badges");
            var disabled = loaded.withAe2Orderable(false).withEnabled(false)
                    .withOptimizeRendering(true).withSources(false, true, true).withHotWords(true, true);
            if (!disabled.withEnabled(true).optimizeRendering())
                throw new AssertionError("rendering optimization is independent of the search assistant");
            disabled.write(path);
            if (!disabled.equals(JeiSearchSettings.read(path))) throw new AssertionError("master switch persistence");
            if (JeiSearchSettings.read(path).ae2Orderable() || disabled.withEnabled(true).ae2Orderable())
                throw new AssertionError("AE2 badge opt-out persists independently of search settings");
            if (disabled.withEnabled(true).history() != loaded.history()) throw new AssertionError("master switch preserves options");
            if (!loaded.randomSource() || !loaded.questSource() || loaded.chatSource() || loaded.hotSource() || loaded.hotShare())
                throw new AssertionError("legacy source defaults");
            Files.writeString(directory.resolve("jei-search-mod-names.json"), "{\"example\":[\"自定义\"]}");
            JeiSearchPreferences.initialize(directory);
            if (!JeiSearchPreferences.modAliases("example", java.util.List.of("标签页")).equals(java.util.List.of("自定义", "标签页")))
                throw new AssertionError("custom mod name precedes creative tab labels");
            var history = JeiSearchPreferences.history();
            history.record("diamond");
            history.save();
            if (!JeiSearchPreferences.clearHistory() || !history.entries().isEmpty())
                throw new AssertionError("clear updates active history");
            if (!com.dishanhai.sh_common_lib.api.client.ulk.SearchHistory.load(
                    directory.resolve("jei-search-history.properties"), 40, 256).entries().isEmpty())
                throw new AssertionError("clear persists across restart");
            providerChoice(directory);
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory.resolve("jei-search-history.properties"));
            Files.deleteIfExists(directory.resolve("jei-search-mod-names.json"));
            Files.deleteIfExists(directory.resolve("provider.properties"));
            Files.deleteIfExists(directory);
        }
    }

    private static void providerChoice(java.nio.file.Path directory) throws Exception {
        var bridge = new JeiGuessCompatibility();
        if (!bridge.enabled()) throw new AssertionError("read public JEIGuess config");
        bridge.setEnabled(false);
        if (bridge.enabled() || com.maixr.suggest.SuggestConfig.ENABLED.saves != 1)
            throw new AssertionError("write and save inherited generic JEIGuess config value");
        bridge.setEnabled(true);
        class Guess implements JeiSearchProviderState.External {
            boolean enabled = true, fail;
            public boolean enabled() { return enabled; }
            public void setEnabled(boolean value) throws Exception {
                if (fail) throw new java.io.IOException("simulated failure");
                enabled = value;
            }
        }
        var guess = new Guess();
        var file = directory.resolve("provider.properties");
        var state = new JeiSearchProviderState(file, true, guess);
        if (!state.needsChoice() || state.useShclib()) throw new AssertionError("coexisting providers require a choice");
        if (!state.choose(JeiSearchProviderState.Choice.SHCLIB) || guess.enabled || !state.useShclib())
            throw new AssertionError("SHCLIB selection disables JEIGuess");
        state = new JeiSearchProviderState(file, true, guess);
        if (state.needsChoice() || state.useShclib() || !state.reconcile() || !state.useShclib())
            throw new AssertionError("saved choice is verified on restart");
        guess.fail = true;
        if (state.choose(JeiSearchProviderState.Choice.JEIGUESS) || state.useShclib() || guess.enabled)
            throw new AssertionError("external failure must not activate either conflicting provider");
        guess.fail = false;
        if (!state.choose(JeiSearchProviderState.Choice.JEIGUESS) || !guess.enabled || state.useShclib())
            throw new AssertionError("JEIGuess selection disables SHCLIB");
        var absent = new JeiSearchProviderState(file, false, guess);
        if (!absent.useShclib() || absent.needsChoice()) throw new AssertionError("absent JEIGuess falls back to SHCLIB");
        var unwritable = new JeiSearchProviderState(directory.resolve("missing-file"), true, guess);
        java.nio.file.Files.createDirectory(directory.resolve("missing-file"));
        try {
            if (unwritable.choose(JeiSearchProviderState.Choice.SHCLIB) || !guess.enabled || unwritable.useShclib())
                throw new AssertionError("failed persistence rolls back JEIGuess");
        } finally { java.nio.file.Files.delete(directory.resolve("missing-file")); }
    }
}
