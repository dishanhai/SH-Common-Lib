// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exercises the real settings file, including a failed replacement. */
public final class PlayerInfoHudSettingsIoTest {
    public static void run() throws IOException {
        Path directory = Files.createTempDirectory("shclib-hud-settings-test-");
        Path file = directory.resolve("settings.properties");
        Path blocked = directory.resolve("blocked");
        try {
            var expected = new PlayerInfoHudSettings(false, 0.25, 0.75, 135,
                    PlayerInfoHudSettings.DEFAULT.withItem(PlayerInfoItem.TPS, true).visibleItems())
                    .withGroup(HudGroups.Group.FTB, false).withDecorations(false, false);
            expected.write(file);
            if (!expected.equals(PlayerInfoHudSettings.read(file))) throw new AssertionError("HUD settings did not round-trip");

            Files.writeString(file, "enabled=true\nshow.tps=true\n");
            var legacy = PlayerInfoHudSettings.read(file);
            if (!legacy.showGroupTitles() || !legacy.showGroupSeparators() || !legacy.showsGroup(HudGroups.Group.AE2)
                    || !legacy.shows(PlayerInfoItem.TPS)) throw new AssertionError("legacy settings must retain visible defaults");
            expected.write(file);

            Files.createDirectory(blocked);
            boolean failed = false;
            try {
                PlayerInfoHudSettings.DEFAULT.write(blocked);
            } catch (IOException expectedFailure) {
                failed = true;
            }
            if (!failed || !expected.equals(PlayerInfoHudSettings.read(file))) {
                throw new AssertionError("failed replacement must preserve the existing settings");
            }
            try (var files = Files.list(directory)) {
                if (files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp"))) {
                    throw new AssertionError("failed replacement left a temporary settings file");
                }
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(blocked);
            Files.deleteIfExists(directory);
        }
    }

    private PlayerInfoHudSettingsIoTest() {}
}
