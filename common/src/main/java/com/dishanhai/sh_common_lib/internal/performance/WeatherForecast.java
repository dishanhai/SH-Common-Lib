// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.performance;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/** The next known weather timer, in game ticks; future randomly chosen timers are not predicted. */
public record WeatherForecast(String dimension, int event, int ticks) {
    public static final int UNKNOWN = 0, PAUSED = 1, CLEAR_END = 2, RAIN_START = 3,
            RAIN_END = 4, THUNDER_START = 5, THUNDER_END = 6;
    private static final WeakHashMap<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    public static boolean allow(ServerPlayer player) {
        long now = System.nanoTime();
        Long last = LAST_REQUEST.get(player);
        if (last != null && now - last < TimeUnit.SECONDS.toNanos(1)) return false;
        LAST_REQUEST.put(player, now);
        return true;
    }

    public static WeatherForecast of(String dimension, boolean enabled, int clear, int rain, int thunder,
                                     boolean raining, boolean thundering) {
        if (!enabled) return new WeatherForecast(dimension, PAUSED, 0);
        if (clear > 0) return new WeatherForecast(dimension, CLEAR_END, clear);
        if (rain <= 0) return new WeatherForecast(dimension, UNKNOWN, 0);
        if (raining && thunder > 0 && thunder < rain) {
            return new WeatherForecast(dimension, thundering ? THUNDER_END : THUNDER_START, thunder);
        }
        return new WeatherForecast(dimension, raining ? RAIN_END : RAIN_START, rain);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(dimension, 256).writeVarInt(event).writeVarInt(ticks);
    }

    public static WeatherForecast decode(FriendlyByteBuf buffer) {
        return new WeatherForecast(buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt());
    }
}
