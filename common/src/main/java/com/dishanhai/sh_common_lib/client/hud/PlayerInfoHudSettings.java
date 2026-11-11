package com.dishanhai.sh_common_lib.client.hud;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

record PlayerInfoHudSettings(boolean enabled, double x, double y) {
    static final PlayerInfoHudSettings DEFAULT = new PlayerInfoHudSettings(true, 0, 0);

    static PlayerInfoHudSettings read(Path file) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (NoSuchFileException ignored) {
            return DEFAULT;
        }
        String enabled = properties.getProperty("enabled", "true").trim();
        return new PlayerInfoHudSettings(!enabled.equalsIgnoreCase("false"),
                readPosition(properties, "x"), readPosition(properties, "y"));
    }

    private static double readPosition(Properties properties, String key) {
        try {
            double value = Double.parseDouble(properties.getProperty(key, "0"));
            return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    void write(Path file) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("x", Double.toString(x));
        properties.setProperty("y", Double.toString(y));
        Path directory = Files.createDirectories(file.getParent());
        Path pending = Files.createTempFile(directory, "sh_common_lib-player-info-", ".tmp");
        try {
            try (var output = Files.newOutputStream(pending)) {
                properties.store(output, "Player info HUD: x and y are fractions of the available screen space (0 to 1).");
            }
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
    }
}
