// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Client-owned display and search settings; disabling history does not erase it. */
public record JeiSearchSettings(boolean prediction, boolean history, boolean hud, boolean preview, boolean hints, boolean glass,
                                boolean randomSource, boolean questSource, boolean chatSource) {
    public static final JeiSearchSettings DEFAULT = new JeiSearchSettings(true, true, true, true, true, true, true, true, false);

    public JeiSearchSettings(boolean prediction, boolean history, boolean hud, boolean preview, boolean hints, boolean glass) {
        this(prediction, history, hud, preview, hints, glass, true, true, false);
    }

    public JeiSearchSettings(boolean prediction, boolean history, boolean hud, boolean preview, boolean hints) {
        this(prediction, history, hud, preview, hints, true);
    }

    public static JeiSearchSettings read(Path file) throws IOException {
        var values = new Properties();
        try (var input = Files.newInputStream(file)) {
            values.load(input);
        } catch (NoSuchFileException missing) {
            return DEFAULT;
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Invalid JEI search settings", malformed);
        }
        return new JeiSearchSettings(value(values, "prediction", true), value(values, "history", true),
                value(values, "hud", true), value(values, "preview", true), value(values, "hints", true), value(values, "glass", true),
                value(values, "source.random", true), value(values, "source.quest", true), value(values, "source.chat", false));
    }

    public void write(Path file) throws IOException {
        var values = new Properties();
        values.setProperty("prediction", Boolean.toString(prediction));
        values.setProperty("history", Boolean.toString(history));
        values.setProperty("hud", Boolean.toString(hud));
        values.setProperty("preview", Boolean.toString(preview));
        values.setProperty("hints", Boolean.toString(hints));
        values.setProperty("glass", Boolean.toString(glass));
        values.setProperty("source.random", Boolean.toString(randomSource));
        values.setProperty("source.quest", Boolean.toString(questSource));
        values.setProperty("source.chat", Boolean.toString(chatSource));
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), "jei-search-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) { values.store(output, null); }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean value(Properties properties, String name, boolean fallback) {
        String text = properties.getProperty(name, "").trim();
        if (text.equalsIgnoreCase("true")) return true;
        if (text.equalsIgnoreCase("false")) return false;
        return fallback;
    }

    public JeiSearchSettings withSources(boolean random, boolean quest, boolean chat) {
        return new JeiSearchSettings(prediction, history, hud, preview, hints, glass, random, quest, chat);
    }
}
