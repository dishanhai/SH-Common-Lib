package com.dishanhai.sh_common_lib.internal.item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings.Profiles;

public final class ArtisanConfig {
    public static final String FILE_NAME = "sh_common_lib-artisan-server.toml";
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtisanConfig.class);
    private static volatile ArtisanConfig config;

    private final BooleanSupplier loaded;
    private final Supplier<Profiles> readValues;
    private final Consumer<Profiles> writeValues;
    private final Runnable saveFile;

    private ArtisanConfig(BooleanSupplier loaded, Supplier<Profiles> readValues,
                          Consumer<Profiles> writeValues, Runnable saveFile) {
        this.loaded = loaded;
        this.readValues = readValues;
        this.writeValues = writeValues;
        this.saveFile = saveFile;
    }

    public static void bind(BooleanSupplier loaded, Supplier<Profiles> readValues,
                            Consumer<Profiles> writeValues, Runnable saveFile) {
        config = new ArtisanConfig(loaded, readValues, writeValues, saveFile);
    }

    public static boolean available() {
        return config != null && config.loaded.getAsBoolean();
    }

    public static Profiles profiles() {
        return available() ? config.readValues.get() : Profiles.DEFAULTS;
    }

    public static ArtisanSettings current(boolean cosmic) {
        return profiles().get(cosmic);
    }

    public static boolean save(Profiles settings) {
        if (!available()) return false;
        return config.write(settings);
    }

    private synchronized boolean write(Profiles settings) {
        Profiles previous = readValues.get();
        try {
            writeValues.accept(settings);
            saveFile.run();
            return true;
        } catch (RuntimeException failure) {
            try {
                writeValues.accept(previous);
            } catch (RuntimeException rollback) {
                failure.addSuppressed(rollback);
            }
            LOGGER.warn("Could not save Artisan settings", failure);
            return false;
        }
    }
}
