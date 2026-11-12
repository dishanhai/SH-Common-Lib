// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import java.util.Locale;

public enum PlayerInfoItem {
    PLAYER, FPS, PING, LOCAL_TIME, WORLD_TIME, DIMENSION, CHUNKS, ENTITIES, BIOME, POSITION, CHUNK, LIGHT,
    TPS, MSPT, MEMORY, CPU, GPU, DISK;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    boolean defaultVisible() {
        return switch (this) {
            case TPS, MSPT, MEMORY, CPU, GPU, DISK -> false;
            default -> true;
        };
    }
}
