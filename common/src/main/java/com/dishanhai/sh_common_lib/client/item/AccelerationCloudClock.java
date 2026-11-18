// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import java.util.HashMap;
import java.util.Map;

/** Keeps cloud displacement after acceleration is paused or the staff is put away. */
public final class AccelerationCloudClock {
    private static Connection connection;
    private static final Map<String, Long> OFFSETS = new HashMap<>();
    private AccelerationCloudClock() {}

    public static void receive(Connection from, long extraTicks) {
        receive(from, AccelerationWorldRenderer.dimension(), extraTicks);
    }

    public static void receive(Connection from, String dimension, long extraTicks) {
        var client = Minecraft.getInstance();
        if (client.getConnection() == null || client.getConnection().getConnection() != from || client.level == null) return;
        if (connection != from) { connection = from; OFFSETS.clear(); }
        OFFSETS.put(dimension, Math.max(0, extraTicks));
    }

    public static long ticks() {
        var client = Minecraft.getInstance();
        if (client.getConnection() == null || client.getConnection().getConnection() != connection) {
            OFFSETS.clear(); connection = null; return 0;
        }
        return client.level == null ? 0 : OFFSETS.getOrDefault(AccelerationWorldRenderer.dimension(), 0L);
    }
}
