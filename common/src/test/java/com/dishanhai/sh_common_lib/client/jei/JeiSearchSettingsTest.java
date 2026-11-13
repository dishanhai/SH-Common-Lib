// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import java.nio.file.Files;

public final class JeiSearchSettingsTest {
    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("shclib-jei-settings-");
        var path = directory.resolve("settings.properties");
        try {
            var settings = new JeiSearchSettings(false, true, false, true, false, false, false, true, true);
            settings.write(path);
            if (!settings.equals(JeiSearchSettings.read(path))) throw new AssertionError("settings persistence");
            Files.writeString(path, "prediction=invalid\nhistory=false\n");
            var loaded = JeiSearchSettings.read(path);
            if (!loaded.prediction() || loaded.history()) throw new AssertionError("invalid value fallback");
            if (!loaded.randomSource() || !loaded.questSource() || loaded.chatSource()) throw new AssertionError("legacy source defaults");
            JeiSearchPreferences.initialize(directory);
            var history = JeiSearchPreferences.history();
            history.record("diamond");
            history.save();
            if (!JeiSearchPreferences.clearHistory() || !history.entries().isEmpty())
                throw new AssertionError("clear updates active history");
            if (!com.dishanhai.sh_common_lib.api.client.ulk.SearchHistory.load(
                    directory.resolve("jei-search-history.properties"), 40, 256).entries().isEmpty())
                throw new AssertionError("clear persists across restart");
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory.resolve("jei-search-history.properties"));
            Files.deleteIfExists(directory);
        }
    }
}
