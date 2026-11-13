// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.performance;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/** Read-only, bounded telemetry for a player's connected AE2 wireless terminal. */
public record Ae2Snapshot(String dimension, int state, int nodes, int channels, int accessUsed, int accessMax,
                          String channelMode, String controller, boolean powered, boolean booting,
                          double stored, double capacity, double usage, double input) {
    public static final int NO_MOD = 0, NO_TERMINAL = 1, DISCONNECTED = 2, CONNECTED = 3, ERROR = 4;
    private static final WeakHashMap<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    public static boolean allow(ServerPlayer player) {
        long now = System.nanoTime();
        Long last = LAST_REQUEST.get(player);
        if (last != null && now - last < TimeUnit.SECONDS.toNanos(2)) return false;
        LAST_REQUEST.put(player, now);
        return true;
    }

    public static Ae2Snapshot empty(String dimension, int state) {
        return new Ae2Snapshot(dimension, state, 0, 0, 0, 0, "", "", false, false, 0, 0, 0, 0);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(dimension, 256).writeVarInt(state).writeVarInt(nodes).writeVarInt(channels)
                .writeVarInt(accessUsed).writeVarInt(accessMax).writeUtf(channelMode, 32).writeUtf(controller, 32)
                .writeBoolean(powered).writeBoolean(booting).writeDouble(stored).writeDouble(capacity)
                .writeDouble(usage).writeDouble(input);
    }

    public static Ae2Snapshot decode(FriendlyByteBuf buffer) {
        return new Ae2Snapshot(buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(32), buffer.readUtf(32),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble());
    }

    public boolean valid() {
        return state >= NO_MOD && state <= ERROR && nodes >= 0 && channels >= 0 && accessUsed >= 0 && accessMax >= 0
                && Double.isFinite(stored) && Double.isFinite(capacity) && Double.isFinite(usage) && Double.isFinite(input)
                && stored >= 0 && capacity >= 0 && usage >= 0 && input >= 0;
    }
}
