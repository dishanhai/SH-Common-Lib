// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public final class ArtisanAppearancePreferences {
    public static final boolean DEFAULT_TARGET_COLORS = true;
    private static final System.Logger LOGGER = System.getLogger(ArtisanAppearancePreferences.class.getName());
    private static final String TARGET_COLORS = "targetColors";
    private static final String QUICK_GROUPS = "quickGroups";
    private static final String CONFIG_GROUP = "configGroup";
    private static final String CONFIG_PAGE = "configPage";
    private static final ConfigPosition DEFAULT_POSITION = new ConfigPosition("", 0);
    private static Path file;
    private static boolean targetColors = DEFAULT_TARGET_COLORS;
    private static String quickGroups = "";
    private static ConfigPosition configPosition = DEFAULT_POSITION;

    private ArtisanAppearancePreferences() {}

    public static void initialize(Path directory) {
        file = directory.resolve("artisan-client.properties");
        targetColors = DEFAULT_TARGET_COLORS;
        quickGroups = "";
        configPosition = DEFAULT_POSITION;
        try (var input = Files.newInputStream(file)) {
            var properties = new Properties();
            properties.load(input);
            targetColors = !"false".equalsIgnoreCase(properties.getProperty(TARGET_COLORS, "").trim());
            quickGroups = properties.getProperty(QUICK_GROUPS, "");
            configPosition = new ConfigPosition(properties.getProperty(CONFIG_GROUP, ""), readConfigPage(properties));
        } catch (NoSuchFileException ignored) {
            // Preserve the existing animated tool modes on fresh installs.
        } catch (IOException | IllegalArgumentException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read " + file, error);
        }
    }

    public static boolean targetColors() {
        return targetColors;
    }

    public static boolean save(boolean enabled) {
        return save(enabled, quickGroups);
    }

    static String quickGroups() { return quickGroups; }

    static boolean saveQuickGroups(String order) {
        return save(targetColors, order);
    }

    static ConfigPosition configPosition() { return configPosition; }

    static void rememberConfigPosition(String group, int page) {
        var position = new ConfigPosition(group, page);
        if (position.equals(configPosition)) return;
        configPosition = position;
        save(targetColors, quickGroups);
    }

    private static int readConfigPage(Properties properties) {
        try {
            return Math.max(0, Integer.parseInt(properties.getProperty(CONFIG_PAGE, "0").trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean save(boolean enabled, String order) {
        if (file == null) return false;
        Path pending = null;
        try {
            var properties = new Properties();
            properties.setProperty(TARGET_COLORS, Boolean.toString(enabled));
            if (!order.isEmpty()) properties.setProperty(QUICK_GROUPS, order);
            if (!configPosition.group().isEmpty()) {
                properties.setProperty(CONFIG_GROUP, configPosition.group());
                properties.setProperty(CONFIG_PAGE, Integer.toString(configPosition.page()));
            }
            Files.createDirectories(file.getParent());
            pending = Files.createTempFile(file.getParent(), "artisan-", ".tmp");
            try (var output = Files.newOutputStream(pending)) {
                properties.store(output, "SHCLIB Artisan appearance");
            }
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
            targetColors = enabled;
            quickGroups = order;
            return true;
        } catch (IOException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not write " + file, error);
            return false;
        } finally {
            if (pending != null) {
                try {
                    Files.deleteIfExists(pending);
                } catch (IOException error) {
                    LOGGER.log(System.Logger.Level.DEBUG, "Could not remove " + pending, error);
                }
            }
        }
    }

    record ConfigPosition(String group, int page) {}
}
