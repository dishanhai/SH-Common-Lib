// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class BeaconBeamPreference {
    private static Boolean enabled;

    private BeaconBeamPreference() {}

    private static Path file(Minecraft client) {
        return client.gameDirectory.toPath().resolve("config/sh_common_lib-beacon-beam.enabled");
    }

    static boolean enabled(Minecraft client) {
        if (enabled == null) enabled = Files.isRegularFile(file(client));
        return enabled;
    }

    static boolean toggle(Minecraft client) {
        boolean next = !enabled(client);
        var file = file(client);
        try {
            if (next) {
                Files.createDirectories(file.getParent());
                Files.write(file, new byte[0]);
            } else {
                Files.deleteIfExists(file);
            }
            enabled = next;
            return true;
        } catch (IOException error) {
            LogUtils.getLogger().warn("Could not save beacon beam preference to {}", file, error);
            return false;
        }
    }
}
