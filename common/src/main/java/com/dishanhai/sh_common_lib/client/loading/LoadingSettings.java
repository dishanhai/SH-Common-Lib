// Author: dishanhai
package com.dishanhai.sh_common_lib.client.loading;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class LoadingSettings {
    private final Path file;
    private boolean animation;
    private boolean profiling;

    LoadingSettings(Path file) {
        this.file = file;
        try (var input = Files.newInputStream(file)) {
            var properties = new Properties();
            properties.load(input);
            animation = Boolean.parseBoolean(properties.getProperty("animation"));
            profiling = Boolean.parseBoolean(properties.getProperty("profiling"));
        } catch (NoSuchFileException ignored) {
            // Both features default to off on a fresh install.
        } catch (IOException | IllegalArgumentException error) {
            ClientLoading.LOGGER.warn("Could not read {}", file, error);
        }
    }

    boolean animation() { return animation; }
    boolean profiling() { return profiling; }

    boolean save(boolean animation, boolean profiling) {
        var properties = new Properties();
        properties.setProperty("animation", Boolean.toString(animation));
        properties.setProperty("profiling", Boolean.toString(profiling));
        Path pending = null;
        try {
            Files.createDirectories(file.getParent());
            pending = Files.createTempFile(file.getParent(), "loading-", ".tmp");
            try (var output = Files.newOutputStream(pending)) {
                properties.store(output, "SHCLIB client loading");
            }
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
            this.animation = animation;
            this.profiling = profiling;
            return true;
        } catch (IOException error) {
            ClientLoading.LOGGER.warn("Could not write {}", file, error);
            return false;
        } finally {
            if (pending != null) {
                try { Files.deleteIfExists(pending); }
                catch (IOException error) { ClientLoading.LOGGER.debug("Could not remove {}", pending, error); }
            }
        }
    }
}
