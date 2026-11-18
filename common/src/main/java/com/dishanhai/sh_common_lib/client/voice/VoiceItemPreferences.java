// Author: dishanhai
package com.dishanhai.sh_common_lib.client.voice;

import com.dishanhai.sh_common_lib.api.client.ulk.OfflineSpeechRecognition;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;

public final class VoiceItemPreferences {
    public record Settings(boolean enabled, boolean fuzzy) {
        public static final Settings DEFAULT = new Settings(false, false);
    }

    private static final System.Logger LOGGER = System.getLogger(VoiceItemPreferences.class.getName());
    private static Path directory;
    private static Settings current = Settings.DEFAULT;

    private VoiceItemPreferences() {}

    public static void initialize(Path configDirectory) {
        directory = configDirectory.resolve("voice");
        current = Settings.DEFAULT;
        try (var input = Files.newInputStream(directory.resolve("preferences.properties"))) {
            var properties = new Properties();
            properties.load(input);
            current = new Settings(Boolean.parseBoolean(properties.getProperty("enabled", "false")),
                    Boolean.parseBoolean(properties.getProperty("fuzzy", "false")));
        } catch (NoSuchFileException ignored) {
            // Fresh installations start with both switches off.
        } catch (IOException | IllegalArgumentException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read voice item preferences", error);
        }
    }

    public static Settings current() { return current; }

    public static boolean save(Settings settings) {
        if (directory == null) return false;
        Path file = directory.resolve("preferences.properties");
        Path pending = null;
        try {
            var properties = new Properties();
            properties.setProperty("enabled", Boolean.toString(settings.enabled()));
            properties.setProperty("fuzzy", Boolean.toString(settings.fuzzy()));
            Files.createDirectories(directory);
            pending = Files.createTempFile(directory, "preferences-", ".tmp");
            try (var output = Files.newOutputStream(pending)) {
                properties.store(output, "SHCLIB voice items");
            }
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
            current = settings;
            return true;
        } catch (IOException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not save voice item preferences", error);
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

    static OfflineSpeechRecognition recognizer() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        return new OfflineSpeechRecognition(directory.resolve(windows ? "whisper-cli.exe" : "whisper-cli"),
                directory.resolve("ggml-base.bin"));
    }
}
